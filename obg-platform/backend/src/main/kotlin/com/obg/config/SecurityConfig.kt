package com.obg.config

import com.obg.security.JwtAuthFilter
import com.obg.security.OBGUserDetailsService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration @EnableWebSecurity @EnableMethodSecurity
class SecurityConfig(private val jwtFilter: JwtAuthFilter, private val uds: OBGUserDetailsService) {

    @Bean fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean fun authProvider(): AuthenticationProvider = DaoAuthenticationProvider().also {
        it.setUserDetailsService(uds); it.setPasswordEncoder(passwordEncoder())
    }

    @Bean fun authManager(cfg: AuthenticationConfiguration): AuthenticationManager = cfg.authenticationManager

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfig()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/games/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/tags/**").permitAll()
                    .requestMatchers("/uploads/**").permitAll()
                    .requestMatchers("/ws/**").permitAll()          // WebSocket — JWT verified inside handler
                    .requestMatchers("/", "/index.html", "/css/**", "/js/**", "/icons/**").permitAll()
                    .requestMatchers("/h2-console/**").permitAll()
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated()
            }
            .authenticationProvider(authProvider())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
            .headers { it.frameOptions { f -> f.disable() } }
        return http.build()
    }

    @Bean
    fun corsConfig(): CorsConfigurationSource = UrlBasedCorsConfigurationSource().also { src ->
        val cfg = CorsConfiguration()
        cfg.allowedOriginPatterns = listOf("*")
        cfg.allowedMethods = listOf("GET","POST","PUT","PATCH","DELETE","OPTIONS")
        cfg.allowedHeaders = listOf("*")
        cfg.allowCredentials = true
        src.registerCorsConfiguration("/**", cfg)
    }
}
