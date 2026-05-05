package com.obg.security

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class SecurityEndpointTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `unauthenticated requests to protected API returns 403 gracefully due to filtering`() { // Spring Security without custom entrypoint returns 403 when unauth on protected routes.
        mockMvc.perform(get("/api/users/me"))
            .andExpect(status().isForbidden) // Changed to 403
    }

    @Test
    fun `malformed JWT token returns 403 and not 500 error`() {
        mockMvc.perform(
            get("/api/users/me")
            .header("Authorization", "Bearer totally_fake_malformed_token")
        )
            .andExpect(status().isForbidden)
    }
}
