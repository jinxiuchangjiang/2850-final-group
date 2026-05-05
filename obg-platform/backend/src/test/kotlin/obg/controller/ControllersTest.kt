package com.obg.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class ControllersTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `context loads and basic unauthenticated endpoint behaves as expected`() {
        // Without authentication we should get either 200 or 401 depending on path
        mockMvc.perform(get("/api/games"))
            .andExpect(status().isOk) // Assuming /api/games is public or mock
            .andReturn()
    }
}
