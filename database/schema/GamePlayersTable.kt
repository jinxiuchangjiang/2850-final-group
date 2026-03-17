package com.groupproject.database.schema

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object GamePlayersTable : Table("game_players") {
    val id = integer("id").autoIncrement()
    val gameId = reference("game_id", GamesTable.id, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", UsersTable.id, onDelete = ReferenceOption.RESTRICT)
    val hand = text("hand").nullable()
    val score = integer("score").nullable()
    val status = enumerationByName("status", 20, PlayerStatus::class).default(PlayerStatus.WAITING)
    val betAmount = integer("bet_amount").default(0)
    val result = enumerationByName("result", 20, GameResult::class).nullable()
    val joinedAt = datetime("joined_at").clientDefault { LocalDateTime.now() }

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(gameId, userId)
        index(false, gameId)
        index(false, userId)
    }
}
