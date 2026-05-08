package com.obg.controller

import com.obg.model.*
import com.obg.repository.*
import com.obg.security.JwtService
import com.obg.service.OnlineTracker
import com.obg.service.UploadService
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.security.Principal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

// ── AUTH ──────────────────────────────────────────────────────────────────────
@RestController @RequestMapping("/api/auth")
class AuthController(
    private val userRepo: UserRepository,
    private val encoder: PasswordEncoder,
    private val authManager: AuthenticationManager,
    private val jwtService: JwtService
) {
    @PostMapping("/register")
    fun register(@RequestBody req: RegisterRequest): ResponseEntity<ApiResponse<AuthResponse>> {
        if (userRepo.existsByUsername(req.username))
            return ResponseEntity.badRequest().body(ApiResponse(false, "Username already taken"))
        if (userRepo.existsByEmail(req.email))
            return ResponseEntity.badRequest().body(ApiResponse(false, "Email already registered"))
        val uid  = "UID-${(1000..9999).random()}-${System.currentTimeMillis() % 10000}"
        val user = userRepo.save(User(username = req.username, fullName = req.fullName,
            email = req.email, password = encoder.encode(req.password), uid = uid))
        val token = jwtService.generateToken(user.username, user.role.name)
        return ResponseEntity.ok(ApiResponse(true, "Registered",
            AuthResponse(token, user.uid, user.username, user.fullName, user.email, user.role.name)))
    }

    @PostMapping("/login")
    fun login(@RequestBody req: LoginRequest): ResponseEntity<ApiResponse<AuthResponse>> {
        return try {
            authManager.authenticate(UsernamePasswordAuthenticationToken(req.username, req.password))
            val user = userRepo.findByUsername(req.username).get()
            if (user.status == UserStatus.BANNED)
                return ResponseEntity.status(403).body(ApiResponse(false, "Account has been banned"))
            val token = jwtService.generateToken(user.username, user.role.name)
            ResponseEntity.ok(ApiResponse(true, "OK",
                AuthResponse(token, user.uid, user.username, user.fullName, user.email, user.role.name)))
        } catch (_: Exception) {
            ResponseEntity.status(401).body(ApiResponse(false, "Invalid credentials"))
        }
    }
}

// ── USER ──────────────────────────────────────────────────────────────────────
@RestController @RequestMapping("/api/users")
class UserController(
    private val userRepo: UserRepository,
    private val encoder: PasswordEncoder,
    private val likedRepo: LikedGameRepository,
    private val histRepo: PlayHistoryRepository,
    private val roomPlayerRepo: RoomPlayerRepository,
    private val roomRepo: GameRoomRepository,
    private val friendRepo: FriendshipRepository,
    private val onlineTracker: OnlineTracker
) {
    @GetMapping("/me")
    fun getMe(p: Principal) = userRepo.findByUsername(p.name).orElseThrow().let {
        ResponseEntity.ok(ApiResponse(true, data = it.toDto()))
    }

    @PutMapping("/me")
    fun updateMe(@RequestBody req: UpdateProfileRequest, p: Principal): ResponseEntity<ApiResponse<UserDto>> {
        val user = userRepo.findByUsername(p.name).orElseThrow()
        return ResponseEntity.ok(ApiResponse(true, "Updated",
            userRepo.save(user.copy(fullName = req.fullName, email = req.email)).toDto()))
    }

    @PostMapping("/me/password")
    fun changePassword(@RequestBody req: ChangePasswordRequest, p: Principal): ResponseEntity<ApiResponse<Unit>> {
        val user = userRepo.findByUsername(p.name).orElseThrow()
        if (!encoder.matches(req.oldPassword, user.password))
            return ResponseEntity.badRequest().body(ApiResponse(false, "Current password is wrong"))
        if (req.newPassword != req.confirmPassword)
            return ResponseEntity.badRequest().body(ApiResponse(false, "Passwords do not match"))
        if (req.newPassword.length < 8)
            return ResponseEntity.badRequest().body(ApiResponse(false, "Password too short (min 8 chars)"))
        userRepo.save(user.copy(password = encoder.encode(req.newPassword)))
        return ResponseEntity.ok(ApiResponse(true, "Password updated"))
    }

    @PostMapping("/me/heartbeat")
    fun heartbeat(
        @RequestBody(required = false) body: Map<String, String>?,
        p: Principal
    ): ResponseEntity<ApiResponse<Unit>> {
        val user   = userRepo.findByUsername(p.name).orElseThrow()
        val status = when (body?.get("status")) {
            "IN_GAME" -> OnlineTracker.Status.IN_GAME
            else      -> OnlineTracker.Status.IDLE
        }
        onlineTracker.seen(user.uid, status)
        return ResponseEntity.ok(ApiResponse(true))
    }

    @DeleteMapping("/me")
    @Transactional
    fun deleteMe(p: Principal): ResponseEntity<ApiResponse<Unit>> {
        val user = userRepo.findByUsername(p.name).orElseThrow()
        purgeUserData(user.id)
        userRepo.deleteById(user.id)
        return ResponseEntity.ok(ApiResponse(true, "Account deleted"))
    }

    fun User.toDto() = UserDto(id, uid, username, fullName, email, role.name, status.name,
        createdAt.format(DATE_FMT), avatarUrl, onlineTracker.isOnline(uid))

    @Transactional
    fun purgeUserData(userId: Long) {
        likedRepo.deleteAllByUserId(userId)
        histRepo.deleteAllByUserId(userId)
        friendRepo.deleteAllByUserId(userId)
        roomPlayerRepo.deleteAllByUserId(userId)
        val hostedRooms = roomRepo.findByHost_Id(userId)
        for (room in hostedRooms) {
            roomPlayerRepo.deleteAllByRoomId(room.id)
            roomRepo.deleteById(room.id)
        }
    }
}

// ── GAMES (public read) ───────────────────────────────────────────────────────
@RestController @RequestMapping("/api/games")
class GameController(private val gameRepo: GameRepository) {

    @GetMapping
    fun getAll(@RequestParam(required = false) q: String?, @RequestParam(required = false) tag: String?) =
        ResponseEntity.ok(ApiResponse(true, data = when {
            !q.isNullOrBlank()   -> gameRepo.searchGames(q)
            !tag.isNullOrBlank() -> gameRepo.findByTag(tag)
            else                 -> gameRepo.findAll()
        }.map { it.toDto() }))

    @GetMapping("/{id}")
    fun getOne(@PathVariable id: Long) = gameRepo.findById(id)
        .map { ResponseEntity.ok(ApiResponse(true, data = it.toDto())) }
        .orElse(ResponseEntity.notFound().build())

    fun Game.toDto() = GameDto(id, title, author, description, rules, coverImageUrl,
        gameFileUrl, durationMinutes, minimumAge,  maxPlayers, tags.map { it.name })
}

// ── ADMIN GAMES ───────────────────────────────────────────────────────────────
@RestController @RequestMapping("/api/admin/games")
class AdminGameController(
    private val gameRepo: GameRepository,
    private val tagRepo: TagRepository,
    private val uploadService: UploadService,
    private val likedRepo: LikedGameRepository,
    private val histRepo: PlayHistoryRepository,
    private val roomPlayerRepo: RoomPlayerRepository,
    private val roomRepo: GameRoomRepository
) {
    @PostMapping
    fun create(@RequestBody req: CreateGameRequest): ResponseEntity<ApiResponse<GameDto>> {
        val tagEntities = tagRepo.findAllByNameIn(req.tags).toMutableSet()
        val g = gameRepo.save(Game(
            title = req.title, author = req.author,
            description = req.description, rules = req.rules,
            durationMinutes = req.durationMinutes,
            minimumAge = req.minimumAge,
            maxPlayers = req.maxPlayers,
            tags = tagEntities
        ))
        return ResponseEntity.ok(ApiResponse(true, "Created", g.toDto()))
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody req: UpdateGameRequest): ResponseEntity<ApiResponse<GameDto>> {
        val g = gameRepo.findById(id).orElse(null) ?: return ResponseEntity.notFound().build()
        req.title?.let { g.title = it }; req.author?.let { g.author = it }
        req.description?.let { g.description = it }; req.rules?.let { g.rules = it }
        req.durationMinutes?.let { g.durationMinutes = it }
        req.minimumAge?.let { g.minimumAge = it }
        req.maxPlayers?.let { g.maxPlayers = it }


        req.tags?.let { g.tags = tagRepo.findAllByNameIn(it).toMutableSet() }
        g.updatedAt = LocalDateTime.now()
        return ResponseEntity.ok(ApiResponse(true, "Updated", gameRepo.save(g).toDto()))
    }

    @PostMapping("/{id}/cover")
    fun uploadCover(@PathVariable id: Long, @RequestParam("file") f: MultipartFile): ResponseEntity<ApiResponse<String>> {
        val g = gameRepo.findById(id).orElse(null) ?: return ResponseEntity.notFound().build()
        uploadService.deleteFile(g.coverImageUrl)
        val url = uploadService.saveFile(f, "covers"); g.coverImageUrl = url; gameRepo.save(g)
        return ResponseEntity.ok(ApiResponse(true, "Uploaded", url))
    }

    @PostMapping("/{id}/game-file")
    fun uploadGameFile(@PathVariable id: Long, @RequestParam("file") f: MultipartFile): ResponseEntity<ApiResponse<String>> {
        val g = gameRepo.findById(id).orElse(null) ?: return ResponseEntity.notFound().build()
        uploadService.deleteFile(g.gameFileUrl)
        val url = uploadService.saveFile(f, "games"); g.gameFileUrl = url; gameRepo.save(g)
        return ResponseEntity.ok(ApiResponse(true, "Uploaded", url))
    }

    @DeleteMapping("/{id}")
    @Transactional
    fun delete(@PathVariable id: Long): ResponseEntity<ApiResponse<Unit>> {
        val g = gameRepo.findById(id).orElse(null) ?: return ResponseEntity.notFound().build()
        uploadService.deleteFile(g.coverImageUrl); uploadService.deleteFile(g.gameFileUrl)
        // Clear all FK references before deleting the game
        likedRepo.deleteAllByGameId(id)
        histRepo.deleteAllByGameId(id)
        roomRepo.findAllByGame_Id(id).forEach { room -> roomPlayerRepo.deleteAllByRoomId(room.id) }
        roomRepo.deleteAll(roomRepo.findAllByGame_Id(id))
        gameRepo.deleteById(id)
        return ResponseEntity.ok(ApiResponse(true, "Deleted"))
    }

    fun Game.toDto() = GameDto(id, title, author, description, rules, coverImageUrl,
        gameFileUrl, durationMinutes, minimumAge,  maxPlayers, tags.map { it.name })
}

// ── ADMIN USERS ───────────────────────────────────────────────────────────────
@RestController @RequestMapping("/api/admin/users")
class AdminUserController(
    private val userRepo: UserRepository,
    private val onlineTracker: OnlineTracker
) {
    @GetMapping
    fun getAll(@RequestParam(required = false) q: String?) = ResponseEntity.ok(ApiResponse(true, data =
        (if (!q.isNullOrBlank()) userRepo.searchUsers(q) else userRepo.findAll()).map { it.toDto() }))

    @GetMapping("/{id}")
    fun getOne(@PathVariable id: Long): ResponseEntity<ApiResponse<UserDto>> {
        val u = userRepo.findById(id).orElse(null) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(ApiResponse(true, data = u.toDto()))
    }

    @PutMapping("/{id}/ban")
    fun ban(@PathVariable id: Long): ResponseEntity<ApiResponse<Unit>> {
        val u = userRepo.findById(id).orElse(null) ?: return ResponseEntity.notFound().build()
        userRepo.save(u.copy(status = UserStatus.BANNED))
        return ResponseEntity.ok(ApiResponse(true, "Banned"))
    }

    @PutMapping("/{id}/unban")
    fun unban(@PathVariable id: Long): ResponseEntity<ApiResponse<Unit>> {
        val u = userRepo.findById(id).orElse(null) ?: return ResponseEntity.notFound().build()
        userRepo.save(u.copy(status = UserStatus.ACTIVE))
        return ResponseEntity.ok(ApiResponse(true, "Unbanned"))
    }

    private fun User.toDto() = UserDto(id, uid, username, fullName, email, role.name, status.name,
        createdAt.format(DATE_FMT), avatarUrl, onlineTracker.isOnline(uid))
}

// ── TAGS ──────────────────────────────────────────────────────────────────────
@RestController @RequestMapping("/api/tags")
class TagController(private val tagRepo: TagRepository) {

    @GetMapping
    fun getAll() = ResponseEntity.ok(ApiResponse(true, data =
        tagRepo.findAll().map { TagDto(it.id, it.name, it.description) }))

    @PostMapping
    fun create(@RequestBody req: CreateTagRequest): ResponseEntity<ApiResponse<TagDto>> {
        if (tagRepo.existsByName(req.name))
            return ResponseEntity.badRequest().body(ApiResponse(false, "Tag already exists"))
        val t = tagRepo.save(Tag(name = req.name, description = req.description))
        return ResponseEntity.ok(ApiResponse(true, "Created", TagDto(t.id, t.name, t.description)))
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody req: UpdateTagRequest): ResponseEntity<ApiResponse<TagDto>> {
        val t = tagRepo.findById(id).orElse(null) ?: return ResponseEntity.notFound().build()
        t.name = req.name; t.description = req.description
        val saved = tagRepo.save(t)
        return ResponseEntity.ok(ApiResponse(true, "Updated", TagDto(saved.id, saved.name, saved.description)))
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<ApiResponse<Unit>> {
        tagRepo.deleteById(id); return ResponseEntity.ok(ApiResponse(true, "Deleted"))
    }
}

// ── ADMIN STATS ───────────────────────────────────────────────────────────────
@RestController @RequestMapping("/api/admin/stats")
class AdminStatsController(
    private val userRepo: UserRepository,
    private val gameRepo: GameRepository,
    private val roomRepo: GameRoomRepository
) {
    @GetMapping("/summary")
    fun summary(): ResponseEntity<ApiResponse<Map<String, Any>>> {
        val result: Map<String, Any> = mapOf(
            "totalGames"  to gameRepo.count(),
            "totalUsers"  to userRepo.count(),
            "activeUsers" to userRepo.countByStatus(UserStatus.ACTIVE),
            "activeRooms" to roomRepo.countByStatusIn(listOf(RoomStatus.WAITING, RoomStatus.PLAYING))
        )
        return ResponseEntity.ok(ApiResponse(true, data = result))
    }

    @GetMapping("/user-growth")
    fun userGrowth(): ResponseEntity<ApiResponse<List<UserGrowthDto>>> {
        val since  = LocalDateTime.now().minusDays(30)
        val users  = userRepo.findByCreatedAtAfter(since)
        val today  = LocalDate.now()
        val result = (29 downTo 0).map { i ->
            val d = today.minusDays(i.toLong())
            UserGrowthDto(d.toString(), users.count { it.createdAt.toLocalDate() == d })
        }
        return ResponseEntity.ok(ApiResponse(true, data = result))
    }
}