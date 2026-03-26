package me.topilov.springplayground.telegram.domain

import org.springframework.stereotype.Component

@Component
class TelegramFocusPriorityResolver {
    fun resolveEffectiveMode(states: Collection<TelegramFocusState>): TelegramFocusState? =
        states
            .filter { it.active }
            .sortedWith(
                compareBy<TelegramFocusState> { priority(it.focusMode) }
                    .thenByDescending { it.lastActivatedAt },
            )
            .firstOrNull()

    private fun priority(mode: TelegramFocusMode): Int = when (mode) {
        TelegramFocusMode.SLEEP -> 0
        TelegramFocusMode.DO_NOT_DISTURB -> 1
        TelegramFocusMode.REDUCE_INTERRUPTIONS -> 2
        TelegramFocusMode.AIRPLANE -> 3
        TelegramFocusMode.PERSONAL -> 4
    }
}
