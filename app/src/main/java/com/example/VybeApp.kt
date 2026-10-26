package com.example

import android.app.Application
import com.example.data.drive.GoogleDriveManager
import com.example.data.local.VybeDatabase
import com.example.data.remote.RetrofitClient
import com.example.data.repository.MusicRepositoryImpl
import com.example.domain.recommendation.PlaylistFeedEngine
import com.example.domain.recommendation.RecommendationEngine
import com.example.domain.repository.MusicRepository
import com.example.player.PlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VybeApp : Application() {

    lateinit var database: VybeDatabase
        private set

    lateinit var repository: MusicRepository
        private set

    lateinit var recommendationEngine: RecommendationEngine
    lateinit var playlistFeedEngine: PlaylistFeedEngine
        private set

    lateinit var playerManager: PlayerManager
        private set

    lateinit var driveManager: GoogleDriveManager
        private set

    lateinit var updateManager: com.example.data.update.UpdateManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = VybeDatabase.getInstance(this)
        repository = MusicRepositoryImpl(RetrofitClient.apiService, database.songDao())
        recommendationEngine = RecommendationEngine(repository, database.songDao())
        playlistFeedEngine = PlaylistFeedEngine(repository, database.songDao())
        playerManager = PlayerManager.initialize(this, repository)
        updateManager = com.example.data.update.UpdateManager(this)
        driveManager = GoogleDriveManager(
            context = this,
            database = database,
            coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        )
        CoroutineScope(Dispatchers.IO).launch {
            com.example.player.EqualizerManager.restoreFromDatabase(database.songDao())
        }
    }

    companion object {
        lateinit var instance: VybeApp
            private set
    }
}
