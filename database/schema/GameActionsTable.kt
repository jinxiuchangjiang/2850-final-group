package com.groupproject.database.schema

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object GameActionsTable : Table("game_actions") {
    val id = integer("id").autoIncrement()
    val gameId = reference("game_id", GamesTable.id, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", UsersTable.id, onDelete = ReferenceOption.RESTRICT)
    val actionType = enumerationByName("action_type", 20, ActionType::class)
    val actionData = text("action_data").nullable()
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, gameId, createdAt)
    }
}
