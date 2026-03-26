package me.topilov.springplayground.telegram

import me.topilov.springplayground.telegram.application.TelegramPendingAuth
import me.topilov.springplayground.telegram.application.TelegramPendingAuthStore
import java.util.concurrent.ConcurrentHashMap

class InMemoryTelegramPendingAuthStore : TelegramPendingAuthStore {
    private val values = ConcurrentHashMap<String, TelegramPendingAuth>()

    fun clear() {
        values.clear()
    }

    override fun save(pendingAuth: TelegramPendingAuth) {
        values[pendingAuth.pendingAuthId] = pendingAuth
    }

    override fun findById(pendingAuthId: String): TelegramPendingAuth? = values[pendingAuthId]

    override fun delete(pendingAuthId: String) {
        values.remove(pendingAuthId)
    }
}
