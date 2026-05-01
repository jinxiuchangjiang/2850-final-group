package com.obg.service

import com.obg.model.PlayHistory
import com.obg.model.RoomStatus
import com.obg.repository.GameRoomRepository
import com.obg.repository.PlayHistoryRepository
import com.obg.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Handles all database writes triggered from an active WebSocket game session.
 * Runs in its own @Transactional context because WebSocket handler threads
 * are not managed by Spring and have no transaction context by default.
 */
@Service
@Transactional
class GameSessionService(
    private val roomRepo: GameRoomRepository,
    private val playHistRepo: PlayHistoryRepository,
    private val userRepo: UserRepository
) {
    /**
     * Record a game result for every participant and mark the room ENDED.
     * @param winnerUid  UID of the winner; null means draw.
     */
    fun recordGameEnd(roomId: Long, playerUids: Set<String>, startTimeMs: Long, winnerUid: String? = null) {
        val room       = roomRepo.findById(roomId).orElse(null) ?: return
        val gameEntity = room.game ?: return
        val durationMin = ((System.currentTimeMillis() - startTimeMs) / 60_000L).toInt().coerceAtLeast(1)

        playerUids.forEach { uid ->
            val user   = userRepo.findByUid(uid).orElse(null) ?: return@forEach
            val result = when {
                winnerUid == null -> "DRAW"
                winnerUid == uid  -> "WIN"
                else              -> "LOSS"
            }
            playHistRepo.save(PlayHistory(user = user, game = gameEntity,
                durationMinutes = durationMin, result = result))
        }
        roomRepo.save(room.copy(status = RoomStatus.ENDED))
    }

    /** Update room status directly (e.g., PLAYING when all players are connected via WS). */
    fun setRoomStatus(roomId: Long, status: RoomStatus) {
        val room = roomRepo.findById(roomId).orElse(null) ?: return
        roomRepo.save(room.copy(status = status))
    }
}
