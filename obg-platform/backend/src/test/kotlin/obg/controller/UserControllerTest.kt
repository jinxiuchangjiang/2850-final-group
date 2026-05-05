package com.obg.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.obg.model.*
import com.obg.repository.*
import com.obg.service.OnlineTracker
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.Optional

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @MockBean lateinit var userRepo: UserRepository
    // We mock these to allow the context to load fully without connecting to real DB operations when not needed
    @MockBean lateinit var likedRepo: LikedGameRepository
    @MockBean lateinit var histRepo: PlayHistoryRepository
    @MockBean lateinit var roomPlayerRepo: RoomPlayerRepository
    @MockBean lateinit var roomRepo: GameRoomRepository
    @MockBean lateinit var friendRepo: FriendshipRepository
    @MockBean lateinit var onlineTracker: OnlineTracker

    @Test
    @WithMockUser(username = "testuser")
    fun `getMe returns user data when authenticated`() {
        val user = User(id = 1L, uid = "U123", username = "testuser", email = "test@example.com", fullName = "Test User")
        `when`(userRepo.findByUsername("testuser")).thenReturn(Optional.of(user))

        mockMvc.perform(get("/api/users/me"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.username").value("testuser"))
            .andExpect(jsonPath("$.data.email").value("test@example.com"))
    }
}
