package com.example.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import com.example.data.remote.RetrofitClient
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

sealed interface UpdateState {
    data object None : UpdateState

    data class SilentUpdated(
        val config: AppUpdateConfig
    ) : UpdateState

    data class MandatoryUpdateRequired(
        val installedVersion: String,
        val minimumSupportedVersion: String,
        val latestReleaseVersion: String,
        val releaseNotes: String?,
        val updateUrl: String = UpdateManager.OFFICIAL_TELEGRAM_URL,
        val isOfflineCached: Boolean = false
    ) : UpdateState
}

class UpdateManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val defaultRemoteConfigUrl: String = DEFAULT_CONFIG_URL
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val configAdapter by lazy {
        moshi.adapter(AppUpdateConfig::class.java)
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.None)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _announcementBanner = MutableStateFlow<AnnouncementBannerConfig?>(null)
    val announcementBanner: StateFlow<AnnouncementBannerConfig?> = _announcementBanner.asStateFlow()

    private val _featureFlags = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val featureFlags: StateFlow<Map<String, Boolean>> = _featureFlags.asStateFlow()

    val installedVersion: String
        get() = BuildConfig.VERSION_NAME.ifBlank { "1.0.0" }

    init {
        // Load cached config immediately on startup without blocking main thread
        loadCachedConfig()
        // Asynchronously check for remote updates
        checkForUpdates(isResume = false)
    }

    private fun loadCachedConfig() {
        try {
            val cachedJson = prefs.getString(KEY_CACHED_CONFIG_JSON, null) ?: return
            val config = configAdapter.fromJson(cachedJson) ?: return
            evaluateConfig(config, isOffline = true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load cached update config: ${e.message}")
        }
    }

    fun checkForUpdates(isResume: Boolean = false) {
        scope.launch {
            // If resuming from background, instantly verify if installed version was updated
            if (isResume) {
                val current = _updateState.value
                if (current is UpdateState.MandatoryUpdateRequired) {
                    if (SemVer.isAtLeast(installedVersion, current.minimumSupportedVersion)) {
                        _updateState.value = UpdateState.None
                    }
                }
            }

            try {
                val targetUrl = getActiveConfigUrl()
                val request = Request.Builder()
                    .url(targetUrl)
                    .header("Accept", "application/json")
                    .header("User-Agent", "VYBE-Android/$installedVersion")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    handleFetchFailure("HTTP ${response.code}")
                    return@launch
                }

                val bodyString = response.body?.string()
                if (bodyString.isNullOrBlank()) {
                    handleFetchFailure("Empty response body")
                    return@launch
                }

                val parsed = configAdapter.fromJson(bodyString)
                if (parsed == null) {
                    handleFetchFailure("Failed to parse config schema")
                    return@launch
                }

                // Cache successful valid config
                prefs.edit()
                    .putString(KEY_CACHED_CONFIG_JSON, bodyString)
                    .putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis())
                    .apply()

                evaluateConfig(parsed, isOffline = false)
            } catch (e: Exception) {
                handleFetchFailure(e.message ?: "Network error")
            }
        }
    }

    private fun evaluateConfig(config: AppUpdateConfig, isOffline: Boolean) {
        if (!config.enabled) {
            _updateState.value = UpdateState.None
            return
        }

        // Apply silent configuration payload if present
        config.remoteConfig?.let { payload ->
            applyRemoteConfig(payload)
        }

        when (config.updateMode) {
            AppUpdateConfig.MODE_MANDATORY -> {
                val isOutdated = SemVer.isLessThan(installedVersion, config.minimumSupportedVersion)
                if (isOutdated) {
                    _updateState.value = UpdateState.MandatoryUpdateRequired(
                        installedVersion = installedVersion,
                        minimumSupportedVersion = config.minimumSupportedVersion,
                        latestReleaseVersion = config.latestReleaseVersion,
                        releaseNotes = config.releaseNotes,
                        updateUrl = OFFICIAL_TELEGRAM_URL,
                        isOfflineCached = isOffline
                    )
                } else {
                    _updateState.value = UpdateState.None
                }
            }
            AppUpdateConfig.MODE_SILENT_CONFIG -> {
                _updateState.value = UpdateState.SilentUpdated(config)
            }
            else -> {
                _updateState.value = UpdateState.None
            }
        }
    }

    private fun applyRemoteConfig(payload: RemoteConfigPayload) {
        // 1. Safe dynamic API base URL update (if HTTPS and valid)
        payload.customApiBaseUrl?.let { customUrl ->
            if (customUrl.startsWith("https://", ignoreCase = true)) {
                RetrofitClient.setBaseUrl(customUrl)
            }
        }

        // 2. Announcement banner
        val banner = payload.announcementBanner
        if (banner != null && banner.enabled && banner.message.isNotBlank()) {
            val dismissedId = prefs.getString(KEY_DISMISSED_BANNER_ID, null)
            if (dismissedId != banner.id) {
                _announcementBanner.value = banner
            }
        } else {
            _announcementBanner.value = null
        }

        // 3. Feature flags
        payload.featureFlags?.let { flags ->
            _featureFlags.value = flags
        }
    }

    private fun handleFetchFailure(reason: String) {
        Log.w(TAG, "Update check failed: $reason")
        // Recovery policy:
        // Do NOT lock the user out if offline/network fails, unless a pre-cached validated
        // config already required a mandatory update for this native version.
        val current = _updateState.value
        if (current !is UpdateState.MandatoryUpdateRequired) {
            // Keep safe access
            _updateState.value = UpdateState.None
        }
    }

    fun dismissAnnouncement() {
        val current = _announcementBanner.value ?: return
        prefs.edit().putString(KEY_DISMISSED_BANNER_ID, current.id).apply()
        _announcementBanner.value = null
    }

    fun openOfficialTelegram(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(OFFICIAL_TELEGRAM_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch telegram URL: ${e.message}")
        }
    }

    private fun getActiveConfigUrl(): String {
        val cachedJson = prefs.getString(KEY_CACHED_CONFIG_JSON, null)
        if (!cachedJson.isNullOrBlank()) {
            try {
                val cached = configAdapter.fromJson(cachedJson)
                if (!cached?.configUrl.isNullOrBlank()) {
                    return cached?.configUrl!!
                }
            } catch (_: Exception) {}
        }
        return defaultRemoteConfigUrl
    }

    companion object {
        private const val TAG = "UpdateManager"
        private const val PREFS_NAME = "vybe_app_update_prefs"
        private const val KEY_CACHED_CONFIG_JSON = "cached_config_json"
        private const val KEY_LAST_CHECK_TIME = "last_check_timestamp"
        private const val KEY_DISMISSED_BANNER_ID = "dismissed_banner_id"

        const val OFFICIAL_TELEGRAM_URL = "https://t.me/VyomaOfficial"
        const val DEFAULT_CONFIG_URL =
            "https://raw.githubusercontent.com/omnjeee-wq/Only-new-vyb/main/config/app-update.json"
    }
}
