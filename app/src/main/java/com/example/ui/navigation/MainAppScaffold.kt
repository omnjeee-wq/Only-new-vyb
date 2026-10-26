package com.example.ui.navigation

import androidx.compose.material3.MaterialTheme
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.VybeApp
import com.example.player.PlayerManager
import com.example.ui.components.FloatingBottomBar
import com.example.ui.components.NavDestination
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.library.LibraryScreen
import com.example.ui.library.LibraryViewModel
import com.example.ui.placeholder.PlaceholderScreen
import androidx.compose.ui.platform.LocalContext
import com.example.ui.library.LibraryTab
import com.example.ui.offline.OfflineScreen
import com.example.ui.player.PlayerScreen
import com.example.ui.search.SearchScreen
import com.example.ui.search.SearchViewModel
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.util.NetworkMonitor
import kotlinx.coroutines.launch

@Composable
fun MainAppScaffold(
    homeViewModel: HomeViewModel,
    searchViewModel: SearchViewModel,
    libraryViewModel: LibraryViewModel,
    settingsViewModel: SettingsViewModel,
    playerManager: PlayerManager,
    modifier: Modifier = Modifier,
    initialOpenPlayer: Boolean = false,
    onPlayerOpenedHandled: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val networkMonitor = remember { NetworkMonitor(context) }
    val isOnline by networkMonitor.isOnline.collectAsStateWithLifecycle(initialValue = networkMonitor.isCurrentlyOnline())

    var currentDestination by remember { mutableStateOf(NavDestination.HOME) }
    var isPlayerExpanded by remember { mutableStateOf(initialOpenPlayer) }
    var previousDestinationBeforeOffline by remember { mutableStateOf<NavDestination?>(null) }
    var isViewingDownloadsOffline by remember { mutableStateOf(false) }

    val showOfflineScreen = !isOnline && !isViewingDownloadsOffline

    val playerState by playerManager.playerState.collectAsStateWithLifecycle()

    LaunchedEffect(initialOpenPlayer) {
        if (initialOpenPlayer) {
            isPlayerExpanded = true
            onPlayerOpenedHandled()
        }
    }

    // Automatically manage transition when connectivity changes
    LaunchedEffect(isOnline) {
        if (!isOnline) {
            if (previousDestinationBeforeOffline == null) {
                previousDestinationBeforeOffline = currentDestination
            }
        } else {
            val previous = previousDestinationBeforeOffline
            if (previous != null) {
                currentDestination = previous
                previousDestinationBeforeOffline = null
            }
            isViewingDownloadsOffline = false
        }
    }

    // Handle back button while viewing downloads offline
    BackHandler(enabled = !isOnline && isViewingDownloadsOffline && !isPlayerExpanded) {
        isViewingDownloadsOffline = false
    }

    // Handle system back press
    BackHandler(enabled = isPlayerExpanded || (currentDestination != NavDestination.HOME && !showOfflineScreen && !isViewingDownloadsOffline)) {
        if (isPlayerExpanded) {
            isPlayerExpanded = false
        } else {
            currentDestination = NavDestination.HOME
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Active Destination Content with cinematic smooth cross-fade animation
        AnimatedContent(
            targetState = currentDestination,
            transitionSpec = {
                fadeIn(
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                ) togetherWith fadeOut(
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                )
            },
            label = "screen_navigation_crossfade",
            modifier = Modifier.fillMaxSize()
        ) { destination ->
            when (destination) {
                NavDestination.HOME -> {
                    HomeScreen(
                        viewModel = homeViewModel,
                        playerManager = playerManager,
                        onOpenFullPlayer = { isPlayerExpanded = true }
                    )
                }
                NavDestination.SEARCH -> {
                    SearchScreen(
                        viewModel = searchViewModel,
                        playerManager = playerManager
                    )
                }
                NavDestination.LIBRARY -> {
                    LibraryScreen(
                        viewModel = libraryViewModel,
                        playerManager = playerManager
                    )
                }
                NavDestination.SETTINGS -> {
                    SettingsScreen(
                        viewModel = settingsViewModel
                    )
                }
            }
        }

        // Floating Bottom Navigation + Mini-Player
        AnimatedVisibility(
            visible = !isPlayerExpanded && !showOfflineScreen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            FloatingBottomBar(
                currentDestination = currentDestination,
                playerState = playerState,
                onNavigate = { destination ->
                    if (!isOnline && destination != NavDestination.LIBRARY) {
                        isViewingDownloadsOffline = false
                    } else {
                        currentDestination = destination
                    }
                },
                onOpenFullPlayer = { isPlayerExpanded = true },
                onPlayPause = { playerManager.togglePlayPause() },
                onNext = { playerManager.next() }
            )
        }

        // Full Screen Player Modal with Smooth Slide-up Animation
        AnimatedVisibility(
            visible = isPlayerExpanded,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(
                    durationMillis = 400,
                    easing = FastOutSlowInEasing
                )
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = 280,
                    easing = FastOutSlowInEasing
                )
            ),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(
                    durationMillis = 350,
                    easing = FastOutSlowInEasing
                )
            ) + fadeOut(
                animationSpec = tween(
                    durationMillis = 200,
                    easing = FastOutSlowInEasing
                )
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            PlayerScreen(
                playerManager = playerManager,
                onCollapse = { isPlayerExpanded = false },
                onToggleFavorite = { song ->
                    coroutineScope.launch {
                        VybeApp.instance.repository.toggleFavorite(song)
                    }
                }
            )
        }

        // Dedicated Full-Screen Offline Screen
        AnimatedVisibility(
            visible = showOfflineScreen && !isPlayerExpanded,
            enter = fadeIn(animationSpec = tween(durationMillis = 280)),
            exit = fadeOut(animationSpec = tween(durationMillis = 220)),
            modifier = Modifier.fillMaxSize()
        ) {
            OfflineScreen(
                onNavigateToDownloads = {
                    isViewingDownloadsOffline = true
                    currentDestination = NavDestination.LIBRARY
                    libraryViewModel.selectTab(LibraryTab.DOWNLOADS)
                }
            )
        }
    }
}
