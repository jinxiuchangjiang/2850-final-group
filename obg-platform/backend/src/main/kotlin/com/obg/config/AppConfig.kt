package com.obg.config

import com.obg.model.*
import com.obg.repository.*
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.file.Paths

@Configuration
class WebConfig : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val uploadPath = Paths.get("uploads").toAbsolutePath().toString()
        registry.addResourceHandler("/uploads/**").addResourceLocations("file:$uploadPath/")
        registry.addResourceHandler("/**").addResourceLocations("classpath:/static/")
    }
}

@Configuration
class DataSeeder(
    private val userRepo: UserRepository,
    private val gameRepo: GameRepository,
    private val tagRepo: TagRepository,
    private val encoder: PasswordEncoder
) {
    @Bean
    fun seedData() = CommandLineRunner {

        // ── Admin account (created once) ──
        if (!userRepo.existsByUsername("admin")) {
            userRepo.save(User(
                username = "admin", fullName = "Administrator",
                email = "admin@obg.local",
                password = encoder.encode("admin123"),
                uid = "UID-ADMIN-001", role = UserRole.ADMIN
            ))
            println("✅ Admin account created  user:admin  pass:admin123")
        }

        // ── Default tags ──
        listOf(
            "Strategy" to "Games requiring strategic thinking",
            "Two Players" to "Best for exactly 2 players",
            "Board Game" to "Traditional board games",
            "Puzzle" to "Puzzle and logic games",
            "Quick Play" to "Under 30 minutes",
            "Family" to "Suitable for all ages",
            "Card Game" to "Primarily uses cards",
            "Party" to "Fun games for groups"
        ).forEach { (n, d) -> if (!tagRepo.existsByName(n)) tagRepo.save(Tag(name = n, description = d)) }

        // ── Seed Gomoku as the first game ──
        if (gameRepo.count() == 0L) {
            gameRepo.save(Game(
                title = "Gomoku (五子棋)",
                author = "Traditional / Classic",
                description = "A classic two-player strategy board game. Players take turns placing stones on a 15×15 grid. The first player to form an unbroken chain of five stones in a row — horizontally, vertically, or diagonally — wins.",
                rules = """<ol>
<li><strong>Black plays first.</strong> Players alternate placing one stone per turn.</li>
<li>Click any empty intersection on the board to place your stone.</li>
<li>The goal is to connect <strong>exactly 5 stones</strong> in a row in any direction.</li>
<li>Horizontal, vertical, and diagonal lines all count.</li>
<li>You can <strong>Resign</strong> at any time to concede the game.</li>
<li>If the board fills up with no winner, the game is a draw.</li>
</ol>""",
                durationMinutes = 30,
                minimumAge = 6,
                tags = "Strategy,Two Players,Board Game,Quick Play",
                // Built-in Gomoku game file served from the static folder
                gameFileUrl = "/games/gomoku.html"
            ))
            println("✅ Gomoku game seeded")
        }
    }
}
