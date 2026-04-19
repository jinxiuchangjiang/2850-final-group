package com.obg.controller

import com.obg.model.*
import com.obg.repository.*
import com.obg.service.OnlineTracker
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.security.Principal

// ── FRIENDS ──────────────────────────────────────────────────────────────────
@RestController @RequestMapping("/api/friends")
class FriendController(
    private val friendRepo: FriendshipRepository,
    private val userRepo: UserRepository,
    private val onlineTracker: OnlineTracker
) {
    /** Accepted friends list. */
    @GetMapping
    fun getFriends(p: Principal): ResponseEntity<ApiResponse<List<FriendDto>>> {
        val me = userRepo.findByUsername(p.name).orElseThrow()
        return ResponseEntity.ok(ApiResponse(true, data = friendRepo.findFriendsOf(me.id).map { f ->
            val other = if (f.requester?.id == me.id) f.addressee!! else f.requester!!
            val status = onlineTracker.getStatus(other.uid)
            FriendDto(
                uid = other.uid,
                username = other.username,
                fullName = other.fullName,
                avatarUrl = other.avatarUrl,
                isOnline = status != OnlineTracker.Status.OFFLINE,
                status = status.name   // "OFFLINE" / "IDLE" / "IN_GAME"
            )
        }))
    }

    /** Incoming pending requests (caller is addressee). */
    @GetMapping("/requests")
    fun getRequests(p: Principal): ResponseEntity<ApiResponse<List<FriendRequestDto>>> {
        val me = userRepo.findByUsername(p.name).orElseThrow()
        return ResponseEntity.ok(ApiResponse(true, data = friendRepo.findPendingRequestsFor(me.id).map { f ->
            FriendRequestDto(f.id, f.requester!!.uid, f.requester.username,
                f.requester.fullName, f.status.name, f.createdAt.toString())
        }))
    }

    /** Outgoing pending requests (caller is requester). Shows "pending" state to the sender. */
    @GetMapping("/sent")
    fun getSentRequests(p: Principal): ResponseEntity<ApiResponse<List<SentRequestDto>>> {
        val me = userRepo.findByUsername(p.name).orElseThrow()
        return ResponseEntity.ok(ApiResponse(true, data = friendRepo.findSentRequestsBy(me.id).map { f ->
            SentRequestDto(f.id, f.addressee!!.uid, f.addressee.username,
                f.addressee.fullName, f.status.name, f.createdAt.toString())
        }))
    }

    @PostMapping("/request/{uid}")
    fun sendRequest(@PathVariable uid: String, p: Principal): ResponseEntity<ApiResponse<Unit>> {
        val me     = userRepo.findByUsername(p.name).orElseThrow()
        val target = userRepo.findByUid(uid).orElse(null)
            ?: return ResponseEntity.badRequest().body(ApiResponse(false, "Player not found"))
        if (me.id == target.id)
            return ResponseEntity.badRequest().body(ApiResponse(false, "Cannot add yourself"))
        if (friendRepo.findBetween(me.id, target.id).isPresent)
            return ResponseEntity.badRequest().body(ApiResponse(false, "Request already sent or already friends"))
        friendRepo.save(Friendship(requester = me, addressee = target))
        return ResponseEntity.ok(ApiResponse(true, "Request sent"))
    }

    @PutMapping("/request/{id}/accept")
    fun accept(@PathVariable id: Long): ResponseEntity<ApiResponse<Unit>> {
        val f = friendRepo.findById(id).orElse(null) ?: return ResponseEntity.notFound().build()
        friendRepo.save(f.copy(status = FriendStatus.ACCEPTED))
        return ResponseEntity.ok(ApiResponse(true, "Accepted"))
    }

    @PutMapping("/request/{id}/reject")
    fun reject(@PathVariable id: Long): ResponseEntity<ApiResponse<Unit>> {
        val f = friendRepo.findById(id).orElse(null) ?: return ResponseEntity.notFound().build()
        friendRepo.save(f.copy(status = FriendStatus.REJECTED))
        return ResponseEntity.ok(ApiResponse(true, "Rejected"))
    }

    /** Remove a friend by their UID. Deletes the friendship row in both directions. */
    @DeleteMapping("/{uid}")
    @Transactional
    fun deleteFriend(@PathVariable uid: String, p: Principal): ResponseEntity<ApiResponse<Unit>> {
        val me     = userRepo.findByUsername(p.name).orElseThrow()
        val friend = userRepo.findByUid(uid).orElse(null) ?: return ResponseEntity.notFound().build()
        friendRepo.deleteBetween(me.id, friend.id)
        return ResponseEntity.ok(ApiResponse(true, "Friend removed"))
    }
}

// ── ROOMS ─────────────────────────────────────────────────────────────────────
@RestController @RequestMapping("/api/rooms")
class RoomController(
    private val roomRepo: GameRoomRepository,
    private val roomPlayerRepo: RoomPlayerRepository,
    private val gameRepo: GameRepository,
    private val userRepo: UserRepository
) {
    /** All non-closed rooms for a game; isMember flag is set when the caller is in the room. */
    @GetMapping("/game/{gameId}")
    fun getRooms(@PathVariable gameId: Long, p: Principal): ResponseEntity<ApiResponse<List<RoomDto>>> {
        val me        = userRepo.findByUsername(p.name).orElseThrow()
        val rooms     = roomRepo.findByGame_IdAndStatusNot(gameId, RoomStatus.CLOSED)
        val myRoomIds = roomPlayerRepo.findByUser_Id(me.id).mapNotNull { it.room?.id }.toSet()
        return ResponseEntity.ok(ApiResponse(true, data = rooms.map { room ->
            val isMember = myRoomIds.contains(room.id)
            room.toDto(isMember && room.status == RoomStatus.PLAYING, isMember)
        }))
    }

    /** Get a single room — used by the lobby poller. */
    @GetMapping("/{id}")
    fun getRoom(@PathVariable id: Long, p: Principal): ResponseEntity<ApiResponse<RoomDto>> {
        val me   = userRepo.findByUsername(p.name).orElseThrow()
        val room = roomRepo.findById(id).orElse(null) ?: return ResponseEntity.notFound().build()
        val isMember = roomPlayerRepo.existsByRoom_IdAndUser_Id(id, me.id)
        return ResponseEntity.ok(ApiResponse(true, data = room.toDto(isMember && room.status == RoomStatus.PLAYING, isMember)))
    }

    /** Create a room. Refuses if the caller already has an open room for this game. */
    @PostMapping
    fun create(@RequestBody req: CreateRoomRequest, p: Principal): ResponseEntity<ApiResponse<RoomDto>> {
        val host = userRepo.findByUsername(p.name).orElseThrow()
        val game = gameRepo.findById(req.gameId).orElse(null)
            ?: return ResponseEntity.badRequest().body(ApiResponse(false, "Game not found"))
        if (roomRepo.findActiveRoomByUserAndGame(host.id, req.gameId).isNotEmpty())
            return ResponseEntity.badRequest().body(ApiResponse(false, "You already have an open room for this game"))
        if (req.privacy == "PRIVATE" && req.password.isNullOrBlank())
            return ResponseEntity.badRequest().body(ApiResponse(false, "Private rooms require a password"))
        val pwHash = if (req.privacy == "PRIVATE") BCryptPasswordEncoder().encode(req.password) else null
        val room   = roomRepo.save(GameRoom(game = game, host = host, roomName = req.roomName,
            privacy = RoomPrivacy.valueOf(req.privacy), passwordHash = pwHash,
            maxPlayers = req.maxPlayers, currentPlayers = 1))
        roomPlayerRepo.save(RoomPlayer(room = room, user = host, isReady = false))
        return ResponseEntity.ok(ApiResponse(true, "Created", room.toDto(false, true)))
    }

    /**
     * Join a room.
     * - Existing members bypass the capacity and password checks (reconnect / post-game return).
     * - New members must satisfy capacity and, for private rooms, provide the correct password.
     * - Exception: invite-join flag bypasses the password check for private rooms.
     */
    @PostMapping("/{id}/join")
    fun join(
        @PathVariable id: Long,
        @RequestBody req: JoinRoomRequest,
        @RequestParam(required = false) inviteBypass: Boolean = false,
        p: Principal
    ): ResponseEntity<ApiResponse<RoomDto>> {
        val user = userRepo.findByUsername(p.name).orElseThrow()
        val room = roomRepo.findById(id).orElse(null) ?: return ResponseEntity.notFound().build()

        val alreadyIn = roomPlayerRepo.existsByRoom_IdAndUser_Id(room.id, user.id)
        if (!alreadyIn) {
            if (room.currentPlayers >= room.maxPlayers)
                return ResponseEntity.badRequest().body(ApiResponse(false, "Room is full"))
            if (room.privacy == RoomPrivacy.PRIVATE && !inviteBypass) {
                if (req.password.isNullOrBlank())
                    return ResponseEntity.status(403).body(ApiResponse(false, "This room requires a password"))
                if (room.passwordHash.isNullOrBlank() ||
                    !BCryptPasswordEncoder().matches(req.password, room.passwordHash))
                    return ResponseEntity.status(403).body(ApiResponse(false, "Wrong password"))
            }
            roomPlayerRepo.save(RoomPlayer(room = room, user = user, isReady = false))
        }

        val count    = roomPlayerRepo.countByRoom_Id(room.id).toInt()
        val updated  = roomRepo.save(room.copy(currentPlayers = count))
        return ResponseEntity.ok(ApiResponse(true, "Joined", updated.toDto(updated.status == RoomStatus.PLAYING, true)))
    }

    /** Toggle the calling player's ready state in a lobby. */
    @PostMapping("/{id}/ready")
    fun toggleReady(@PathVariable id: Long, p: Principal): ResponseEntity<ApiResponse<RoomDto>> {
        val user = userRepo.findByUsername(p.name).orElseThrow()
        val rp   = roomPlayerRepo.findByRoom_IdAndUser_Id(id, user.id).orElse(null)
            ?: return ResponseEntity.badRequest().body(ApiResponse(false, "You are not in this room"))
        roomPlayerRepo.save(rp.copy(isReady = !rp.isReady))
        val room = roomRepo.findById(id).orElseThrow()
        return ResponseEntity.ok(ApiResponse(true, "Ready toggled", room.toDto(false, true)))
    }

    /**
     * Reset a room after a game ends.
     * Sets status back to WAITING and clears all players' ready flags.
     * Used when players choose to play another round.
     */
    @PostMapping("/{id}/reset")
    @Transactional
    fun reset(@PathVariable id: Long, p: Principal): ResponseEntity<ApiResponse<RoomDto>> {
        val user = userRepo.findByUsername(p.name).orElseThrow()
        val room = roomRepo.findById(id).orElse(null) ?: return ResponseEntity.notFound().build()
        if (!roomPlayerRepo.existsByRoom_IdAndUser_Id(id, user.id))
            return ResponseEntity.badRequest().body(ApiResponse(false, "Not a room member"))
        roomPlayerRepo.findByRoom_Id(id).forEach { rp -> roomPlayerRepo.save(rp.copy(isReady = false)) }
        val updated = roomRepo.save(room.copy(status = RoomStatus.WAITING))
        return ResponseEntity.ok(ApiResponse(true, "Room reset", updated.toDto(false, true)))
    }

    /**
     * Leave a room.
     * - Transfers host if the leaver is the current host and others remain.
     * - Closes the room when empty.
     */
    @DeleteMapping("/{id}/leave")
    @Transactional
    fun leave(@PathVariable id: Long, p: Principal): ResponseEntity<ApiResponse<Unit>> {
        val user = userRepo.findByUsername(p.name).orElseThrow()
        val room = roomRepo.findById(id).orElse(null) ?: return ResponseEntity.ok(ApiResponse(true, "Left"))

        roomPlayerRepo.deleteByRoom_IdAndUser_Id(id, user.id)
        val remaining = roomPlayerRepo.findByRoom_Id(id)

        if (remaining.isEmpty()) {
            roomRepo.save(room.copy(currentPlayers = 0, status = RoomStatus.CLOSED))
        } else {
            val newHost = if (room.host?.id == user.id) remaining.first().user else room.host
            roomRepo.save(room.copy(host = newHost, currentPlayers = remaining.size, status = RoomStatus.WAITING))
        }
        return ResponseEntity.ok(ApiResponse(true, "Left"))
    }

    // ── DTO builder ──────────────────────────────────────────────────────────
    private fun GameRoom.toDto(showUids: Boolean, isMember: Boolean = false): RoomDto {
        val players = roomPlayerRepo.findByRoom_Id(id).map { rp ->
            RoomPlayerDto(
                username = rp.user?.username ?: "",
                fullName = rp.user?.fullName ?: "",
                uid      = if (showUids) rp.user?.uid else null,
                isReady  = rp.isReady
            )
        }
        return RoomDto(id, game?.id ?: 0, game?.title ?: "", roomName,
            host?.username ?: "", privacy.name, status.name, currentPlayers, maxPlayers, players, isMember)
    }
}

// ── LIKED GAMES ───────────────────────────────────────────────────────────────
@RestController @RequestMapping("/api/likes")
class LikedGameController(
    private val likedRepo: LikedGameRepository,
    private val histRepo: PlayHistoryRepository,
    private val gameRepo: GameRepository,
    private val userRepo: UserRepository
) {
    @GetMapping
    fun getLiked(p: Principal) = ResponseEntity.ok(ApiResponse(true, data =
        userRepo.findByUsername(p.name).orElseThrow().let { u ->
            likedRepo.findByUser_Id(u.id).mapNotNull { it.game?.id }
        }))

    @PostMapping("/{gameId}")
    fun like(@PathVariable gameId: Long, p: Principal): ResponseEntity<ApiResponse<Unit>> {
        val u = userRepo.findByUsername(p.name).orElseThrow()
        val g = gameRepo.findById(gameId).orElse(null) ?: return ResponseEntity.notFound().build()
        if (!likedRepo.existsByUser_IdAndGame_Id(u.id, gameId)) likedRepo.save(LikedGame(user = u, game = g))
        return ResponseEntity.ok(ApiResponse(true, "Liked"))
    }

    @DeleteMapping("/{gameId}")
    @Transactional
    fun unlike(@PathVariable gameId: Long, p: Principal): ResponseEntity<ApiResponse<Unit>> {
        val u = userRepo.findByUsername(p.name).orElseThrow()
        likedRepo.deleteByUser_IdAndGame_Id(u.id, gameId)
        return ResponseEntity.ok(ApiResponse(true, "Unliked"))
    }

    @GetMapping("/history")
    fun getHistory(p: Principal): ResponseEntity<ApiResponse<List<Map<String, Any>>>> {
        val u = userRepo.findByUsername(p.name).orElseThrow()
        return ResponseEntity.ok(ApiResponse(true, data =
            histRepo.findByUser_IdOrderByPlayedAtDesc(u.id).map { h ->
                mapOf("gameId"       to (h.game?.id ?: 0),
                      "gameTitle"    to (h.game?.title ?: ""),
                      "coverImageUrl" to (h.game?.coverImageUrl ?: ""),
                      "playedAt"     to h.playedAt.format(DATE_FMT),
                      "durationMinutes" to h.durationMinutes,
                      "result"       to h.result)   // WIN | LOSS | DRAW | UNKNOWN
            }))
    }
}

// ── ROOM INVITES (in-memory; players poll to receive pending invites) ─────────
@RestController @RequestMapping("/api/invites")
class InviteController(
    private val userRepo: UserRepository,
    private val roomRepo: GameRoomRepository
) {
    companion object {
        // targetUid -> list of invite payloads
        val pending = java.util.concurrent.ConcurrentHashMap<String, MutableList<Map<String, Any>>>()
    }

    /** Send a room invite to a friend. Private rooms do not require the invitee to know the password. */
    @PostMapping("/send")
    fun send(@RequestBody req: Map<String, String>, p: Principal): ResponseEntity<ApiResponse<Unit>> {
        val sender    = userRepo.findByUsername(p.name).orElseThrow()
        val targetUid = req["targetUid"] ?: return ResponseEntity.badRequest().body(ApiResponse(false, "Missing targetUid"))
        val roomId    = req["roomId"]?.toLongOrNull() ?: return ResponseEntity.badRequest().body(ApiResponse(false, "Missing roomId"))
        val room      = roomRepo.findById(roomId).orElse(null) ?: return ResponseEntity.badRequest().body(ApiResponse(false, "Room not found"))
        if (room.currentPlayers >= room.maxPlayers)
            return ResponseEntity.badRequest().body(ApiResponse(false, "Room is full"))
        val invite = mapOf(
            "fromUid"   to sender.uid,
            "fromName"  to sender.fullName.ifBlank { sender.username },
            "roomId"    to roomId,
            "roomName"  to room.roomName,
            "gameId"    to (room.game?.id ?: 0),
            "gameTitle" to (room.game?.title ?: ""),
            "privacy"   to room.privacy.name,
            "sentAt"    to System.currentTimeMillis()
        )
        pending.getOrPut(targetUid) { mutableListOf() }.add(invite)
        return ResponseEntity.ok(ApiResponse(true, "Invite sent"))
    }

    /** Poll for pending invites. Returns all and clears the queue. */
    @GetMapping("/poll")
    fun poll(p: Principal): ResponseEntity<ApiResponse<List<Map<String, Any>>>> {
        val user = userRepo.findByUsername(p.name).orElseThrow()
        val invites = pending.remove(user.uid) ?: emptyList()
        return ResponseEntity.ok(ApiResponse(true, data = invites))
    }
}
