package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.player.VybePlaybackService
import com.example.ui.home.HomeViewModel
import com.example.ui.library.LibraryViewModel
import com.example.ui.navigation.MainAppScaffold
import com.example.ui.search.SearchViewModel
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.VybeBlack
import com.example.ui.theme.VybeOrange
import com.example.ui.theme.VybeTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val openPlayerTrigger = MutableStateFlow(false)

    private val homeViewModel: HomeViewModel by lazy {
        val app = VybeApp.instance
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(
                    repository = app.repository,
                    recommendationEngine = app.recommendationEngine,
                    playlistFeedEngine = app.playlistFeedEngine,
                    playerManager = app.playerManager
                ) as T
            }
        })[HomeViewModel::class.java]
    }

    private val searchViewModel: SearchViewModel by lazy {
        val app = VybeApp.instance
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SearchViewModel(
                    repository = app.repository
                ) as T
            }
        })[SearchViewModel::class.java]
    }

    private val libraryViewModel: LibraryViewModel by lazy {
        val app = VybeApp.instance
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LibraryViewModel(
                    repository = app.repository,
                    playerManager = app.playerManager
                ) as T
            }
        })[LibraryViewModel::class.java]
    }

    private val settingsViewModel: SettingsViewModel by lazy {
        val app = VybeApp.instance
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(
                    driveManager = app.driveManager,
                    songDao = app.database.songDao(),
                    appContext = applicationContext,
                    playerManager = app.playerManager
                ) as T
            }
        })[SettingsViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        val app = VybeApp.instance

        setContent {
            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
            val accentColorHex by settingsViewModel.accentColor.collectAsStateWithLifecycle()
            val dynamicTheme by settingsViewModel.dynamicTheme.collectAsStateWithLifecycle()

            val isDark = when (themeMode) {
                "Light" -> false
                "Dark" -> true
                else -> isSystemInDarkTheme()
            }
            val accentColor = remember(accentColorHex) {
                try {
                    Color(android.graphics.Color.parseColor(accentColorHex))
                } catch (_: Exception) {
                    VybeOrange
                }
            }

            VybeTheme(
                darkTheme = isDark,
                accentColor = accentColor,
                dynamicColor = dynamicTheme
            ) {
                val shouldOpenPlayer by openPlayerTrigger.collectAsStateWithLifecycle()

                // Request notification permission on Android 13+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { _ -> }

                    LaunchedEffect(Unit) {
                        if (ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val updateState by app.updateManager.updateState.collectAsStateWithLifecycle()

                    when (val currentUpdateState = updateState) {
                        is com.example.data.update.UpdateState.MandatoryUpdateRequired -> {
                            com.example.ui.update.MandatoryUpdateScreen(
                                state = currentUpdateState,
                                onUpdateClick = {
                                    app.updateManager.openOfficialTelegram(this@MainActivity)
                                },
                                onCheckAgainClick = {
                                    app.updateManager.checkForUpdates(isResume = true)
                                }
                            )
                        }
                        else -> {
                            MainAppScaffold(
                                homeViewModel = homeViewModel,
                                searchViewModel = searchViewModel,
                                libraryViewModel = libraryViewModel,
                                settingsViewModel = settingsViewModel,
                                playerManager = app.playerManager,
                                initialOpenPlayer = shouldOpenPlayer,
                                onPlayerOpenedHandled = { openPlayerTrigger.value = false }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        VybeApp.instance.updateManager.checkForUpdates(isResume = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(VybePlaybackService.EXTRA_OPEN_PLAYER, false) == true) {
            openPlayerTrigger.value = true
        }
    }
}
