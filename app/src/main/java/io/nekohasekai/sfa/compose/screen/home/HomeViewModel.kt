package io.nekohasekai.sfa.compose.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.utils.HTTPClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date

/**
 * Логика минимального экрана CottonVPN: сохранение «ключа» и подготовка remote-профиля.
 * Переиспользует те же механизмы, что и стандартный импорт sing-box
 * (ProfileImportHandler.importRemoteProfile + EditProfileViewModel.updateRemoteProfile).
 */
class HomeViewModel : ViewModel() {
    data class UiState(
        val hasKey: Boolean = false,
        val profileName: String? = null,
        val isSaving: Boolean = false,
        val errorMessage: String? = null,
        val savedOk: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Обновить флаг наличия активного профиля (есть ли сохранённый ключ). */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val id = Settings.selectedProfile
            val profile = if (id != -1L) ProfileManager.get(id) else null
            _uiState.update { it.copy(hasKey = profile != null, profileName = profile?.name) }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
    fun clearSavedOk() = _uiState.update { it.copy(savedOk = false) }

    /** Автоопределение: токен -> URL подписки; полная ссылка / sing-box:// -> как есть. */
    private fun normalizeUrl(input: String): String {
        val s = input.trim()
        return when {
            s.startsWith("http://") || s.startsWith("https://") -> s
            s.startsWith("sing-box://import-remote-profile") ->
                Libbox.parseRemoteProfileImportLink(s).url
            else -> SUBSCRIPTION_BASE + s
        }
    }

    fun saveKey(input: String) {
        if (input.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                val url = normalizeUrl(input)

                // Скачать и проверить конфиг до сохранения (битый ключ/сеть/конфиг -> ошибка).
                val content = HTTPClient().use { it.getString(url) }
                Libbox.checkConfig(content)

                val selectedId = Settings.selectedProfile
                val existing = if (selectedId != -1L) ProfileManager.get(selectedId) else null

                if (existing != null && existing.typed.type == TypedProfile.Type.Remote) {
                    // Обновляем существующий профиль вместо создания дубликата.
                    existing.typed.remoteURL = url
                    existing.typed.lastUpdated = Date()
                    File(existing.typed.path).writeText(content)
                    ProfileManager.update(existing)
                    _uiState.update {
                        it.copy(isSaving = false, hasKey = true, profileName = existing.name, savedOk = true)
                    }
                } else {
                    val typed = TypedProfile().apply {
                        type = TypedProfile.Type.Remote
                        remoteURL = url
                        autoUpdate = true
                        autoUpdateInterval = 60
                        lastUpdated = Date()
                    }
                    val profile = Profile(name = PROFILE_NAME, typed = typed).apply {
                        userOrder = ProfileManager.nextOrder()
                    }
                    val fileID = ProfileManager.nextFileID()
                    val dir = File(Application.application.filesDir, "configs").also { it.mkdirs() }
                    val file = File(dir, "$fileID.json")
                    file.writeText(content)
                    typed.path = file.path
                    ProfileManager.create(profile, andSelect = true)
                    _uiState.update {
                        it.copy(isSaving = false, hasKey = true, profileName = profile.name, savedOk = true)
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, errorMessage = e.message ?: "Error") }
            }
        }
    }

    companion object {
        private const val SUBSCRIPTION_BASE = "https://webhook.cottonvpn.com/singbox/"
        private const val PROFILE_NAME = "CottonVPN"
    }
}
