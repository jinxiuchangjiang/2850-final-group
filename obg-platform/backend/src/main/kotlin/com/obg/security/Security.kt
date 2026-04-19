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

@Service
class JwtService {
    @Value("\${jwt.secret}")
    lateinit var secret: String

    @Value("\${jwt.expiration}")
    var expiration: Long = 86400000

    private val key by lazy { Keys.hmacShaKeyFor(secret.toByteArray()) }

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

    fun isValid(token: String): Boolean = try {
        getClaims(token); true
    } catch (e: Exception) { false }

    private fun getClaims(token: String): Claims =
        Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).body
}

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

@Component
class JwtAuthFilter(
    private val jwtService: JwtService,
    private val userDetailsService: OBGUserDetailsService
) : OncePerRequestFilter() {

    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        val header = req.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ")) {
            val token = header.substring(7)
            if (jwtService.isValid(token)) {
                val username = jwtService.extractUsername(token)
                val userDetails = userDetailsService.loadUserByUsername(username)
                val auth = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
                SecurityContextHolder.getContext().authentication = auth
            }
        }
        chain.doFilter(req, res)
    }
}
