package com.obg.service

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.obg.model.RoomStatus
import com.obg.repository.GameRoomRepository
import com.obg.repository.UserRepository
import com.obg.security.JwtService
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

/**
 * Generic game message relay WebSocket handler.
 *
 * The platform does NOT understand game logic. It only:
 *   1. Verifies JWT tokens — rejects unauthenticated connections
 *   2. Groups players by roomId
 *   3. Injects `fromUid` into every message and forwards it to the same room
 *   4. Broadcasts ROOM_READY when the room reaches capacity
 *   5. Persists game-end history when it receives OBG_GAME_OVER
 *   6. Tracks online status via OnlineTracker
 *
 * Connect: ws://localhost:8090/ws/relay?roomId=<id>&token=<JWT>
 *
 * Platform -> Clients (broadcast):
 *   PLAYER_JOINED  { type, uid, fullName, username, players:[...] }
 *   PLAYER_LEFT    { type, uid }
 *   ROOM_READY     { type, roomId, startTimeMs, players:[...] }
 *   PLAYER_READY   { type, uid, isReady }
 *   ERROR          { type, message }
 *
 * Client -> Platform (triggers persist + broadcast):
 *   OBG_GAME_OVER  { type, winner: uid|null, startTimeMs: long }
 *
 * All other messages are forwarded to room peers unchanged (+ fromUid injected).
 */
@Component
class GameRelayWebSocketHandler(
    private val jwtService: JwtService,
    private val userRepo: UserRepository,
    private val roomRepo: GameRoomRepository,
    private val sessionService: GameSessionService,
    private val onlineTracker: OnlineTracker
) : TextWebSocketHandler() {

    private val mapper = jacksonObjectMapper()

    // roomId -> { uid -> session }
    private val rooms       = ConcurrentHashMap<String, ConcurrentHashMap<String, WebSocketSession>>()
    // roomId -> { uid -> displayName }
    private val roomPlayers = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()
    // sessionId -> roomId
    private val sessionRoom = ConcurrentHashMap<String, String>()
    // sessionId -> uid
    private val sessionUid  = ConcurrentHashMap<String, String>()
    // roomId -> game start timestamp
    private val roomStartMs = ConcurrentHashMap<String, Long>()

    // ── Connection ────────────────────────────────────────────────────────────
    override fun afterConnectionEstablished(session: WebSocketSession) {
        val params  = parseQuery(session.uri?.query ?: "")
        val token   = params["token"]  ?: return session.close(CloseStatus.BAD_DATA)
        val roomId  = params["roomId"] ?: return session.close(CloseStatus.BAD_DATA)

        if (!jwtService.isValid(token)) return session.close(CloseStatus.POLICY_VIOLATION)

        val username = jwtService.extractUsername(token)
        val user = userRepo.findByUsername(username).orElse(null)
            ?: return session.close(CloseStatus.BAD_DATA)

        val room    = rooms.getOrPut(roomId)       { ConcurrentHashMap() }
        val players = roomPlayers.getOrPut(roomId) { ConcurrentHashMap() }

        synchronized(room) {
            val maxPlayers = roomRepo.findById(roomId.toLongOrNull() ?: 0L)
                .map { it.maxPlayers }.orElse(2)

            if (room.size >= maxPlayers && !room.containsKey(user.uid)) {
                send(session, mapOf("type" to "ERROR", "message" to "Room is full"))
                session.close(CloseStatus.NORMAL)
                return
            }

            room[user.uid]    = session
            players[user.uid] = user.fullName.ifBlank { user.username }
            sessionRoom[session.id] = roomId
            sessionUid[session.id]  = user.uid
            onlineTracker.ping(user.uid)

            val playerList = buildPlayerList(players)
            // Notify the joining player of the current occupants
            send(session, mapOf(
                "type"     to "PLAYER_JOINED",
                "uid"      to user.uid,
                "fullName" to user.fullName,
                "username" to user.username,
                "players"  to playerList
            ))
            // Notify existing occupants
            broadcastExcept(room, user.uid, mapOf(
                "type"     to "PLAYER_JOINED",
                "uid"      to user.uid,
                "fullName" to user.fullName,
                "username" to user.username,
                "players"  to playerList
            ))
            // All seats filled -> start the game
            if (room.size == maxPlayers) {
                val startMs = System.currentTimeMillis()
                roomStartMs[roomId] = startMs
                persistAsync { sessionService.setRoomStatus(roomId.toLong(), RoomStatus.PLAYING) }
                broadcast(room, mapOf(
                    "type"        to "ROOM_READY",
                    "roomId"      to roomId,
                    "startTimeMs" to startMs,
                    "players"     to playerList
                ))
            }
        }
    }

    // ── Message ───────────────────────────────────────────────────────────────
    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val roomId = sessionRoom[session.id] ?: return
        val uid    = sessionUid[session.id]  ?: return
        val room   = rooms[roomId]           ?: return

        val node = try {
            mapper.readTree(message.payload) as? ObjectNode ?: return
        } catch (_: Exception) { return }
        node.put("fromUid", uid)

        when (node.path("type").asText()) {
            "PING" -> {
                // Heartbeat — reply with PONG to the sender only (do not broadcast)
                send(session, mapOf("type" to "PONG", "ts" to System.currentTimeMillis()))
            }
            "OBG_GAME_OVER" -> {
                val winnerUid = node.path("winner").takeIf { !it.isNull && !it.isMissingNode }?.asText()
                val startMs   = roomStartMs[roomId] ?: node.path("startTimeMs").asLong(0L)
                roomId.toLongOrNull()?.let { rid ->
                    persistAsync { sessionService.recordGameEnd(rid, room.keys.toSet(), startMs, winnerUid) }
                }
                broadcast(room, mapper.convertValue(node, Map::class.java) as Map<String, Any?>)
            }
            else -> broadcast(room, mapper.convertValue(node, Map::class.java) as Map<String, Any?>)
        }
    }

    // ── Disconnect ────────────────────────────────────────────────────────────
    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val roomId = sessionRoom.remove(session.id) ?: return
        val uid    = sessionUid.remove(session.id)  ?: return
        val room   = rooms[roomId] ?: return

        onlineTracker.disconnect(uid)

        synchronized(room) {
            room.remove(uid)
            roomPlayers[roomId]?.remove(uid)
            if (room.isEmpty()) {
                rooms.remove(roomId); roomPlayers.remove(roomId); roomStartMs.remove(roomId)
                persistAsync {
                    roomId.toLongOrNull()?.let { sessionService.setRoomStatus(it, RoomStatus.CLOSED) }
                }
            } else {
                broadcast(room, mapOf("type" to "PLAYER_LEFT", "uid" to uid))
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun buildPlayerList(players: Map<String, String>) =
        players.map { (uid, name) -> mapOf("uid" to uid, "name" to name) }

    private fun send(session: WebSocketSession, data: Map<String, Any?>) {
        try { if (session.isOpen) session.sendMessage(TextMessage(mapper.writeValueAsString(data))) }
        catch (_: Exception) {}
    }
    private fun broadcast(room: Map<String, WebSocketSession>, data: Map<String, Any?>) {
        val json = mapper.writeValueAsString(data)
        room.values.filter { it.isOpen }.forEach { s ->
            try { s.sendMessage(TextMessage(json)) } catch (_: Exception) {}
        }
    }
    private fun broadcastExcept(room: Map<String, WebSocketSession>, excludeUid: String, data: Map<String, Any?>) {
        val json = mapper.writeValueAsString(data)
        room.entries.filter { it.key != excludeUid && it.value.isOpen }.forEach { (_, s) ->
            try { s.sendMessage(TextMessage(json)) } catch (_: Exception) {}
        }
    }
    private fun persistAsync(block: () -> Unit) {
        Thread { try { block() } catch (e: Exception) { System.err.println("Relay persist: ${e.message}") } }
            .also { it.isDaemon = true }.start()
    }
    private fun parseQuery(query: String): Map<String, String> =
        query.split("&").mapNotNull { p ->
            val i = p.indexOf('='); if (i < 0) null else p.substring(0, i) to p.substring(i + 1)
        }.toMap()
}
