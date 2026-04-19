package com.obg.model

// ── AUTH ──────────────────────────────────────────────────────────────────────
data class RegisterRequest(val username: String, val fullName: String, val email: String, val password: String)
data class LoginRequest(val username: String, val password: String)
data class AuthResponse(val token: String, val uid: String, val username: String, val fullName: String, val email: String, val role: String)
data class ChangePasswordRequest(val oldPassword: String, val newPassword: String, val confirmPassword: String)

// ── USER ──────────────────────────────────────────────────────────────────────
data class UpdateProfileRequest(val fullName: String, val email: String)
data class UserDto(val id: Long, val uid: String, val username: String, val fullName: String,
    val email: String, val role: String, val status: String, val registeredAt: String,
    val avatarUrl: String?, val isOnline: Boolean = false)

// ── GAME (rulesPdfUrl removed — PDF functionality disabled) ──────────────────
data class GameDto(val id: Long, val title: String, val author: String, val description: String,
    val rules: String, val coverImageUrl: String?, val gameFileUrl: String?,
    val durationMinutes: Int, val minimumAge: Int, val tags: List<String>)
data class CreateGameRequest(val title: String, val author: String, val description: String,
    val rules: String, val durationMinutes: Int, val minimumAge: Int, val tags: List<String>)
data class UpdateGameRequest(val title: String?, val author: String?, val description: String?,
    val rules: String?, val durationMinutes: Int?, val minimumAge: Int?, val tags: List<String>?)

// ── ROOM ──────────────────────────────────────────────────────────────────────
data class CreateRoomRequest(val gameId: Long, val roomName: String, val privacy: String,
    val password: String?, val maxPlayers: Int)
data class JoinRoomRequest(val password: String?)
// uid is null in lobby (privacy); shown during PLAYING
data class RoomPlayerDto(val username: String, val fullName: String, val uid: String?, val isReady: Boolean = false)
data class RoomDto(val id: Long, val gameId: Long, val gameTitle: String, val roomName: String,
    val hostUsername: String, val privacy: String, val status: String, val currentPlayers: Int,
    val maxPlayers: Int, val players: List<RoomPlayerDto>, val isMember: Boolean = false)

// ── FRIENDS ───────────────────────────────────────────────────────────────────
// Received friend requests (you are the addressee)
data class FriendRequestDto(val id: Long, val requesterUid: String, val requesterUsername: String,
    val requesterFullName: String, val status: String, val createdAt: String)
// Sent friend requests (you are the requester)
data class SentRequestDto(val id: Long, val addresseeUid: String, val addresseeUsername: String,
    val addresseeFullName: String, val status: String, val createdAt: String)
data class FriendDto(val uid: String, val username: String, val fullName: String, val avatarUrl: String?, val isOnline: Boolean, val status: String )

// ── TAGS ──────────────────────────────────────────────────────────────────────
data class TagDto(val id: Long, val name: String, val description: String)
data class CreateTagRequest(val name: String, val description: String)
data class UpdateTagRequest(val name: String, val description: String)

// ── STATS ─────────────────────────────────────────────────────────────────────
data class UserGrowthDto(val date: String, val count: Int)

// ── GENERIC ───────────────────────────────────────────────────────────────────
data class ApiResponse<T>(val success: Boolean, val message: String = "", val data: T? = null)
