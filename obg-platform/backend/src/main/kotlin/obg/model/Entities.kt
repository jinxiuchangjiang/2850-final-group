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
    var maxPlayers: Int = 10,   // ← 新增：游戏最多允许几人
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    // ✅ 改为真正的多对多关联，自动生成 game_tags 中间表
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "game_tags",
        joinColumns = [JoinColumn(name = "game_id")],
        inverseJoinColumns = [JoinColumn(name = "tag_id")]
    )
    var tags: MutableSet<Tag> = mutableSetOf()
)

@Entity @Table(name = "friendships",
    uniqueConstraints = [UniqueConstraint(columnNames = ["requester_id", "addressee_id"])])
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
    // ✅ 删掉 currentPlayers — 改用 roomPlayerRepo.countByRoom_Id() 实时查询
    @Enumerated(EnumType.STRING) var status: RoomStatus = RoomStatus.WAITING,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

@Entity @Table(name = "room_players",
    uniqueConstraints = [UniqueConstraint(columnNames = ["room_id", "user_id"])])
data class RoomPlayer(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @ManyToOne @JoinColumn(name = "room_id") val room: GameRoom? = null,
    @ManyToOne @JoinColumn(name = "user_id") val user: User? = null,
    val joinedAt: LocalDateTime = LocalDateTime.now(),
    var isReady: Boolean = false
)

@Entity @Table(name = "liked_games",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "game_id"])])
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
