package com.obg.service

import com.obg.model.*
import com.obg.repository.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class GameSessionServiceTest {

    @Mock lateinit var roomRepo: GameRoomRepository
    @Mock lateinit var playHistRepo: PlayHistoryRepository
    @Mock lateinit var userRepo: UserRepository

    @InjectMocks
    lateinit var service: GameSessionService

    @Test
    fun `recordGameEnd records history properly for WIN and LOSS and sets status ENDED`() {
        val game = Game(title = "Gomoku", maxPlayers = 2, gameFileUrl = "gomoku.html")
        val room = GameRoom(id = 1L, game = game, status = RoomStatus.PLAYING)
        val user1 = User(uid = "U1", username = "p1", password = "p", email = "1@e", fullName = "p 1")
        val user2 = User(uid = "U2", username = "p2", password = "p", email = "2@e", fullName = "p 2")

        `when`(roomRepo.findById(1L)).thenReturn(Optional.of(room))
        `when`(userRepo.findByUid("U1")).thenReturn(Optional.of(user1))
        `when`(userRepo.findByUid("U2")).thenReturn(Optional.of(user2))

        service.recordGameEnd(1L, setOf("U1", "U2"), System.currentTimeMillis() - 120_000, "U1")

        verify(playHistRepo, times(2)).save(any())
        // room status update
        verify(roomRepo).save(argThat { it.status == RoomStatus.ENDED })
    }

    @Test
    fun `recordGameEnd gracefully handles missing room or game`() {
        `when`(roomRepo.findById(99L)).thenReturn(Optional.empty())

        // Should return early, no exceptions
        assertDoesNotThrow {
            service.recordGameEnd(99L, setOf("U1"), System.currentTimeMillis())
        }
        verify(playHistRepo, never()).save(any())
        
        val roomNoGame = GameRoom(id = 100L, game = null, status = RoomStatus.PLAYING)
        `when`(roomRepo.findById(100L)).thenReturn(Optional.of(roomNoGame))
        assertDoesNotThrow {
            service.recordGameEnd(100L, setOf("U1"), System.currentTimeMillis())
        }
        verify(playHistRepo, never()).save(any())
    }
    
    @Test
    fun `recordGameEnd gracefully handles missing user in history recording without affecting others`() {
        val game = Game(title = "Gomoku", maxPlayers = 2, gameFileUrl = "gomoku.html")
        val room = GameRoom(id = 2L, game = game, status = RoomStatus.PLAYING)
        
        val validUser = User(uid = "U1", username = "p1", password = "p", email = "1@e", fullName = "p 1")

        `when`(roomRepo.findById(2L)).thenReturn(Optional.of(room))
        `when`(userRepo.findByUid("U1")).thenReturn(Optional.of(validUser))
        `when`(userRepo.findByUid("MISSING")).thenReturn(Optional.empty())

        service.recordGameEnd(2L, setOf("U1", "MISSING"), System.currentTimeMillis(), "U1")

        // Should only save history for U1
        verify(playHistRepo, times(1)).save(any())
    }

    @Test
    fun `setRoomStatus sets new status`() {
        val room = GameRoom(id = 1L, status = RoomStatus.WAITING)
        `when`(roomRepo.findById(1L)).thenReturn(Optional.of(room))

        service.setRoomStatus(1L, RoomStatus.PLAYING)

        verify(roomRepo).save(argThat { it.status == RoomStatus.PLAYING })
    }
}
