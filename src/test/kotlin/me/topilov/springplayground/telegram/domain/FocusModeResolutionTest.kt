package me.topilov.springplayground.telegram.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class FocusModeResolutionTest {
    private val resolver = TelegramFocusPriorityResolver()
    private val mappingResolver = TelegramEmojiMappingResolver(
        defaults = mapOf(
            TelegramFocusMode.PERSONAL to "1001",
            TelegramFocusMode.AIRPLANE to "1002",
            TelegramFocusMode.DO_NOT_DISTURB to "1003",
            TelegramFocusMode.REDUCE_INTERRUPTIONS to "1004",
            TelegramFocusMode.SLEEP to "1005",
        ),
    )

    @Test
    fun `higher priority focus mode wins over lower priority mode`() {
        val effective = resolver.resolveEffectiveMode(
            listOf(
                TelegramFocusState(
                    userId = 1L,
                    focusMode = TelegramFocusMode.PERSONAL,
                    active = true,
                    lastActivatedAt = Instant.parse("2026-03-26T10:00:00Z"),
                ),
                TelegramFocusState(
                    userId = 1L,
                    focusMode = TelegramFocusMode.SLEEP,
                    active = true,
                    lastActivatedAt = Instant.parse("2026-03-26T09:00:00Z"),
                ),
            ),
        )

        assertThat(effective?.focusMode).isEqualTo(TelegramFocusMode.SLEEP)
    }

    @Test
    fun `no focus falls back to explicit default status`() {
        val resolved = mappingResolver.resolveEmojiStatusDocumentId(
            effectiveMode = null,
            userMappings = emptyMap(),
            defaultNoFocusEmojiStatusDocumentId = "7000",
        )

        assertThat(resolved).isEqualTo("7000")
    }

    @Test
    fun `missing user override falls back to configured default mapping`() {
        val resolved = mappingResolver.resolveEmojiStatusDocumentId(
            effectiveMode = TelegramFocusMode.REDUCE_INTERRUPTIONS,
            userMappings = mapOf(TelegramFocusMode.SLEEP to "9999"),
            defaultNoFocusEmojiStatusDocumentId = null,
        )

        assertThat(resolved).isEqualTo("1004")
    }
}
