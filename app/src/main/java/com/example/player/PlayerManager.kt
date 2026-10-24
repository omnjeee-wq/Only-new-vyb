package com.example.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.example.VybeApp
import java.io.File
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.domain.model.ListeningEventType
import com.example.domain.model.Song
import com.example.domain.repository.MusicRepository
import com.example.domain.smartshuffle.MoodCategory
import com.example.domain.smartshuffle.SmartShuffleEngine
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerManager private constructor(
    private val context: Context,
    private val repository: MusicRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val persistence = PlaybackPersistence(context)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pendingCommands = mutableListOf<(MediaController) -> Unit>()

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _heroRefreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val _recsRefreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val recsRefreshSignal: SharedFlow<Unit> = _recsRefreshSignal.asSharedFlow()
    private var recsPlayCounter = 0
    val heroRefreshSignal: SharedFlow<Unit> = _heroRefreshSignal.asSharedFlow()

    private var progressJob: Job? = null
    private var currentSongQualifyingCounted = false
    private var currentSongPlayStartTime = 0L
    private var isRefillingQueue = false

    private fun refillQueueIfNeeded() {
        val state = _playerState.value
        if (isRefillingQueue) return
        if (state.repeatMode != Player.REPEAT_MODE_OFF) return
        if (state.isSmartShuffleEnabled) return
        val current = state.currentSong ?: return
        val remaining = state.queue.size - state.currentIndex - 1
        if (remaining > 4) return

        isRefillingQueue = true
        scope.launch {
            try {
                repository.getRadioQueue(
                    songId = current.id,
                    limit = 12,
                    excludeIds = _playerState.value.queue.map { it.id }
                ).onSuccess { songs ->
                    val existing = _playerState.value.queue.map { it.id }.toSet()
                    val fresh = songs.filter { it.id !in existing }.distinctBy { it.id }.take(10)
                    if (fresh.isNotEmpty()) {
                        _playerState.update { it.copy(queue = it.queue + fresh) }
                        executeWithController { ctrl ->
                            ctrl.addMediaItems(fresh.map { it.toMediaItem() })
                        }
                        persistCurrentState()
                    }
                }
            } finally {
                isRefillingQueue = false
            }
        }
    }

    
    
    init {
        restoreSavedState()
        connectToService()
        scope.launch(Dispatchers.IO) {
            currentStreamingQuality = VybeApp.instance.database.songDao()
                .getPreference("pref_streaming_quality") ?: DEFAULT_STREAMING_QUALITY
        }
    }

    /**
     * Changes the actual stream URL of the currently loaded queue.
     * Merely saving the setting is not enough because Media3 keeps the old
     * MediaItems/URLs in the player until they are replaced.
     */
    fun setStreamingQuality(quality: String) {
        currentStreamingQuality = quality

        scope.launch {
            val ctrl = controller ?: return@launch
            if (ctrl.mediaItemCount == 0) return@launch

            val state = _playerState.value
            val queue = state.queue
            if (queue.isEmpty()) return@launch

            val currentIndex = ctrl.currentMediaItemIndex.coerceIn(0, queue.lastIndex)
            val currentPosition = ctrl.currentPosition.coerceAtLeast(0L)
            val wasPlaying = ctrl.isPlaying
            val mediaItems = queue.map { it.toMediaItem(quality) }

            ctrl.setMediaItems(mediaItems, currentIndex, currentPosition)
            ctrl.prepare()
            if (wasPlaying) ctrl.play() else ctrl.pause()
        }
    }

    private fun restoreSavedState() {
        val saved = persistence.getSavedState() ?: return
        _playerState.update {
            it.copy(
                currentSong = saved.currentSong,
                queue = saved.queue,
                currentIndex = saved.currentIndex,
                currentPositionMs = saved.positionMs,
                repeatMode = saved.repeatMode,
                isShuffleEnabled = saved.isShuffle,
                playlistName = saved.playlistName,
                isPlaying = false,
                isBuffering = false,
                durationMs = (saved.currentSong.durationSeconds.toLong() * 1000L).coerceAtLeast(0L)
            )
        }
    }

    private fun connectToService() {
        val sessionToken = SessionToken(context, ComponentName(context, VybePlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future

        future.addListener({
            try {
                val ctrl = future.get()
                controller = ctrl
                setupController(ctrl)
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun setupController(ctrl: MediaController) {
        ctrl.addListener(playerListener)

        // Sync initial state from controller
        val isPlaying = ctrl.isPlaying
        val currentIdx = ctrl.currentMediaItemIndex
        val queue = _playerState.value.queue

        if (ctrl.mediaItemCount > 0 && currentIdx in queue.indices) {
            val song = queue[currentIdx]
            _playerState.update {
                it.copy(
                    currentSong = song,
                    currentIndex = currentIdx,
                    isPlaying = isPlaying,
                    isBuffering = ctrl.playbackState == Player.STATE_BUFFERING,
                    durationMs = ctrl.duration.coerceAtLeast(0L),
                    currentPositionMs = ctrl.currentPosition.coerceAtLeast(0L),
                    isShuffleEnabled = ctrl.shuffleModeEnabled,
                    repeatMode = ctrl.repeatMode
                )
            }
            if (isPlaying) {
                startProgressTracker()
            }
        } else if (_playerState.value.queue.isNotEmpty() && ctrl.mediaItemCount == 0) {
            scope.launch(Dispatchers.IO) {
                val songDao = VybeApp.instance.database.songDao()
                val continuePlayback = songDao.getPreference("pref_continue_playback_open")?.toBooleanStrictOrNull() ?: true
                if (continuePlayback) {
                    val restoredQueue = _playerState.value.queue
                    val restoredIdx = _playerState.value.currentIndex.coerceIn(0, restoredQueue.size - 1)
                    val restoredPos = _playerState.value.currentPositionMs
                    val quality = songDao.getPreference("pref_streaming_quality") ?: "High (320 kbps)"
                    val mediaItems = restoredQueue.map { it.toMediaItem(quality) }

                    val savedRepeat = songDao.getPreference("pref_repeat_mode") ?: "Off"
                    val mappedRepeat = when (savedRepeat) {
                        "All" -> Player.REPEAT_MODE_ALL
                        "One" -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                    val startOnLaunch = songDao.getPreference("pref_start_playback_launch")?.toBooleanStrictOrNull() ?: false

                    withContext(Dispatchers.Main) {
                        ctrl.setMediaItems(mediaItems, restoredIdx, restoredPos)
                        ctrl.shuffleModeEnabled = _playerState.value.isShuffleEnabled
                        ctrl.repeatMode = mappedRepeat
                        _playerState.update { it.copy(repeatMode = mappedRepeat) }

                        if (startOnLaunch) {
                            ctrl.prepare()
                            ctrl.play()
                        }
                    }
                }
            }
        }

        // Execute queued commands
        val commandsToRun = ArrayList(pendingCommands)
        pendingCommands.clear()
        commandsToRun.forEach { it(ctrl) }
    }

    private fun executeWithController(action: (MediaController) -> Unit) {
        val ctrl = controller
        if (ctrl != null) {
            action(ctrl)
        } else {
            pendingCommands.add(action)
            ensureServiceStarted()
            if (controllerFuture == null || controllerFuture?.isDone == true) {
                connectToService()
            }
        }
    }

    private fun ensureServiceStarted() {
        try {
            val serviceIntent = Intent(context, VybePlaybackService::class.java)
            context.startService(serviceIntent)
        } catch (_: Exception) {
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playerState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startProgressTracker()
            } else {
                stopProgressTracker()
            }
            persistCurrentState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _playerState.update { it.copy(isBuffering = true) }
                }
                Player.STATE_READY -> {
                    val ctrl = controller
                    val dur = ctrl?.duration?.coerceAtLeast(0L) ?: 0L
                    _playerState.update {
                        it.copy(
                            isBuffering = false,
                            durationMs = if (dur > 0) dur else it.durationMs
                        )
                    }
                    persistCurrentState()
                }
                Player.STATE_ENDED -> {
                    _playerState.update { it.copy(isPlaying = false, isBuffering = false) }
                    handleSongCompleted()
                }
                Player.STATE_IDLE -> {
                    _playerState.update { it.copy(isBuffering = false) }
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            android.util.Log.e("VybePlayer", "Playback exception: ${error.errorCodeName} (${error.errorCode})", error)
            _playerState.update { it.copy(isPlaying = false, isBuffering = false) }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val ctrl = controller ?: return
            val currentIdx = ctrl.currentMediaItemIndex
            val queue = _playerState.value.queue
            val newSong = if (currentIdx in queue.indices) {
                queue[currentIdx]
            } else {
                mediaItem?.toSong()
            }

            if (newSong != null) {
                val prevSong = _playerState.value.currentSong
                if (prevSong != null && prevSong.id != newSong.id && !currentSongQualifyingCounted) {
                    val playedMs = System.currentTimeMillis() - currentSongPlayStartTime
                    if (playedMs in 2000..20000) {
                        scope.launch(Dispatchers.IO) {
                            repository.recordListeningEvent(prevSong, ListeningEventType.SKIPPED)
                        }
                    }
                }

                currentSongQualifyingCounted = false
                currentSongPlayStartTime = System.currentTimeMillis()

                _playerState.update {
                    it.copy(
                        currentSong = newSong,
                        currentIndex = currentIdx,
                        currentPositionMs = 0L,
                        durationMs = (newSong.durationSeconds.toLong() * 1000L).coerceAtLeast(0L)
                    )
                }

                persistCurrentState()
                refillQueueIfNeeded()

                scope.launch(Dispatchers.IO) {
                    repository.recordListeningEvent(newSong, ListeningEventType.PLAY_STARTED)
                }
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _playerState.update { it.copy(isShuffleEnabled = shuffleModeEnabled) }
            persistCurrentState()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _playerState.update { it.copy(repeatMode = repeatMode) }
            persistCurrentState()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            val ctrl = controller ?: return
            if (ctrl.mediaItemCount > 0 && _playerState.value.queue.isEmpty()) {
                val reconstructedQueue = mutableListOf<Song>()
                for (i in 0 until ctrl.mediaItemCount) {
                    ctrl.getMediaItemAt(i).toSong()?.let { reconstructedQueue.add(it) }
                }
                if (reconstructedQueue.isNotEmpty()) {
                    val idx = ctrl.currentMediaItemIndex.coerceIn(0, reconstructedQueue.size - 1)
                    _playerState.update {
                        it.copy(
                            queue = reconstructedQueue,
                            currentIndex = idx,
                            currentSong = reconstructedQueue[idx]
                        )
                    }
                    persistCurrentState()
                }
            }
        }
    }

    fun playSong(
        song: Song,
        playlist: List<Song> = listOf(song),
        playlistName: String = "Your Music"
    ) {
        ensureServiceStarted()

        val fullList = if (playlist.isEmpty() || !playlist.contains(song)) {
            listOf(song) + playlist
        } else {
            playlist
        }

        val startIndex = fullList.indexOfFirst { it.id == song.id }.coerceAtLeast(0)

        _playerState.update {
            it.copy(
                queue = fullList,
                currentIndex = startIndex,
                currentSong = song,
                playlistName = playlistName
            )
        }

        scope.launch(Dispatchers.IO) {
            val quality = VybeApp.instance.database.songDao().getPreference("pref_streaming_quality") ?: "High (320 kbps)"
            val mediaItems = fullList.map { it.toMediaItem(quality) }

            withContext(Dispatchers.Main) {
                executeWithController { ctrl ->
                    ctrl.setMediaItems(mediaItems, startIndex, 0L)
                    ctrl.prepare()
                    ctrl.play()
                }
            }
        }

        persistCurrentState()
    }

    fun togglePlayPause() {
        executeWithController { ctrl ->
            if (ctrl.isPlaying) {
                ctrl.pause()
            } else {
                ensureServiceStarted()
                if (ctrl.playbackState == Player.STATE_IDLE && _playerState.value.currentSong != null) {
                    if (ctrl.mediaItemCount == 0 && _playerState.value.queue.isNotEmpty()) {
                        val mediaItems = _playerState.value.queue.map { it.toMediaItem() }
                        ctrl.setMediaItems(
                            mediaItems,
                            _playerState.value.currentIndex.coerceAtLeast(0),
                            _playerState.value.currentPositionMs
                        )
                    }
                    ctrl.prepare()
                }
                ctrl.play()
            }
        }
    }

    fun play() {
        ensureServiceStarted()
        executeWithController { ctrl ->
            if (ctrl.playbackState == Player.STATE_IDLE) {
                ctrl.prepare()
            }
            ctrl.play()
        }
    }

    fun pause() {
        executeWithController { ctrl ->
            ctrl.pause()
        }
    }

    fun seekTo(positionMs: Long) {
        _playerState.update { it.copy(currentPositionMs = positionMs) }
        executeWithController { ctrl ->
            ctrl.seekTo(positionMs)
        }
        persistCurrentState()
    }

    fun next() {
        executeWithController { ctrl ->
            if (ctrl.hasNextMediaItem()) {
                ctrl.seekToNextMediaItem()
            } else if (_playerState.value.repeatMode == Player.REPEAT_MODE_ALL && _playerState.value.queue.isNotEmpty()) {
                ctrl.seekTo(0, 0L)
            }
        }
    }

    fun previous() {
        executeWithController { ctrl ->
            if (ctrl.currentPosition > 3000L) {
                ctrl.seekTo(0L)
            } else if (ctrl.hasPreviousMediaItem()) {
                ctrl.seekToPreviousMediaItem()
            } else {
                ctrl.seekTo(0L)
            }
        }
    }

    fun toggleShuffle() {
        executeWithController { ctrl ->
            val currentState = _playerState.value
            if (!currentState.isShuffleEnabled) {
                // 1. Off -> Standard Shuffle
                ctrl.shuffleModeEnabled = true
                _playerState.update {
                    it.copy(
                        isShuffleEnabled = true,
                        isSmartShuffleEnabled = false,
                        activeMoodFlow = null
                    )
                }
            } else if (!currentState.isSmartShuffleEnabled) {
                // 2. Standard Shuffle -> Smart Shuffle (Mood Flow)
                ctrl.shuffleModeEnabled = false // Custom harmonic sequence order
                val currentSong = currentState.currentSong
                val fullQueue = currentState.queue
                if (fullQueue.isNotEmpty()) {
                    val sequenced = SmartShuffleEngine.generateCohesiveSequence(
                        songs = fullQueue,
                        startingSongId = currentSong?.id
                    )
                    val newIdx = currentSong?.let { s -> sequenced.indexOfFirst { it.id == s.id } }?.coerceAtLeast(0) ?: 0
                    val mediaItems = sequenced.map { it.toMediaItem() }
                    val currentPos = ctrl.currentPosition
                    ctrl.setMediaItems(mediaItems, newIdx, currentPos)
                    _playerState.update {
                        it.copy(
                            queue = sequenced,
                            currentIndex = newIdx,
                            isShuffleEnabled = true,
                            isSmartShuffleEnabled = true,
                            activeMoodFlow = "✨ Harmonic Flow"
                        )
                    }
                } else {
                    _playerState.update {
                        it.copy(
                            isShuffleEnabled = true,
                            isSmartShuffleEnabled = true,
                            activeMoodFlow = "✨ Harmonic Flow"
                        )
                    }
                }
            } else {
                // 3. Smart Shuffle -> Off
                ctrl.shuffleModeEnabled = false
                _playerState.update {
                    it.copy(
                        isShuffleEnabled = false,
                        isSmartShuffleEnabled = false,
                        activeMoodFlow = null
                    )
                }
            }
            persistCurrentState()
        }
    }

    fun smartShufflePlay(list: List<Song>, sourceName: String, preferredMood: MoodCategory? = null) {
        if (list.isEmpty()) return
        ensureServiceStarted()

        val sequenced = SmartShuffleEngine.generateCohesiveSequence(
            songs = list,
            startingSongId = null,
            preferredMood = preferredMood
        )

        val flowName = preferredMood?.let { "${it.emoji} ${it.title} Flow" } ?: "✨ Harmonic Flow"
        val startSong = sequenced.first()

        _playerState.update {
            it.copy(
                queue = sequenced,
                currentIndex = 0,
                currentSong = startSong,
                playlistName = "$sourceName ($flowName)",
                isShuffleEnabled = true,
                isSmartShuffleEnabled = true,
                activeMoodFlow = flowName
            )
        }

        val mediaItems = sequenced.map { it.toMediaItem() }

        executeWithController { ctrl ->
            ctrl.shuffleModeEnabled = false // Queue is already harmonically arranged
            ctrl.setMediaItems(mediaItems, 0, 0L)
            ctrl.prepare()
            ctrl.play()
        }

        persistCurrentState()
    }

    fun cycleRepeatMode() {
        executeWithController { ctrl ->
            val nextRepeat = when (ctrl.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            ctrl.repeatMode = nextRepeat
            _playerState.update { it.copy(repeatMode = nextRepeat) }
            persistCurrentState()
        }
    }

     fun replaceUpcomingQueue(currentSong: Song, newQueue: List<Song>) {
        executeWithController { ctrl ->
            val state = _playerState.value
            if (state.currentSong?.id != currentSong.id) return@executeWithController
            val idx = ctrl.currentMediaItemIndex
            val itemCount = ctrl.mediaItemCount
            if (itemCount > idx + 1) ctrl.removeMediaItems(idx + 1, itemCount)
            val upcoming = newQueue.filterNot { it.id == currentSong.id }
            if (upcoming.isNotEmpty()) ctrl.addMediaItems(upcoming.map { it.toMediaItem() })
            val updatedQueue = state.queue.take(idx + 1) + upcoming
            _playerState.update { it.copy(queue = updatedQueue) }
            persistCurrentState()
        }
     }

    fun addToQueue(song: Song) {
        val updatedQueue = _playerState.value.queue + song
        _playerState.update { it.copy(queue = updatedQueue) }
        executeWithController { ctrl ->
            ctrl.addMediaItem(song.toMediaItem())
        }
        persistCurrentState()
    }

    fun removeFromQueue(index: Int) {
        val currentQueue = _playerState.value.queue.toMutableList()
        if (index in currentQueue.indices) {
            currentQueue.removeAt(index)
            _playerState.update { it.copy(queue = currentQueue) }
            executeWithController { ctrl ->
                ctrl.removeMediaItem(index)
                val newIdx = ctrl.currentMediaItemIndex
                _playerState.update { it.copy(currentIndex = newIdx) }
            }
            persistCurrentState()
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val ctrl = controller
                if (ctrl != null) {
                    val pos = ctrl.currentPosition.coerceAtLeast(0L)
                    val dur = ctrl.duration.coerceAtLeast(0L)
                    _playerState.update {
                        it.copy(
                            currentPositionMs = pos,
                            durationMs = if (dur > 0) dur else it.durationMs
                        )
                    }
                    checkQualifyingPlay(pos, dur)
                }
                delay(250)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun checkQualifyingPlay(posMs: Long, durMs: Long) {
        if (currentSongQualifyingCounted) return
        val currentSong = _playerState.value.currentSong ?: return

        val reached30Sec = posMs >= 30_000L
        val reached50Percent = durMs > 0 && (posMs.toDouble() / durMs.toDouble() >= 0.5)

        if (reached30Sec || reached50Percent) {
            currentSongQualifyingCounted = true
            onQualifyingPlayCounted(currentSong)
        }
    }

    private fun handleSongCompleted() {
        val currentSong = _playerState.value.currentSong ?: return
        if (!currentSongQualifyingCounted) {
            currentSongQualifyingCounted = true
            onQualifyingPlayCounted(currentSong)
        }
        scope.launch(Dispatchers.IO) {
            repository.recordListeningEvent(currentSong, ListeningEventType.PLAY_COMPLETED)
        }
        executeWithController { ctrl ->
            if (ctrl.hasNextMediaItem()) {
                ctrl.seekToNextMediaItem()
            } else if (_playerState.value.repeatMode == Player.REPEAT_MODE_ALL && _playerState.value.queue.isNotEmpty()) {
                ctrl.seekTo(0, 0L)
            } else {
                scope.launch(Dispatchers.IO) {
                    val autoplay = VybeApp.instance.database.songDao().getPreference("pref_autoplay")?.toBooleanStrictOrNull() ?: true
                    if (autoplay) {
                        val candidates = repository.getRadioQueue(currentSong.id, 10).getOrNull()
                        if (!candidates.isNullOrEmpty()) {
                            withContext(Dispatchers.Main) {
                                playSong(candidates.first(), candidates, "Recommended For You")
                            }
                        }
                    }
                }
            }
        }
    }

   private fun onQualifyingPlayCounted(song: Song) {
        recsPlayCounter++
        if (recsPlayCounter >= 3) {
            recsPlayCounter = 0
            scope.launch { _recsRefreshSignal.emit(Unit) }
        }
        scope.launch(Dispatchers.IO) {
            val newCount = repository.incrementQualifyingPlayCount()
            if (newCount >= 5) {
                repository.resetQualifyingPlayCount()
                _heroRefreshSignal.emit(Unit)
            }
        }
   } 

    private fun persistCurrentState() {
        val state = _playerState.value
        persistence.saveState(
            currentSong = state.currentSong,
            queue = state.queue,
            currentIndex = state.currentIndex,
            positionMs = state.currentPositionMs,
            repeatMode = state.repeatMode,
            isShuffle = state.isShuffleEnabled,
            playlistName = state.playlistName
        )
    }

    companion object {
        const val DEFAULT_STREAMING_QUALITY = "High (320 kbps)"

        @Volatile
        private var currentStreamingQuality: String = DEFAULT_STREAMING_QUALITY

        @Volatile
        private var INSTANCE: PlayerManager? = null

        fun getCurrentStreamingQuality(): String = currentStreamingQuality

        fun initialize(context: Context, repository: MusicRepository): PlayerManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlayerManager(context.applicationContext, repository).also { INSTANCE = it }
            }
        }

        fun getInstance(): PlayerManager {
            return INSTANCE ?: throw IllegalStateException("PlayerManager must be initialized first")
        }
    }
}

fun Song.toMediaItem(preferredQuality: String? = null): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setArtworkUri(if (artworkUrl.isNotBlank()) Uri.parse(artworkUrl) else null)
        .setDisplayTitle(title)
        .setSubtitle(artist)
        .setDescription(album)
        .setExtras(Bundle().apply {
            putString("id", id)
            putString("title", title)
            putString("artist", artist)
            putString("artistId", artistId ?: "")
            putString("album", album)
            putString("albumId", albumId ?: "")
            putInt("durationSeconds", durationSeconds)
            putString("year", year)
            putString("language", language)
            putString("artworkUrl", artworkUrl)
            putString("streamUrl", streamUrl)
            putBoolean("hasLyrics", hasLyrics)
            putString("lyricsId", lyricsId ?: "")
            putBoolean("isLiked", isLiked)
        })
        .build()

    val effectiveQuality = preferredQuality ?: PlayerManager.getCurrentStreamingQuality()

    val playableUri = if (streamUrl.startsWith("file://") || streamUrl.startsWith("/")) {
        Uri.parse(streamUrl)
    } else {
        val localFile = File(VybeApp.instance.filesDir, "downloads/$id.m4a")
        if (localFile.exists() && localFile.length() > 0) {
            Uri.fromFile(localFile)
        } else {
            val resolvedUrl = com.example.util.MusicUtils.applyQualityToUrl(streamUrl, effectiveQuality)
            Uri.parse(resolvedUrl)
        }
    }

    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(playableUri)
        .setMediaMetadata(metadata)
        .build()
}

fun MediaItem.toSong(): Song? {
    val extras = mediaMetadata.extras
    return if (extras != null && extras.containsKey("id")) {
        Song(
            id = extras.getString("id") ?: mediaId,
            title = extras.getString("title") ?: (mediaMetadata.title?.toString() ?: ""),
            artist = extras.getString("artist") ?: (mediaMetadata.artist?.toString() ?: ""),
            artistId = extras.getString("artistId")?.takeIf { it.isNotEmpty() },
            album = extras.getString("album") ?: (mediaMetadata.albumTitle?.toString() ?: ""),
            albumId = extras.getString("albumId")?.takeIf { it.isNotEmpty() },
            durationSeconds = extras.getInt("durationSeconds", 0),
            year = extras.getString("year") ?: "",
            language = extras.getString("language") ?: "",
            artworkUrl = extras.getString("artworkUrl") ?: (mediaMetadata.artworkUri?.toString() ?: ""),
            streamUrl = extras.getString("streamUrl") ?: (localConfiguration?.uri?.toString() ?: ""),
            hasLyrics = extras.getBoolean("hasLyrics", false),
            lyricsId = extras.getString("lyricsId")?.takeIf { it.isNotEmpty() },
            isLiked = extras.getBoolean("isLiked", false)
        )
    } else {
        val idVal = mediaId.ifBlank { null } ?: return null
        Song(
            id = idVal,
            title = mediaMetadata.title?.toString() ?: "",
            artist = mediaMetadata.artist?.toString() ?: "",
            album = mediaMetadata.albumTitle?.toString() ?: "",
            artworkUrl = mediaMetadata.artworkUri?.toString() ?: "",
            streamUrl = localConfiguration?.uri?.toString() ?: "",
            durationSeconds = 0
        )
    }
}
