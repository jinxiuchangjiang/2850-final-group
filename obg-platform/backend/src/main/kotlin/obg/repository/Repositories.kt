package com.obg.repository

import com.obg.model.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.Optional

// ── Users ─────────────────────────────────────────────────────
@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByUsername(username: String): Optional<User>
    fun findByEmail(email: String): Optional<User>
    fun findByUid(uid: String): Optional<User>
    fun existsByUsername(username: String): Boolean
    fun existsByEmail(email: String): Boolean
    fun findByCreatedAtAfter(since: LocalDateTime): List<User>   // used for growth stats
    fun countByStatus(status: UserStatus): Long

    // Case-insensitive search across username, full name, email, and UID
    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(u.fullName) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%',:q,'%')) OR u.uid LIKE CONCAT('%',:q,'%')")
    fun searchUsers(@Param("q") q: String): List<User>
}

// ── Games ─────────────────────────────────────────────────────
@Repository
interface GameRepository : JpaRepository<Game, Long> {
    // Case-insensitive search by title or author
    @Query("SELECT g FROM Game g WHERE LOWER(g.title) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(g.author) LIKE LOWER(CONCAT('%',:q,'%'))")
    fun searchGames(@Param("q") q: String): List<Game>

    // Filter games by a specific tag name
    @Query("SELECT g FROM Game g JOIN g.tags t WHERE t.name = :tag")
    fun findByTag(@Param("tag") tag: String): List<Game>
}

// ── Friendships ───────────────────────────────────────────────
@Repository
interface FriendshipRepository : JpaRepository<Friendship, Long> {
    // All accepted friendships involving this user (either side)
    @Query("SELECT f FROM Friendship f WHERE (f.requester.id = :uid OR f.addressee.id = :uid) AND f.status = 'ACCEPTED'")
    fun findFriendsOf(@Param("uid") userId: Long): List<Friendship>

    // Incoming pending requests (this user is the addressee)
    @Query("SELECT f FROM Friendship f WHERE f.addressee.id = :uid AND f.status = 'PENDING'")
    fun findPendingRequestsFor(@Param("uid") userId: Long): List<Friendship>

    // Outgoing pending requests (this user sent them)
    @Query("SELECT f FROM Friendship f WHERE f.requester.id = :uid AND f.status = 'PENDING'")
    fun findSentRequestsBy(@Param("uid") userId: Long): List<Friendship>

    // Find the friendship record between two users regardless of direction
    @Query("SELECT f FROM Friendship f WHERE (f.requester.id = :a AND f.addressee.id = :b) OR (f.requester.id = :b AND f.addressee.id = :a)")
    fun findBetween(@Param("a") a: Long, @Param("b") b: Long): Optional<Friendship>

    // Remove friendship between two users (used for unfriend)
    @Modifying
    @Query("DELETE FROM Friendship f WHERE (f.requester.id = :a AND f.addressee.id = :b) OR (f.requester.id = :b AND f.addressee.id = :a)")
    fun deleteBetween(@Param("a") a: Long, @Param("b") b: Long)

    // Remove all friendship records when a user is deleted
    @Modifying
    @Query("DELETE FROM Friendship f WHERE f.requester.id = :uid OR f.addressee.id = :uid")
    fun deleteAllByUserId(@Param("uid") userId: Long)
}

// ── Game Rooms ────────────────────────────────────────────────
@Repository
interface GameRoomRepository : JpaRepository<GameRoom, Long> {
    // Rooms for a game excluding the given status (e.g. exclude CLOSED)
    fun findByGame_IdAndStatusNot(gameId: Long, status: RoomStatus): List<GameRoom>
    fun findByStatus(status: RoomStatus): List<GameRoom>
    fun countByStatusIn(statuses: List<RoomStatus>): Long        // used for admin dashboard stats
    fun findByHost_Id(hostId: Long): List<GameRoom>
    fun findAllByGame_Id(gameId: Long): List<GameRoom>

    // Find a WAITING room that this user has already joined for a given game (for rejoin logic)
    @Query("SELECT r FROM GameRoom r JOIN RoomPlayer rp ON rp.room.id = r.id WHERE rp.user.id = :userId AND r.game.id = :gameId AND r.status = 'WAITING'")
    fun findActiveRoomByUserAndGame(@Param("userId") userId: Long, @Param("gameId") gameId: Long): List<GameRoom>
}

// ── Room Players ──────────────────────────────────────────────
@Repository
interface RoomPlayerRepository : JpaRepository<RoomPlayer, Long> {
    fun findByRoom_Id(roomId: Long): List<RoomPlayer>
    fun findByRoom_IdAndUser_Id(roomId: Long, userId: Long): Optional<RoomPlayer>
    fun countByRoom_Id(roomId: Long): Long
    fun existsByRoom_IdAndUser_Id(roomId: Long, userId: Long): Boolean
    fun deleteByRoom_IdAndUser_Id(roomId: Long, userId: Long)    // leave room
    fun findByUser_Id(userId: Long): List<RoomPlayer>            // all rooms a user is in

    // Cascade-delete all room memberships when a user is deleted
    @Modifying
    @Query("DELETE FROM RoomPlayer rp WHERE rp.user.id = :userId")
    fun deleteAllByUserId(@Param("userId") userId: Long)

    // Remove all players from a room (used when closing/resetting a room)
    @Modifying
    @Query("DELETE FROM RoomPlayer rp WHERE rp.room.id = :roomId")
    fun deleteAllByRoomId(@Param("roomId") roomId: Long)
}

// ── Liked Games ───────────────────────────────────────────────
@Repository
interface LikedGameRepository : JpaRepository<LikedGame, Long> {
    fun findByUser_Id(userId: Long): List<LikedGame>
    fun existsByUser_IdAndGame_Id(userId: Long, gameId: Long): Boolean
    fun deleteByUser_IdAndGame_Id(userId: Long, gameId: Long)    // unlike

    // Cascade-delete when user is deleted
    @Modifying
    @Query("DELETE FROM LikedGame lg WHERE lg.user.id = :userId")
    fun deleteAllByUserId(@Param("userId") userId: Long)

    // Cascade-delete when game is deleted
    @Modifying
    @Query("DELETE FROM LikedGame lg WHERE lg.game.id = :gameId")
    fun deleteAllByGameId(@Param("gameId") gameId: Long)
}

// ── Play History ──────────────────────────────────────────────
@Repository
interface PlayHistoryRepository : JpaRepository<PlayHistory, Long> {
    // Returns a user's match history newest-first
    fun findByUser_IdOrderByPlayedAtDesc(userId: Long): List<PlayHistory>

    @Modifying
    @Query("DELETE FROM PlayHistory ph WHERE ph.user.id = :userId")
    fun deleteAllByUserId(@Param("userId") userId: Long)

    @Modifying
    @Query("DELETE FROM PlayHistory ph WHERE ph.game.id = :gameId")
    fun deleteAllByGameId(@Param("gameId") gameId: Long)
}

// ── Tags ──────────────────────────────────────────────────────
@Repository
interface TagRepository : JpaRepository<Tag, Long> {
    fun findByName(name: String): Optional<Tag>
    fun existsByName(name: String): Boolean
    fun findAllByNameIn(names: List<String>): List<Tag>          // batch lookup when saving a game's tag list
}