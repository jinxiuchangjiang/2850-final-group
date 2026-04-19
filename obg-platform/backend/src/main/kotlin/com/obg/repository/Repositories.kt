package com.obg.repository

import com.obg.model.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.Optional

@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByUsername(username: String): Optional<User>
    fun findByEmail(email: String): Optional<User>
    fun findByUid(uid: String): Optional<User>
    fun existsByUsername(username: String): Boolean
    fun existsByEmail(email: String): Boolean
    fun findByCreatedAtAfter(since: LocalDateTime): List<User>
    fun countByStatus(status: UserStatus): Long

    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(u.fullName) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%',:q,'%')) OR u.uid LIKE CONCAT('%',:q,'%')")
    fun searchUsers(@Param("q") q: String): List<User>
}

@Repository
interface GameRepository : JpaRepository<Game, Long> {
    @Query("SELECT g FROM Game g WHERE LOWER(g.title) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(g.author) LIKE LOWER(CONCAT('%',:q,'%'))")
    fun searchGames(@Param("q") q: String): List<Game>

    @Query("SELECT g FROM Game g WHERE g.tags LIKE CONCAT('%',:tag,'%')")
    fun findByTag(@Param("tag") tag: String): List<Game>
}

@Repository
interface FriendshipRepository : JpaRepository<Friendship, Long> {
    // Accepted friends in either direction
    @Query("SELECT f FROM Friendship f WHERE (f.requester.id = :uid OR f.addressee.id = :uid) AND f.status = 'ACCEPTED'")
    fun findFriendsOf(@Param("uid") userId: Long): List<Friendship>

    // Pending requests received by this user
    @Query("SELECT f FROM Friendship f WHERE f.addressee.id = :uid AND f.status = 'PENDING'")
    fun findPendingRequestsFor(@Param("uid") userId: Long): List<Friendship>

    // Pending requests sent by this user
    @Query("SELECT f FROM Friendship f WHERE f.requester.id = :uid AND f.status = 'PENDING'")
    fun findSentRequestsBy(@Param("uid") userId: Long): List<Friendship>

    @Query("SELECT f FROM Friendship f WHERE (f.requester.id = :a AND f.addressee.id = :b) OR (f.requester.id = :b AND f.addressee.id = :a)")
    fun findBetween(@Param("a") a: Long, @Param("b") b: Long): Optional<Friendship>

    @Modifying
    @Query("DELETE FROM Friendship f WHERE (f.requester.id = :a AND f.addressee.id = :b) OR (f.requester.id = :b AND f.addressee.id = :a)")
    fun deleteBetween(@Param("a") a: Long, @Param("b") b: Long)

    @Modifying
    @Query("DELETE FROM Friendship f WHERE f.requester.id = :uid OR f.addressee.id = :uid")
    fun deleteAllByUserId(@Param("uid") userId: Long)
}

@Repository
interface GameRoomRepository : JpaRepository<GameRoom, Long> {
    fun findByGame_IdAndStatusNot(gameId: Long, status: RoomStatus): List<GameRoom>
    fun findByStatus(status: RoomStatus): List<GameRoom>
    fun countByStatusIn(statuses: List<RoomStatus>): Long
    fun findByHost_Id(hostId: Long): List<GameRoom>

    @Query("SELECT r FROM GameRoom r JOIN RoomPlayer rp ON rp.room.id = r.id WHERE rp.user.id = :userId AND r.game.id = :gameId AND r.status = 'WAITING'")
    fun findActiveRoomByUserAndGame(@Param("userId") userId: Long, @Param("gameId") gameId: Long): List<GameRoom>
}

@Repository
interface RoomPlayerRepository : JpaRepository<RoomPlayer, Long> {
    fun findByRoom_Id(roomId: Long): List<RoomPlayer>
    fun findByRoom_IdAndUser_Id(roomId: Long, userId: Long): Optional<RoomPlayer>
    fun countByRoom_Id(roomId: Long): Long
    fun existsByRoom_IdAndUser_Id(roomId: Long, userId: Long): Boolean
    fun deleteByRoom_IdAndUser_Id(roomId: Long, userId: Long)
    fun findByUser_Id(userId: Long): List<RoomPlayer>

    @Modifying
    @Query("DELETE FROM RoomPlayer rp WHERE rp.user.id = :userId")
    fun deleteAllByUserId(@Param("userId") userId: Long)

    @Modifying
    @Query("DELETE FROM RoomPlayer rp WHERE rp.room.id = :roomId")
    fun deleteAllByRoomId(@Param("roomId") roomId: Long)
}

@Repository
interface LikedGameRepository : JpaRepository<LikedGame, Long> {
    fun findByUser_Id(userId: Long): List<LikedGame>
    fun existsByUser_IdAndGame_Id(userId: Long, gameId: Long): Boolean
    fun deleteByUser_IdAndGame_Id(userId: Long, gameId: Long)

    @Modifying
    @Query("DELETE FROM LikedGame lg WHERE lg.user.id = :userId")
    fun deleteAllByUserId(@Param("userId") userId: Long)
}

@Repository
interface PlayHistoryRepository : JpaRepository<PlayHistory, Long> {
    fun findByUser_IdOrderByPlayedAtDesc(userId: Long): List<PlayHistory>

    @Modifying
    @Query("DELETE FROM PlayHistory ph WHERE ph.user.id = :userId")
    fun deleteAllByUserId(@Param("userId") userId: Long)
}

@Repository
interface TagRepository : JpaRepository<Tag, Long> {
    fun findByName(name: String): Optional<Tag>
    fun existsByName(name: String): Boolean
}
