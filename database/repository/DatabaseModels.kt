package com.groupproject.database.repository

import com.groupproject.database.schema.ActionType
import com.groupproject.database.schema.GameResult
import com.groupproject.database.schema.GameStatus
import com.groupproject.database.schema.PlayerStatus
import java.time.LocalDateTime

data class GameStateDto(
    val gameId: Int,
    val dealerId: Int,
    val status: GameStatus,
    val currentPlayerId: Int?,
    val dealerHandJson: String?,
    val dealerScore: Int?,
    val createdAt: LocalDateTime,
    val startedAt: LocalDateTime?,
    val finishedAt: LocalDateTime?,
    val players: List<PlayerStateDto>
)

data class PlayerStateDto(
    val userId: Int,
    val username: String,
    val handJson: String?,
    val score: Int?,
    val status: PlayerStatus,
    val betAmount: Int,
    val result: GameResult?,
    val joinedAt: LocalDateTime
)

data class PlayerHistoryDto(
    val gameId: Int,
    val finishedAt: LocalDateTime?,
    val result: GameResult?,
    val betAmount: Int,
    val score: Int?
)

data class TopPlayerDto(
    val userId: Int,
    val username: String,
    val totalGames: Long,
    val wins: Long,
    val losses: Long,
    val pushes: Long,
    val winRate: Double,
    val totalBet: Long
)

data class GameActionDto(
    val actionId: Int,
    val gameId: Int,
    val userId: Int,
    val username: String,
    val actionType: ActionType,
    val actionDataJson: String?,
    val createdAt: LocalDateTime
)
