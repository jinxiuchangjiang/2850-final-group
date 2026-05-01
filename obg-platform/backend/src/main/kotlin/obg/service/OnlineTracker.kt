package com.obg.service

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class OnlineTracker {

    enum class Status { OFFLINE, IDLE, IN_GAME }

    private data class Entry(val lastSeen: Long, val status: Status)

    private val store = ConcurrentHashMap<String, Entry>()
    private val TIMEOUT_MS = 90_000L

    /** Called by heartbeat endpoint. status = IDLE or IN_GAME */
    fun seen(uid: String, status: Status = Status.IDLE) {
        store[uid] = Entry(System.currentTimeMillis(), status)
    }

    /** Called by relay WS on connect */
    fun ping(uid: String) {
        val current = store[uid]?.status ?: Status.IDLE
        store[uid] = Entry(System.currentTimeMillis(), current)
    }

    /** Called by relay WS on disconnect — no-op, let timeout handle it */
    fun disconnect(uid: String) {}

    fun getStatus(uid: String): Status {
        val entry = store[uid] ?: return Status.OFFLINE
        if (System.currentTimeMillis() - entry.lastSeen > TIMEOUT_MS) return Status.OFFLINE
        return entry.status
    }

    fun isOnline(uid: String) = getStatus(uid) != Status.OFFLINE
}