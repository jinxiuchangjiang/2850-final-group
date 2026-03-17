package com.groupproject.database.schema

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object GamesTable : Table("games") {
    val id = integer("id").autoIncrement()
    val dealerId = reference("dealer_id", UsersTable.id, onDelete = ReferenceOption.RESTRICT)
    val status = enumerationByName("status", 20, GameStatus::class).default(GameStatus.WAITING)
    val currentPlayerId =
        optReference("current_player_id", UsersTable.id, onDelete = ReferenceOption.SET_NULL)
    val dealerHand = text("dealer_hand").nullable()
    val dealerScore = integer("dealer_score").nullable()
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
    val startedAt = datetime("started_at").nullable()
    val finishedAt = datetime("finished_at").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, status)
        index(false, createdAt)
    }
}
