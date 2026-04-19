package com.obg.model

import jakarta.persistence.*
import java.time.LocalDateTime

enum class UserRole    { PLAYER, ADMIN }
enum class UserStatus  { ACTIVE, INACTIVE, BANNED }
enum class FriendStatus { PENDING, ACCEPTED, REJECTED }
enum class RoomPrivacy  { PUBLIC, PRIVATE }
enum class RoomStatus   { WAITING, PLAYING, ENDED, CLOSED }

@Entity @Table(name = "users")
data class User(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @Column(unique = true, nullable = false) val username: String = "",
    @Column(nullable = false)                var fullName: String = "",
    @Column(unique = true, nullable = false) var email: String = "",
    @Column(nullable = false)                var password: String = "",
    @Column(unique = true)                   val uid: String = "",
    @Enumerated(EnumType.STRING)             var role: UserRole = UserRole.PLAYER,
    @Enumerated(EnumType.STRING)             var status: UserStatus = UserStatus.ACTIVE,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var avatarUrl: String? = null
)

@Entity @Table(name = "games")
data class Game(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @Column(nullable = false)          var title: String = "",
    var author: String = "",
    @Column(columnDefinition = "TEXT") var description: String = "",
    @Column(columnDefinition = "TEXT") var rules: String = "",
    var coverImageUrl: String? = null,
    var gameFileUrl: String? = null,
    var durationMinutes: Int = 60,
    var minimumAge: Int = 6,
    @Column(columnDefinition = "TEXT") var tags: String = "",
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun getTagList(): List<String> = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    fun setTagList(list: List<String>) { tags = list.joinToString(",") }
}

@Entity @Table(name = "friendships")
data class Friendship(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @ManyToOne @JoinColumn(name = "requester_id") val requester: User? = null,
    @ManyToOne @JoinColumn(name = "addressee_id") val addressee: User? = null,
    @Enumerated(EnumType.STRING) var status: FriendStatus = FriendStatus.PENDING,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

@Entity @Table(name = "game_rooms")
data class GameRoom(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @ManyToOne @JoinColumn(name = "game_id") val game: Game? = null,
    @ManyToOne @JoinColumn(name = "host_id", nullable = true) var host: User? = null,
    var roomName: String = "",
    @Enumerated(EnumType.STRING) var privacy: RoomPrivacy = RoomPrivacy.PUBLIC,
    var passwordHash: String? = null,
    var maxPlayers: Int = 2,
    var currentPlayers: Int = 0,
    @Enumerated(EnumType.STRING) var status: RoomStatus = RoomStatus.WAITING,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

@Entity @Table(name = "room_players")
data class RoomPlayer(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @ManyToOne @JoinColumn(name = "room_id") val room: GameRoom? = null,
    @ManyToOne @JoinColumn(name = "user_id") val user: User? = null,
    val joinedAt: LocalDateTime = LocalDateTime.now(),
    var isReady: Boolean = false
)

@Entity @Table(name = "liked_games")
data class LikedGame(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @ManyToOne @JoinColumn(name = "user_id") val user: User? = null,
    @ManyToOne @JoinColumn(name = "game_id") val game: Game? = null,
    val likedAt: LocalDateTime = LocalDateTime.now()
)

@Entity @Table(name = "play_history")
data class PlayHistory(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @ManyToOne @JoinColumn(name = "user_id") val user: User? = null,
    @ManyToOne @JoinColumn(name = "game_id") val game: Game? = null,
    val playedAt: LocalDateTime = LocalDateTime.now(),
    var durationMinutes: Int = 0,
    var result: String = "UNKNOWN"   // WIN | LOSS | DRAW | UNKNOWN
)

@Entity @Table(name = "tags")
data class Tag(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @Column(unique = true, nullable = false) var name: String = "",
    var description: String = "",
    val createdAt: LocalDateTime = LocalDateTime.now()
)
