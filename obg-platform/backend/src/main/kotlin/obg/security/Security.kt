package com.obg.security

import com.obg.repository.UserRepository
import io.jsonwebtoken.*
import io.jsonwebtoken.security.Keys
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.filter.OncePerRequestFilter
import java.util.Date

// ════════════════════════════════════════
// security/Security.kt — JWT + Auth
// ════════════════════════════════════════

// Handles JWT creation and validation. Secret and expiration are read from application.properties.
@Service
class JwtService {
    @Value("\${jwt.secret}")
    lateinit var secret: String

    @Value("\${jwt.expiration}")
    var expiration: Long = 86400000  // default: 24 hours in ms

    // Key is built lazily so it is only created after @Value injection completes
    private val key by lazy { Keys.hmacShaKeyFor(secret.toByteArray()) }

    // Issue a signed HS256 token carrying the user's username and role
    fun generateToken(username: String, role: String): String =
        Jwts.builder()
            .setSubject(username)
            .claim("role", role)
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + expiration))
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()

    fun extractUsername(token: String): String =
        getClaims(token).subject

    // Returns false if the token is expired, malformed, or has an invalid signature
    fun isValid(token: String): Boolean = try {
        getClaims(token); true
    } catch (e: Exception) { false }

    private fun getClaims(token: String): Claims =
        Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).body
}

// Loads a Spring Security UserDetails from the database by username.
// The user's role is mapped to a Spring authority (e.g. ROLE_ADMIN, ROLE_PLAYER).
@Service
class OBGUserDetailsService(private val userRepo: UserRepository) : UserDetailsService {
    override fun loadUserByUsername(username: String): UserDetails =
        userRepo.findByUsername(username).orElseThrow { UsernameNotFoundException("User not found: $username") }
            .let { user ->
                org.springframework.security.core.userdetails.User(
                    user.username, user.password,
                    listOf(SimpleGrantedAuthority("ROLE_${user.role.name}"))
                )
            }
}

// Per-request filter that reads the Bearer token from the Authorization header,
// validates it, and populates the SecurityContext so downstream handlers know who the caller is.
// Extends OncePerRequestFilter to guarantee it runs exactly once per request.
@Component
class JwtAuthFilter(
    private val jwtService: JwtService,
    private val userDetailsService: OBGUserDetailsService
) : OncePerRequestFilter() {

    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        val header = req.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ")) {
            val token = header.substring(7)  // strip "Bearer " prefix
            if (jwtService.isValid(token)) {
                val username = jwtService.extractUsername(token)
                val userDetails = userDetailsService.loadUserByUsername(username)
                // Credentials are null — authentication is already proven by the token
                val auth = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
                SecurityContextHolder.getContext().authentication = auth
            }
        }
        chain.doFilter(req, res)  // always continue the filter chain
    }
}