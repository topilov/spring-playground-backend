# Telegram User-Defined Modes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace fixed Telegram focus-mode enums with user-defined mode resources, keep a default no-mode emoji status, and make automation updates activate at most one user-owned mode at a time.

**Architecture:** Refactor the telegram domain from enum-based focus mappings/states into a user-owned `telegram_mode` resource plus a single nullable `active_mode_id` on `telegram_account_connection`. Expose CRUD endpoints for modes, simplify `/focus-settings` to default emoji only, and update `/focus-updates` to resolve plain string mode keys against the authenticated user's stored modes.

**Tech Stack:** Kotlin, Spring Boot, Spring MVC, Spring Data JPA, Flyway, MockMvc, JUnit 5, AssertJ, Mockito, OpenAPI YAML

---

### Task 1: Lock The Breaking API Contract With Failing Tests

**Files:**
- Modify: `src/test/kotlin/me/topilov/springplayground/TelegramIntegrationTest.kt`
- Create: `src/test/kotlin/me/topilov/springplayground/telegram/application/TelegramModeApplicationServiceTest.kt`
- Modify: `src/test/kotlin/me/topilov/springplayground/telegram/application/TelegramFocusAutomationApplicationServiceTest.kt`

- [ ] **Step 1: Write failing integration tests for the new settings and mode endpoints**

```kotlin
@Test
fun `user can create list update and delete telegram modes`() {
    val session = loginSession("demo", "demo-password")

    mockMvc.perform(
        post("/api/profile/me/telegram/modes")
            .session(session)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"mode":"work","emojiStatusDocumentId":"1001"}"""),
    ).andExpect(status().isCreated)

    mockMvc.perform(get("/api/profile/me/telegram/modes").session(session))
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.modes[0].mode").value("work"))
}
```

- [ ] **Step 2: Add failing integration coverage for the new settings snapshot**

```kotlin
mockMvc.perform(get("/api/profile/me/telegram").session(session))
    .andExpect(jsonPath("$.activeFocusMode").doesNotExist())
    .andExpect(jsonPath("$.modes").isArray)
    .andExpect(jsonPath("$.resolvedEmojiMappings").doesNotExist())
    .andExpect(jsonPath("$.activeFocusModes").doesNotExist())
```

- [ ] **Step 3: Add failing integration coverage for automation updates with user-defined string modes**

```kotlin
mockMvc.perform(
    post("/api/telegram/focus-updates")
        .header("Authorization", "Bearer $token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"mode":"work","active":true}"""),
)
    .andExpect(status().isOk)
    .andExpect(jsonPath("$.activeFocusMode").value("work"))
    .andExpect(jsonPath("$.appliedEmojiStatusDocumentId").value("1001"))
```

- [ ] **Step 4: Write failing unit tests for mode CRUD application logic**

```kotlin
@Test
fun `create mode rejects duplicate mode key for same user`() {
    `when`(modeRepository.findByUserIdAndModeKey(1L, "work")).thenReturn(existingMode)

    assertThatThrownBy { service.createMode(1L, "work", "1001") }
        .isInstanceOf(TelegramModeAlreadyExistsException::class.java)
}
```

- [ ] **Step 5: Run the targeted tests to verify they fail for the expected reasons**

Run: `./gradlew test --tests me.topilov.springplayground.TelegramIntegrationTest --tests me.topilov.springplayground.telegram.application.TelegramModeApplicationServiceTest --tests me.topilov.springplayground.telegram.application.TelegramFocusAutomationApplicationServiceTest`

Expected:
- compile or assertion failures referencing missing mode endpoints, missing `activeFocusMode`, or enum-based request/response types

- [ ] **Step 6: Commit the red tests**

```bash
git add src/test/kotlin/me/topilov/springplayground/TelegramIntegrationTest.kt src/test/kotlin/me/topilov/springplayground/telegram/application/TelegramModeApplicationServiceTest.kt src/test/kotlin/me/topilov/springplayground/telegram/application/TelegramFocusAutomationApplicationServiceTest.kt
git commit -m "test: cover telegram user-defined modes contract"
```

### Task 2: Introduce The New Persistence Model And Migrate Legacy Data

**Files:**
- Create: `src/main/resources/db/migration/V12__migrate_telegram_focus_modes_to_user_defined_modes.sql`
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/domain/TelegramMode.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/infrastructure/repository/TelegramModeRepository.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/telegram/domain/TelegramAccountConnection.kt`
- Delete: `src/main/kotlin/me/topilov/springplayground/telegram/domain/TelegramFocusMapping.kt`
- Delete: `src/main/kotlin/me/topilov/springplayground/telegram/domain/TelegramFocusState.kt`
- Delete: `src/main/kotlin/me/topilov/springplayground/telegram/infrastructure/repository/TelegramFocusMappingRepository.kt`
- Delete: `src/main/kotlin/me/topilov/springplayground/telegram/infrastructure/repository/TelegramFocusStateRepository.kt`

- [ ] **Step 1: Write the Flyway migration for the new `telegram_mode` table and `active_mode_id`**

```sql
CREATE TABLE telegram_mode (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES auth_user (id),
    mode_key VARCHAR(64) NOT NULL,
    emoji_status_document_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT telegram_mode_user_mode_unique UNIQUE (user_id, mode_key)
);

ALTER TABLE telegram_account_connection
    ADD COLUMN active_mode_id BIGINT REFERENCES telegram_mode (id);
```

- [ ] **Step 2: Add data-copy SQL from legacy mapping/state tables into `telegram_mode` and `active_mode_id`**

```sql
INSERT INTO telegram_mode (user_id, mode_key, emoji_status_document_id, created_at, updated_at)
SELECT user_id, focus_mode, emoji_status_document_id, created_at, updated_at
FROM telegram_focus_mapping;
```

- [ ] **Step 3: Implement the new JPA entity and repository**

```kotlin
@Entity
@Table(name = "telegram_mode")
class TelegramMode(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "mode_key", nullable = false, length = 64)
    var modeKey: String,
    @Column(name = "emoji_status_document_id", nullable = false, length = 64)
    var emojiStatusDocumentId: String,
)
```

- [ ] **Step 4: Replace the legacy active state with a nullable active mode reference on `TelegramAccountConnection`**

```kotlin
@Column(name = "active_mode_id")
var activeModeId: Long? = null
```

- [ ] **Step 5: Delete legacy focus state/mapping entity and repository files after the new model compiles**

- [ ] **Step 6: Run the targeted tests to verify persistence wiring now passes compilation and remaining tests still fail higher up**

Run: `./gradlew test --tests me.topilov.springplayground.telegram.application.TelegramModeApplicationServiceTest`

Expected:
- tests move past missing persistence classes and now fail on missing service/controller behavior

- [ ] **Step 7: Commit the persistence refactor**

```bash
git add src/main/resources/db/migration/V12__migrate_telegram_focus_modes_to_user_defined_modes.sql src/main/kotlin/me/topilov/springplayground/telegram/domain/TelegramMode.kt src/main/kotlin/me/topilov/springplayground/telegram/infrastructure/repository/TelegramModeRepository.kt src/main/kotlin/me/topilov/springplayground/telegram/domain/TelegramAccountConnection.kt
git add -u src/main/kotlin/me/topilov/springplayground/telegram/domain src/main/kotlin/me/topilov/springplayground/telegram/infrastructure/repository
git commit -m "refactor: introduce telegram mode persistence model"
```

### Task 3: Implement Mode CRUD Application And Web Flows

**Files:**
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/application/TelegramModeApplicationService.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/domain/exception/TelegramModeAlreadyExistsException.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/domain/exception/TelegramModeInvalidException.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/domain/exception/TelegramModeNotFoundException.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/web/dto/TelegramModeDtos.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/telegram/web/TelegramController.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/telegram/web/TelegramExceptionHandler.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/telegram/application/TelegramFocusSettingsService.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/telegram/application/TelegramQueryService.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/telegram/web/dto/TelegramSettingsResponse.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/telegram/web/dto/TelegramFocusDtos.kt`
- Modify: `src/test/kotlin/me/topilov/springplayground/telegram/application/TelegramModeApplicationServiceTest.kt`
- Modify: `src/test/kotlin/me/topilov/springplayground/TelegramIntegrationTest.kt`

- [ ] **Step 1: Implement failing unit tests for validation, duplicates, rename, and delete behavior**

```kotlin
@Test
fun `delete active mode clears account active mode id`() {
    val connection = TelegramAccountConnection(userId = 1L, activeModeId = 10L, connectionStatus = DISCONNECTED)
    val mode = TelegramMode(id = 10L, userId = 1L, modeKey = "work", emojiStatusDocumentId = "1001")

    service.deleteMode(1L, "work")

    assertThat(connection.activeModeId).isNull()
}
```

- [ ] **Step 2: Implement `TelegramModeApplicationService` with normalized string validation**

```kotlin
private fun normalizeModeKey(value: String): String =
    value.trim().takeIf { it.isNotBlank() && it.length <= 64 }
        ?: throw TelegramModeInvalidException("Telegram mode must be a non-blank string up to 64 characters.")
```

- [ ] **Step 3: Add profile endpoints for list/create/update/delete mode resources**

```kotlin
@PostMapping("/modes")
@ResponseStatus(HttpStatus.CREATED)
fun createMode(...) : TelegramModeResponse
```

- [ ] **Step 4: Simplify `/focus-settings` to update only `defaultEmojiStatusDocumentId`**

```kotlin
data class TelegramFocusSettingsRequest(
    val defaultEmojiStatusDocumentId: String? = null,
)
```

- [ ] **Step 5: Update the settings query response to expose `activeFocusMode` and `modes`**

```kotlin
data class TelegramSettingsResponse(
    val defaultEmojiStatusDocumentId: String? = null,
    val activeFocusMode: String? = null,
    val modes: List<TelegramModeResponse>,
)
```

- [ ] **Step 6: Add exception mappings for `TELEGRAM_MODE_NOT_FOUND`, `TELEGRAM_MODE_ALREADY_EXISTS`, and `TELEGRAM_MODE_INVALID`**

- [ ] **Step 7: Run the targeted tests to verify CRUD and settings snapshot behavior**

Run: `./gradlew test --tests me.topilov.springplayground.telegram.application.TelegramModeApplicationServiceTest --tests me.topilov.springplayground.TelegramIntegrationTest`

Expected:
- mode CRUD tests pass
- any remaining failures are limited to automation sync and leftover enum-based code

- [ ] **Step 8: Commit the mode CRUD/web layer**

```bash
git add src/main/kotlin/me/topilov/springplayground/telegram/application/TelegramModeApplicationService.kt src/main/kotlin/me/topilov/springplayground/telegram/domain/exception/TelegramModeAlreadyExistsException.kt src/main/kotlin/me/topilov/springplayground/telegram/domain/exception/TelegramModeInvalidException.kt src/main/kotlin/me/topilov/springplayground/telegram/domain/exception/TelegramModeNotFoundException.kt src/main/kotlin/me/topilov/springplayground/telegram/web/dto/TelegramModeDtos.kt src/main/kotlin/me/topilov/springplayground/telegram/web/TelegramController.kt src/main/kotlin/me/topilov/springplayground/telegram/web/TelegramExceptionHandler.kt src/main/kotlin/me/topilov/springplayground/telegram/application/TelegramFocusSettingsService.kt src/main/kotlin/me/topilov/springplayground/telegram/application/TelegramQueryService.kt src/main/kotlin/me/topilov/springplayground/telegram/web/dto/TelegramSettingsResponse.kt src/main/kotlin/me/topilov/springplayground/telegram/web/dto/TelegramFocusDtos.kt src/test/kotlin/me/topilov/springplayground/telegram/application/TelegramModeApplicationServiceTest.kt src/test/kotlin/me/topilov/springplayground/TelegramIntegrationTest.kt
git commit -m "feat: add telegram user-defined mode endpoints"
```

### Task 4: Refactor Automation Sync To Single Active String Modes

**Files:**
- Modify: `src/main/kotlin/me/topilov/springplayground/telegram/application/TelegramFocusAutomationApplicationService.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/telegram/web/TelegramAutomationController.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/telegram/web/dto/TelegramFocusDtos.kt`
- Modify: `src/test/kotlin/me/topilov/springplayground/telegram/application/TelegramFocusAutomationApplicationServiceTest.kt`
- Modify: `src/test/kotlin/me/topilov/springplayground/TelegramIntegrationTest.kt`
- Delete: `src/main/kotlin/me/topilov/springplayground/telegram/domain/TelegramFocusMode.kt`
- Delete: `src/main/kotlin/me/topilov/springplayground/telegram/domain/TelegramFocusPriorityResolver.kt`
- Delete: `src/main/kotlin/me/topilov/springplayground/telegram/domain/TelegramEmojiMappingResolver.kt`
- Delete: `src/main/kotlin/me/topilov/springplayground/telegram/domain/exception/TelegramInvalidFocusModeException.kt`
- Delete: `src/test/kotlin/me/topilov/springplayground/telegram/domain/FocusModeResolutionTest.kt`

- [ ] **Step 1: Replace enum-based request/response types with plain strings in the automation DTOs**

```kotlin
data class TelegramFocusUpdateRequest(
    @field:NotBlank
    val mode: String,
    val active: Boolean,
)
```

- [ ] **Step 2: Rewrite `TelegramFocusAutomationApplicationService` to resolve modes by `modeKey` and `activeModeId`**

```kotlin
if (active) {
    val mode = modeRepository.findByUserIdAndModeKey(userId, normalizedMode)
        ?: throw TelegramModeNotFoundException(normalizedMode)
    connection.activeModeId = mode.id
    emojiStatusDocumentId = mode.emojiStatusDocumentId
} else if (connection.activeModeId == matchingMode?.id) {
    connection.activeModeId = null
    emojiStatusDocumentId = connection.defaultEmojiStatusDocumentId
}
```

- [ ] **Step 3: Update automation responses to return `activeFocusMode` instead of `effectiveFocusMode`**

- [ ] **Step 4: Delete the old enum/priority/mapping helper classes and their tests**

- [ ] **Step 5: Run the targeted automation and integration tests**

Run: `./gradlew test --tests me.topilov.springplayground.telegram.application.TelegramFocusAutomationApplicationServiceTest --tests me.topilov.springplayground.TelegramIntegrationTest`

Expected:
- user-defined mode activation/deactivation flows pass
- no remaining failures reference `TelegramFocusMode`

- [ ] **Step 6: Commit the automation refactor**

```bash
git add src/main/kotlin/me/topilov/springplayground/telegram/application/TelegramFocusAutomationApplicationService.kt src/main/kotlin/me/topilov/springplayground/telegram/web/TelegramAutomationController.kt src/main/kotlin/me/topilov/springplayground/telegram/web/dto/TelegramFocusDtos.kt src/test/kotlin/me/topilov/springplayground/telegram/application/TelegramFocusAutomationApplicationServiceTest.kt src/test/kotlin/me/topilov/springplayground/TelegramIntegrationTest.kt
git add -u src/main/kotlin/me/topilov/springplayground/telegram/domain src/test/kotlin/me/topilov/springplayground/telegram/domain
git commit -m "refactor: sync telegram emoji status from user-defined modes"
```

### Task 5: Remove Legacy Config, Update Contracts, And Verify Broadly

**Files:**
- Modify: `src/main/kotlin/me/topilov/springplayground/telegram/infrastructure/config/TelegramProperties.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/telegram/infrastructure/config/TelegramConfiguration.kt`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/resources/application-local.yml`
- Modify: `src/main/resources/application-test.yml`
- Modify: `docs/contracts/telegram.md`
- Modify: `openapi/openapi.yaml`
- Modify: `README.md`

- [ ] **Step 1: Remove fixed-mode default configuration from application properties and bean wiring**

```kotlin
data class TelegramProperties(
    var enabled: Boolean = false,
    var sessionRoot: String = "telegram-sessions",
    var pendingAuthTtl: Duration = Duration.ofMinutes(10),
    var automationTokenBytes: Int = 32,
    var encryptionKeyBase64: String = "...",
    var apiId: Int = 0,
    var apiHash: String = "",
)
```

- [ ] **Step 2: Update contract docs with the new mode resource endpoints and response shapes**

```markdown
Change note for this task: `Breaking`.

## POST /api/profile/me/telegram/modes
Creates a user-defined Telegram mode resource.
```

- [ ] **Step 3: Update `openapi/openapi.yaml` to remove enum mode schemas and add the new mode resources**

- [ ] **Step 4: Refresh the README Telegram section so it describes user-defined modes instead of fixed mode mappings**

- [ ] **Step 5: Run the broad backend verification suite**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Run the final build verification**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit docs/config cleanup**

```bash
git add src/main/kotlin/me/topilov/springplayground/telegram/infrastructure/config/TelegramProperties.kt src/main/kotlin/me/topilov/springplayground/telegram/infrastructure/config/TelegramConfiguration.kt src/main/resources/application.yml src/main/resources/application-local.yml src/main/resources/application-test.yml docs/contracts/telegram.md openapi/openapi.yaml README.md
git commit -m "docs: publish telegram user-defined modes contract"
```
