package com.groupproject.database

import com.groupproject.database.schema.GameActionsTable
import com.groupproject.database.schema.GamePlayersTable
import com.groupproject.database.schema.GamesTable
import com.groupproject.database.schema.UsersTable
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {

    private lateinit var database: Database

    data class DatabaseSettings(
        val jdbcUrl: String,
        val driverClassName: String,
        val username: String,
        val password: String,
        val maximumPoolSize: Int = 10,
        val isAutoCommit: Boolean = false,
        val transactionIsolation: String = "TRANSACTION_REPEATABLE_READ"
    )

    fun init(settings: DatabaseSettings) {
        val hikariDataSource = HikariDataSource(hikariConfig(settings))
        database = Database.connect(hikariDataSource)

        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                UsersTable,
                GamesTable,
                GamePlayersTable,
                GameActionsTable
            )
        }
    }

    suspend fun <T> dbQuery(block: suspend Transaction.() -> T): T =
        newSuspendedTransaction(Dispatchers.IO, database, block)

    private fun hikariConfig(settings: DatabaseSettings): HikariConfig {
        return HikariConfig().apply {
            driverClassName = settings.driverClassName
            jdbcUrl = settings.jdbcUrl
            username = settings.username
            password = settings.password
            maximumPoolSize = settings.maximumPoolSize
            isAutoCommit = settings.isAutoCommit
            transactionIsolation = settings.transactionIsolation
            validate()
        }
    }
}
