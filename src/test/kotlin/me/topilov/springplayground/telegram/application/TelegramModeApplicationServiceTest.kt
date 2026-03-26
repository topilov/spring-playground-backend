package me.topilov.springplayground.telegram.application

import me.topilov.springplayground.telegram.domain.TelegramAccountConnection
import me.topilov.springplayground.telegram.domain.TelegramConnectionStatus
import me.topilov.springplayground.telegram.domain.TelegramMode
import me.topilov.springplayground.telegram.domain.exception.TelegramModeAlreadyExistsException
import me.topilov.springplayground.telegram.infrastructure.repository.TelegramAccountConnectionRepository
import me.topilov.springplayground.telegram.infrastructure.repository.TelegramModeRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.Optional

class TelegramModeApplicationServiceTest {
    private val modeRepository = mock(TelegramModeRepository::class.java)
    private val accountConnectionRepository = mock(TelegramAccountConnectionRepository::class.java)

    private val service = TelegramModeApplicationService(
        modeRepository = modeRepository,
        accountConnectionRepository = accountConnectionRepository,
    )

    @Test
    fun `create mode rejects duplicate mode key for same user`() {
        val existingMode = TelegramMode(
            id = 10L,
            userId = 1L,
            modeKey = "work",
            emojiStatusDocumentId = "1001",
        )

        `when`(modeRepository.findByUserIdAndModeKey(1L, "work")).thenReturn(existingMode)

        assertThatThrownBy { service.createMode(1L, "work", "1001") }
            .isInstanceOf(TelegramModeAlreadyExistsException::class.java)
    }

    @Test
    fun `delete active mode clears account active mode id`() {
        val connection = TelegramAccountConnection(
            userId = 1L,
            connectionStatus = TelegramConnectionStatus.DISCONNECTED,
            activeModeId = 10L,
        )
        val mode = TelegramMode(
            id = 10L,
            userId = 1L,
            modeKey = "work",
            emojiStatusDocumentId = "1001",
        )

        `when`(modeRepository.findByUserIdAndModeKey(1L, "work")).thenReturn(mode)
        `when`(accountConnectionRepository.findByUserId(1L)).thenReturn(Optional.of(connection))

        service.deleteMode(1L, "work")

        assertThat(connection.activeModeId).isNull()
    }
}
