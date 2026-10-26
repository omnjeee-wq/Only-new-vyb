package com.example.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.Coil
import com.example.VybeApp
import com.example.data.drive.DriveAccountInfo
import com.example.data.drive.DriveBackupSummary
import com.example.data.drive.GoogleDriveManager
import com.example.data.drive.SyncStatus
import com.example.data.local.AppPreferenceEntity
import com.example.data.local.SongDao
import com.example.data.remote.RetrofitClient
import com.example.domain.repository.MusicRepository
import com.example.player.EqualizerManager
import com.example.player.PlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File

class SettingsViewModel(
    private val driveManager: GoogleDriveManager,
    private val songDao: SongDao,
    private val appContext: Context,
    private val playerManager: PlayerManager = VybeApp.instance.playerManager,
    private val repository: MusicRepository = VybeApp.instance.repository
) : ViewModel() {

    // -------------------------------------------------------------
    // 1. ACCOUNT SYNC
    // -------------------------------------------------------------
    val accountInfo: StateFlow<DriveAccountInfo?> = driveManager.accountInfo
    val syncStatus: StateFlow<SyncStatus> = driveManager.syncStatus
    val lastBackupSummary: StateFlow<DriveBackupSummary?> = driveManager.lastBackupSummary
    val isCheckingBackup: StateFlow<Boolean> = driveManager.isCheckingBackup
    val userRecoverableAuthIntent: StateFlow<Intent?> = driveManager.userRecoverableAuthIntent

    private val _autoBackup = MutableStateFlow(false)
    val autoBackup: StateFlow<Boolean> = _autoBackup.asStateFlow()

    // -------------------------------------------------------------
    // 2. APPEARANCE
    // -------------------------------------------------------------
    private val _themeMode = MutableStateFlow("Dark")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _accentColor = MutableStateFlow("#FF6436")
    val accentColor: StateFlow<String> = _accentColor.asStateFlow()

    private val _dynamicTheme = MutableStateFlow(false)
    val dynamicTheme: StateFlow<Boolean> = _dynamicTheme.asStateFlow()

    private val _useArtworkColors = MutableStateFlow(true)
    val useArtworkColors: StateFlow<Boolean> = _useArtworkColors.asStateFlow()

    // -------------------------------------------------------------
    // 3. AUDIO
    // -------------------------------------------------------------
    private val _streamingQuality = MutableStateFlow("High (320 kbps)")
    val streamingQuality: StateFlow<String> = _streamingQuality.asStateFlow()

    private val _equalizerEnabled = MutableStateFlow(false)
    val equalizerEnabled: StateFlow<Boolean> = _equalizerEnabled.asStateFlow()

    private val _equalizerPreset = MutableStateFlow("Normal")
    val equalizerPreset: StateFlow<String> = _equalizerPreset.asStateFlow()

    private val _equalizerBands = MutableStateFlow(listOf(0, 0, 0, 0, 0))
    val equalizerBands: StateFlow<List<Int>> = _equalizerBands.asStateFlow()

    // -------------------------------------------------------------
    // 4. PLAYBACK
    // -------------------------------------------------------------
    private val _autoplay = MutableStateFlow(true)
    val autoplay: StateFlow<Boolean> = _autoplay.asStateFlow()

    private val _gaplessPlayback = MutableStateFlow(true)
    val gaplessPlayback: StateFlow<Boolean> = _gaplessPlayback.asStateFlow()

    private val _crossfadeSeconds = MutableStateFlow(0) // 0 = Off
    val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds.asStateFlow()

    private val _repeatMode = MutableStateFlow("Off")
    val repeatMode: StateFlow<String> = _repeatMode.asStateFlow()

    private val _startPlaybackOnLaunch = MutableStateFlow(false)
    val startPlaybackOnLaunch: StateFlow<Boolean> = _startPlaybackOnLaunch.asStateFlow()

    private val _continuePlaybackOnOpen = MutableStateFlow(true)
    val continuePlaybackOnOpen: StateFlow<Boolean> = _continuePlaybackOnOpen.asStateFlow()

    // -------------------------------------------------------------
    // 5. LIBRARY & STORAGE
    // -------------------------------------------------------------
    private val _downloadQuality = MutableStateFlow("High (320 kbps)")
    val downloadQuality: StateFlow<String> = _downloadQuality.asStateFlow()

    private val _downloadWifiOnly = MutableStateFlow(false)
    val downloadWifiOnly: StateFlow<Boolean> = _downloadWifiOnly.asStateFlow()

    val downloadLocation: String = File(appContext.filesDir, "downloads").absolutePath

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    val downloadSizeBytes: StateFlow<Long> = repository.getTotalDownloadedSizeBytes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _searchHistoryCount = MutableStateFlow(0)
    val searchHistoryCount: StateFlow<Int> = _searchHistoryCount.asStateFlow()

    val playHistoryCount: StateFlow<Int> = songDao.getSongHistoryCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // -------------------------------------------------------------
    // 6. NOTIFICATIONS
    // -------------------------------------------------------------
    private val _playbackNotifications = MutableStateFlow(true)
    val playbackNotifications: StateFlow<Boolean> = _playbackNotifications.asStateFlow()

    private val _downloadNotifications = MutableStateFlow(true)
    val downloadNotifications: StateFlow<Boolean> = _downloadNotifications.asStateFlow()

    // -------------------------------------------------------------
    // 7. BACKEND SERVER
    // -------------------------------------------------------------
    private val _backendUrl = MutableStateFlow(RetrofitClient.DEFAULT_BASE_URL)
    val backendUrl: StateFlow<String> = _backendUrl.asStateFlow()

    companion object {
        val EQUALIZER_PRESETS = mapOf(
            "Normal" to listOf(0, 0, 0, 0, 0),
            "Classical" to listOf(5, 3, -2, 4, 4),
            "Dance" to listOf(6, 0, 2, 4, 1),
            "Flat" to listOf(0, 0, 0, 0, 0),
            "Folk" to listOf(3, 0, 0, 2, -1),
            "Heavy" to listOf(4, 1, 9, 3, 0),
            "Hip-Hop" to listOf(5, 3, 0, 1, 3),
            "Jazz" to listOf(4, 2, -2, 2, 5),
            "Pop" to listOf(-1, 2, 5, 1, -2),
            "Rock" to listOf(5, 3, -1, 3, 5),
            "Vocal" to listOf(-2, -1, 3, 4, 1)
        )
    }

    init {
        loadPreferences()
        calculateCacheSize()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // 1. Account Sync
                _autoBackup.value = songDao.getPreference("pref_auto_backup")?.toBooleanStrictOrNull() ?: false

                // 2. Appearance
                _themeMode.value = songDao.getPreference("pref_theme_mode") ?: "Dark"
                _accentColor.value = songDao.getPreference("pref_accent_color") ?: "#FF6436"
                _dynamicTheme.value = songDao.getPreference("pref_dynamic_theme")?.toBooleanStrictOrNull() ?: false
                _useArtworkColors.value = songDao.getPreference("pref_use_artwork_colors")?.toBooleanStrictOrNull() ?: true

                // 3. Audio
                val quality = songDao.getPreference("pref_streaming_quality") ?: "High (320 kbps)"
                _streamingQuality.value = quality
                val eqEnabled = songDao.getPreference("pref_equalizer_enabled")?.toBooleanStrictOrNull() ?: false
                _equalizerEnabled.value = eqEnabled
                EqualizerManager.setEnabled(eqEnabled)

                _equalizerPreset.value = songDao.getPreference("pref_equalizer_preset") ?: "Normal"

                val b0 = songDao.getPreference("pref_eq_band_0")?.toIntOrNull() ?: 0
                val b1 = songDao.getPreference("pref_eq_band_1")?.toIntOrNull() ?: 0
                val b2 = songDao.getPreference("pref_eq_band_2")?.toIntOrNull() ?: 0
                val b3 = songDao.getPreference("pref_eq_band_3")?.toIntOrNull() ?: 0
                val b4 = songDao.getPreference("pref_eq_band_4")?.toIntOrNull() ?: 0
                val loadedBands = listOf(b0, b1, b2, b3, b4)
                _equalizerBands.value = loadedBands
                EqualizerManager.setAllBands(loadedBands)

                // 4. Playback
                _autoplay.value = songDao.getPreference("pref_autoplay")?.toBooleanStrictOrNull() ?: true
                _gaplessPlayback.value = songDao.getPreference("pref_gapless_playback")?.toBooleanStrictOrNull() ?: true
                _crossfadeSeconds.value = songDao.getPreference("pref_crossfade_seconds")?.toIntOrNull() ?: 0
                _repeatMode.value = songDao.getPreference("pref_repeat_mode") ?: "Off"
                _startPlaybackOnLaunch.value = songDao.getPreference("pref_start_playback_launch")?.toBooleanStrictOrNull() ?: false
                _continuePlaybackOnOpen.value = songDao.getPreference("pref_continue_playback_open")?.toBooleanStrictOrNull() ?: true

                // 5. Library & Storage
                _downloadQuality.value = songDao.getPreference("pref_download_quality") ?: "High (320 kbps)"
                _downloadWifiOnly.value = songDao.getPreference("pref_download_wifi_only")?.toBooleanStrictOrNull() ?: false

                // 6. Notifications
                _playbackNotifications.value = songDao.getPreference("pref_playback_notifications")?.toBooleanStrictOrNull() ?: true
                _downloadNotifications.value = songDao.getPreference("pref_download_notifications")?.toBooleanStrictOrNull() ?: true

                // 7. Backend URL
                val savedUrl = songDao.getPreference("pref_backend_url")
                if (!savedUrl.isNullOrBlank()) {
                    _backendUrl.value = savedUrl
                    RetrofitClient.setBaseUrl(savedUrl)
                }

                // Counts
                _searchHistoryCount.value = songDao.getAllSearchHistorySync().size
            }
        }
    }

    private fun persistPreference(key: String, value: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                songDao.setPreference(AppPreferenceEntity(key, value))
            }
        }
    }

    // --- Account Sync actions ---
    fun setAutoBackup(enabled: Boolean) {
        _autoBackup.value = enabled
        persistPreference("pref_auto_backup", enabled.toString())
        if (enabled) {
            driveManager.triggerAutoBackup()
        }
    }

    fun getSignInIntent(): Intent = driveManager.getSignInIntent()
    fun handleSignInResult(data: Intent?) = driveManager.handleSignInResult(data)
    fun connectLocalSyncStorage() = driveManager.connectLocalSyncStorage()
    fun onAuthRecoveryResult(granted: Boolean) = driveManager.onAuthRecoveryResult(granted)
    fun disconnectGoogleDrive() = driveManager.disconnect()
    fun backupNow() = driveManager.backupNow()
    fun restoreData() = driveManager.restoreData()
    fun clearStatus() = driveManager.clearStatus()
    fun refreshBackupMetadata() = driveManager.refreshBackupMetadata()

    fun exportBackupToUri(uri: Uri) {
        try {
            appContext.contentResolver.openOutputStream(uri)?.let { stream ->
                driveManager.exportBackup(stream)
            }
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "Failed to export backup to URI", e)
        }
    }

    fun importBackupFromUri(uri: Uri) {
        try {
            appContext.contentResolver.openInputStream(uri)?.let { stream ->
                driveManager.importBackup(stream)
            }
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "Failed to import backup from URI", e)
        }
    }

    // --- Appearance actions ---
    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        persistPreference("pref_theme_mode", mode)
    }

    fun setAccentColor(hexColor: String) {
        _accentColor.value = hexColor
        persistPreference("pref_accent_color", hexColor)
    }

    fun setDynamicTheme(enabled: Boolean) {
        _dynamicTheme.value = enabled
        persistPreference("pref_dynamic_theme", enabled.toString())
    }

    fun setUseArtworkColors(enabled: Boolean) {
        _useArtworkColors.value = enabled
        persistPreference("pref_use_artwork_colors", enabled.toString())
    }

    // --- Audio actions ---
    fun setStreamingQuality(quality: String) {
        _streamingQuality.value = quality
        persistPreference("pref_streaming_quality", quality)
        // Rebuild the active Media3 queue so the new bitrate takes effect now,
        // not only on the next song/app restart.
        playerManager.setStreamingQuality(quality)
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        _equalizerEnabled.value = enabled
        persistPreference("pref_equalizer_enabled", enabled.toString())
        EqualizerManager.setEnabled(enabled)
    }

    fun setEqualizerPreset(preset: String) {
        _equalizerPreset.value = preset
        persistPreference("pref_equalizer_preset", preset)
        val presetBands = EQUALIZER_PRESETS[preset] ?: listOf(0, 0, 0, 0, 0)
        _equalizerBands.value = presetBands
        EqualizerManager.setAllBands(presetBands)
        saveEqualizerBands(presetBands)
    }

    fun setEqualizerBand(bandIndex: Int, value: Int) {
        val current = _equalizerBands.value.toMutableList()
        if (bandIndex in current.indices) {
            current[bandIndex] = value.coerceIn(-10, 10)
            _equalizerBands.value = current
            _equalizerPreset.value = "Custom"
            persistPreference("pref_equalizer_preset", "Custom")
            EqualizerManager.setBandGain(bandIndex, value)
            saveEqualizerBands(current)
        }
    }

    fun resetEqualizer() {
        val flat = listOf(0, 0, 0, 0, 0)
        _equalizerBands.value = flat
        _equalizerPreset.value = "Flat"
        persistPreference("pref_equalizer_preset", "Flat")
        EqualizerManager.setAllBands(flat)
        saveEqualizerBands(flat)
    }

    private fun saveEqualizerBands(bands: List<Int>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                bands.forEachIndexed { idx, v ->
                    songDao.setPreference(AppPreferenceEntity("pref_eq_band_$idx", v.toString()))
                }
            }
        }
    }

    // --- Playback actions ---
    fun setAutoplay(enabled: Boolean) {
        _autoplay.value = enabled
        persistPreference("pref_autoplay", enabled.toString())
    }

    fun setGaplessPlayback(enabled: Boolean) {
        _gaplessPlayback.value = enabled
        persistPreference("pref_gapless_playback", enabled.toString())
    }

    fun setCrossfadeSeconds(seconds: Int) {
        _crossfadeSeconds.value = seconds
        persistPreference("pref_crossfade_seconds", seconds.toString())
    }

    fun setRepeatMode(mode: String) {
        _repeatMode.value = mode
        persistPreference("pref_repeat_mode", mode)
    }

    fun setStartPlaybackOnLaunch(enabled: Boolean) {
        _startPlaybackOnLaunch.value = enabled
        persistPreference("pref_start_playback_launch", enabled.toString())
    }

    fun setContinuePlaybackOnOpen(enabled: Boolean) {
        _continuePlaybackOnOpen.value = enabled
        persistPreference("pref_continue_playback_open", enabled.toString())
    }

    // --- Library & Storage actions ---
    fun setDownloadQuality(quality: String) {
        _downloadQuality.value = quality
        persistPreference("pref_download_quality", quality)
    }

    fun setDownloadWifiOnly(enabled: Boolean) {
        _downloadWifiOnly.value = enabled
        persistPreference("pref_download_wifi_only", enabled.toString())
    }

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    appContext.cacheDir.deleteRecursively()
                    // Clear Coil image cache
                    val imageLoader = Coil.imageLoader(appContext)
                    imageLoader.diskCache?.clear()
                    imageLoader.memoryCache?.clear()
                } catch (_: Exception) {}
            }
            calculateCacheSize()
        }
    }

    fun clearDownloadedSongs() {
        viewModelScope.launch {
            repository.clearAllDownloads()
        }
    }

    fun clearPlayHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                songDao.clearAllSongHistory()
            }
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                songDao.clearAllSearchHistory()
            }
            _searchHistoryCount.value = 0
        }
    }

    private fun calculateCacheSize() {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                getFolderSize(appContext.cacheDir)
            }
            _cacheSizeBytes.value = bytes
        }
    }

    private fun getFolderSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var result = 0L
        val fileList = dir.listFiles() ?: return 0L
        for (file in fileList) {
            result += if (file.isDirectory) getFolderSize(file) else file.length()
        }
        return result
    }

    // --- Notifications actions ---
    fun setPlaybackNotifications(enabled: Boolean) {
        _playbackNotifications.value = enabled
        persistPreference("pref_playback_notifications", enabled.toString())
    }

    fun setDownloadNotifications(enabled: Boolean) {
        _downloadNotifications.value = enabled
        persistPreference("pref_download_notifications", enabled.toString())
    }

    // --- Backend Server URL actions ---
    fun setBackendUrl(url: String): Boolean {
        val trimmed = url.trim()
        val parsed = trimmed.toHttpUrlOrNull()
        if (parsed == null || (!trimmed.startsWith("http://") && !trimmed.startsWith("https://"))) {
            return false
        }
        val formatted = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        _backendUrl.value = formatted
        persistPreference("pref_backend_url", formatted)
        RetrofitClient.setBaseUrl(formatted)
        return true
    }

    fun resetBackendUrl() {
        _backendUrl.value = RetrofitClient.DEFAULT_BASE_URL
        persistPreference("pref_backend_url", RetrofitClient.DEFAULT_BASE_URL)
        RetrofitClient.setBaseUrl(RetrofitClient.DEFAULT_BASE_URL)
    }
}
