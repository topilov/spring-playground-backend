package me.topilov.springplayground.telegram.domain

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import me.topilov.springplayground.telegram.domain.exception.TelegramInvalidFocusModeException

enum class TelegramFocusMode(
    @get:JsonValue val value: String,
) {
    PERSONAL("personal"),
    AIRPLANE("airplane"),
    DO_NOT_DISTURB("do_not_disturb"),
    REDUCE_INTERRUPTIONS("reduce_interruptions"),
    SLEEP("sleep"),
    ;

    override fun toString(): String = value

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromValue(value: String): TelegramFocusMode =
            entries.firstOrNull { it.value.equals(value.trim(), ignoreCase = true) }
                ?: throw TelegramInvalidFocusModeException(value)
    }
}
