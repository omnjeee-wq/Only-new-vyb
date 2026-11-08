package com.example.ui.skeleton

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.Playlist
import com.example.domain.model.Song
import com.example.domain.repository.MusicRepository
import com.example.player.PlayerManager
import com.example.ui.library.LibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class SkeletonLoadingStateTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun user_playlist_detail_loading_state_transitions_properly() = runTest(testDispatcher) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val playlistSongsFlow = MutableStateFlow<List<Song>>(emptyList())

        val repoProxy = Proxy.newProxyInstance(
            MusicRepository::class.java.classLoader,
            arrayOf(MusicRepository::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getUserPlaylistSongs" -> playlistSongsFlow.asStateFlow()
                "getFavorites" -> MutableStateFlow<List<Song>>(emptyList())
                "getFavoriteAlbums" -> MutableStateFlow(emptyList<Any>())
                "getDownloadedSongs" -> MutableStateFlow<List<Song>>(emptyList())
                "getTotalDownloadedSizeBytes" -> MutableStateFlow(0L)
                "getUserPlaylists" -> MutableStateFlow<List<Playlist>>(emptyList())
                "getLikedPlaylists" -> MutableStateFlow<List<Playlist>>(emptyList())
                else -> null
            }
        } as MusicRepository

        val playerManager = PlayerManager.initialize(context, repoProxy)
        val viewModel = LibraryViewModel(repoProxy, playerManager)

        val testPlaylist = Playlist(id = "pl_test", title = "Test Vibe", isUserCreated = true)

        // Opening user playlist triggers isDetailLoading = true
        viewModel.openUserPlaylist(testPlaylist)
        assertEquals(testPlaylist, viewModel.uiState.value.activeUserPlaylist)

        // When songs flow emits data
        val songs = listOf(Song(id = "s1", title = "Song One", artist = "Artist One"))
        playlistSongsFlow.value = songs
        advanceUntilIdle()

        // Detail loading is finished
        assertFalse(viewModel.uiState.value.isDetailLoading)
        assertEquals(1, viewModel.uiState.value.activeUserPlaylistSongs.size)

        // Closing resets active playlist, songs, and detail loading
        viewModel.closeUserPlaylist()
        assertEquals(null, viewModel.uiState.value.activeUserPlaylist)
        assertTrue(viewModel.uiState.value.activeUserPlaylistSongs.isEmpty())
        assertFalse(viewModel.uiState.value.isDetailLoading)
    }
}
