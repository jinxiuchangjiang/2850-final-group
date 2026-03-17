package com.groupproject.database.repository

import com.groupproject.database.DatabaseFactory
import com.groupproject.database.schema.GameActionsTable
import com.groupproject.database.schema.GamePlayersTable
import com.groupproject.database.schema.GameResult
import com.groupproject.database.schema.GamesTable
import com.groupproject.database.schema.GameStatus
import com.groupproject.database.schema.UsersTable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll

class GameRepository {

    suspend fun getCurrentGameState(gameId: Int): GameStateDto? = DatabaseFactory.dbQuery {
        val gameRow = GamesTable
            .selectAll()
            .where { (GamesTable.id eq gameId) and (GamesTable.status eq GameStatus.PLAYING) }
            .singleOrNull()
            ?: return@dbQuery null

        val players = (GamePlayersTable
            .join(UsersTable, JoinType.INNER, additionalConstraint = { GamePlayersTable.userId eq UsersTable.id }))
            .selectAll()
            .where { GamePlayersTable.gameId eq gameId }
            .orderBy(GamePlayersTable.joinedAt to SortOrder.ASC)
            .map {
                PlayerStateDto(
                    userId = it[GamePlayersTable.userId],
                    username = it[UsersTable.username],
                    handJson = it[GamePlayersTable.hand],
                    score = it[GamePlayersTable.score],
                    status = it[GamePlayersTable.status],
                    betAmount = it[GamePlayersTable.betAmount],
                    result = it[GamePlayersTable.result],
                    joinedAt = it[GamePlayersTable.joinedAt]
                )
            }

        GameStateDto(
            gameId = gameRow[GamesTable.id],
            dealerId = gameRow[GamesTable.dealerId],
            status = gameRow[GamesTable.status],
            currentPlayerId = gameRow[GamesTable.currentPlayerId],
            dealerHandJson = gameRow[GamesTable.dealerHand],
            dealerScore = gameRow[GamesTable.dealerScore],
            createdAt = gameRow[GamesTable.createdAt],
            startedAt = gameRow[GamesTable.startedAt],
            finishedAt = gameRow[GamesTable.finishedAt],
            players = players
        )
    }

    suspend fun getPlayerGameHistory(userId: Int, limit: Int = 20): List<PlayerHistoryDto> = DatabaseFactory.dbQuery {
        (GamesTable
            .join(GamePlayersTable, JoinType.INNER, additionalConstraint = { GamesTable.id eq GamePlayersTable.gameId }))
            .select { (GamePlayersTable.userId eq userId) and (GamesTable.status eq GameStatus.FINISHED) }
            .orderBy(GamesTable.finishedAt to SortOrder.DESC)
            .limit(limit)
            .map {
                PlayerHistoryDto(
                    gameId = it[GamesTable.id],
                    finishedAt = it[GamesTable.finishedAt],
                    result = it[GamePlayersTable.result],
                    betAmount = it[GamePlayersTable.betAmount],
                    score = it[GamePlayersTable.score]
                )
            }
    }

    suspend fun getTopPlayers(minGames: Int = 5, limit: Int = 10): List<TopPlayerDto> = DatabaseFactory.dbQuery {
        val rows = (UsersTable
            .join(GamePlayersTable, JoinType.INNER, additionalConstraint = { UsersTable.id eq GamePlayersTable.userId })
            .join(GamesTable, JoinType.INNER, additionalConstraint = { GamePlayersTable.gameId eq GamesTable.id }))
            .select { GamesTable.status eq GameStatus.FINISHED }
            .toList()

        rows.groupBy { it[UsersTable.id] to it[UsersTable.username] }
            .mapNotNull { (userKey, userRows) ->
                val totalGames = userRows.size.toLong()
                if (totalGames < minGames) {
                    null
                } else {
                    val wins = userRows.count { it[GamePlayersTable.result] == GameResult.WIN }.toLong()
                    val losses = userRows.count { it[GamePlayersTable.result] == GameResult.LOSE }.toLong()
                    val pushes = userRows.count { it[GamePlayersTable.result] == GameResult.PUSH }.toLong()
                    val totalBet = userRows.sumOf { it[GamePlayersTable.betAmount].toLong() }

                    TopPlayerDto(
                        userId = userKey.first,
                        username = userKey.second,
                        totalGames = totalGames,
                        wins = wins,
                        losses = losses,
                        pushes = pushes,
                        winRate = if (totalGames == 0L) 0.0 else (wins * 100.0 / totalGames),
                        totalBet = totalBet
                    )
                }
            }
            .sortedWith(compareByDescending<TopPlayerDto> { it.winRate }.thenByDescending { it.totalGames })
            .take(limit)
    }

    suspend fun getGameActionHistory(gameId: Int): List<GameActionDto> = DatabaseFactory.dbQuery {
        (GameActionsTable
            .join(UsersTable, JoinType.INNER, additionalConstraint = { GameActionsTable.userId eq UsersTable.id }))
            .selectAll()
            .where { GameActionsTable.gameId eq gameId }
            .orderBy(GameActionsTable.createdAt to SortOrder.ASC)
            .map {
                GameActionDto(
                    actionId = it[GameActionsTable.id],
                    gameId = it[GameActionsTable.gameId],
                    userId = it[GameActionsTable.userId],
                    username = it[UsersTable.username],
                    actionType = it[GameActionsTable.actionType],
                    actionDataJson = it[GameActionsTable.actionData],
                    createdAt = it[GameActionsTable.createdAt]
                )
            }
    }
}
