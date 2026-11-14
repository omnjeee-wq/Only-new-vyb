package com.example.ui.ai

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.VybeApp
import com.example.data.ai.AiAssistantRequest
import com.example.data.ai.AiDjRequest
import com.example.data.ai.AiLyricsRequest
import com.example.data.ai.AiMoodSearchRequest
import com.example.data.ai.AiRecommendationRequest
import com.example.data.ai.AiSongDto
import com.example.domain.model.Song
import com.example.domain.recommendation.RecommendationEngine
import com.example.domain.repository.MusicRepository
import com.example.domain.smartshuffle.MoodCategory
import com.example.domain.smartshuffle.SmartShuffleEngine
import com.example.player.PlayerManager
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

private data class AiChatLine(val prompt: String, val answer: String)

@Composable
fun AiHubScreen(
    repository: MusicRepository,
    recommendationEngine: RecommendationEngine,
    playerManager: PlayerManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val aiApi = remember { VybeApp.instance.aiApi }
    val aiConfigured = BuildConfig.VYBE_AI_BASE_URL.isNotBlank()
    var prompt by remember { mutableStateOf("") }
    var lyricsInput by remember { mutableStateOf("") }
    var lyricsQuestion by remember { mutableStateOf("Explain the meaning and emotion of these lyrics.") }
    var resultText by remember { mutableStateOf(if (aiConfigured) "AI backend connected. Ask VYBE anything about music." else "AI server is not configured yet. Local discovery, recommendations and Smart DJ still work.") }
    var busy by remember { mutableStateOf(false) }
    var busyLabel by remember { mutableStateOf("") }
    var foundSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var selectedAudioName by remember { mutableStateOf("") }
    val chatLines = remember { mutableStateListOf<AiChatLine>() }
    val playerState by playerManager.playerState.collectAsState()
    val currentSong = playerState.currentSong

    fun runTask(label: String, task: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            busyLabel = label
            try {
                task()
            } catch (error: Exception) {
                resultText = error.localizedMessage ?: "Something went wrong. Please try again."
            } finally {
                busy = false
                busyLabel = ""
            }
        }
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            runTask("Analyzing audio…") {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Could not read the selected audio file.")
                if (bytes.size > 40 * 1024 * 1024) throw IllegalArgumentException("Please choose an audio file smaller than 40 MB.")
                val body = bytes.toRequestBody("application/octet-stream".toMediaType())
                val part = MultipartBody.Part.createFormData("file", "vybe-audio", body)
                val response = aiApi.analyzeAudio(part)
                selectedAudioName = "Audio analyzed"
                resultText = buildString {
                    append(response.text ?: "Audio analysis complete.")
                    response.bpm?.let { append("\nBPM: ${it.toInt()}") }
                    response.musicalKey?.let { append("\nKey: $it") }
                    response.mood?.let { append("\nMood: $it") }
                    if (response.tags.isNotEmpty()) append("\nTags: ${response.tags.joinToString()}")
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, end = 18.dp, top = 26.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    androidx.compose.material3.Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("VYBE AI", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                }
                Text("Your personal music intelligence layer", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text(if (aiConfigured) "Self-hosted AI endpoint configured" else "Local mode • Connect your own AI server for generative features", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
            }
        }

        item {
            FeatureCard(title = "1. Mood Search", subtitle = "Describe a vibe, scene or feeling. VYBE finds matching tracks.", icon = Icons.Default.Search) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. dangerous villain entry, late-night drive…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                )
                Button(enabled = prompt.isNotBlank() && !busy, onClick = {
                    runTask("Finding your vibe…") {
                        val interpreted = if (aiConfigured) {
                            runCatching { aiApi.moodSearch(AiMoodSearchRequest(prompt)).query }.getOrNull().orEmpty()
                        } else ""
                        val query = interpreted.ifBlank { prompt }
                        val songs = repository.searchSongs(query, 30).getOrThrow()
                        foundSongs = songs
                        resultText = if (aiConfigured && interpreted.isNotBlank()) "Understood as: $interpreted\nFound ${songs.size} tracks." else "Found ${songs.size} tracks using catalog search. Connect AI backend for natural-language mood interpretation."
                    }
                }) { Text("Find music") }
            }
        }

        item {
            FeatureCard(title = "2. Personalized Recommendations", subtitle = "Uses VYBE's existing listening-history and taste engine.", icon = Icons.Default.AutoAwesome) {
                Button(enabled = !busy, onClick = {
                    runTask("Building your mix…") {
                        val personal = recommendationEngine.fetchPersonalPool()
                        val candidates = if (personal.size >= 6) personal else repository.getRecommendationCandidates()
                        val localRanked = recommendationEngine.generateRecommendations(candidates, limit = 12, personalized = personal.size >= 6)
                        val history = repository.getRecentPlayedSongs(12).map { it.toAiDto() }
                        val aiRanked = if (aiConfigured && localRanked.isNotEmpty()) runCatching {
                            aiApi.recommendations(AiRecommendationRequest(history, localRanked.map { it.toAiDto() })).songIds
                        }.getOrNull().orEmpty() else emptyList()
                        foundSongs = if (aiRanked.isNotEmpty()) aiRanked.mapNotNull { id -> localRanked.find { it.id == id } } + localRanked.filterNot { it.id in aiRanked } else localRanked
                        resultText = if (aiRanked.isNotEmpty()) "AI-ranked recommendations are ready." else "Your personalized mix is ready using VYBE's on-device taste engine."
                    }
                }) { Text("Build my mix") }
            }
        }

        item {
            FeatureCard(title = "3. Music Understanding", subtitle = "Analyze an audio file for tempo, key, energy and mood.", icon = Icons.Default.GraphicEq) {
                Button(enabled = aiConfigured && !busy, onClick = { audioPicker.launch("audio/*") }) { Text("Choose audio file") }
                if (selectedAudioName.isNotBlank()) Text(selectedAudioName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Text(if (aiConfigured) "Audio is sent only to the AI backend URL you configure. Use audio you have permission to analyze." else "Configure VYBE_AI_BASE_URL to enable audio analysis.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }

        item {
            FeatureCard(title = "4. VYBE AI Assistant", subtitle = "Ask for playlists, music discovery ideas or help navigating your music.", icon = Icons.Default.Mic) {
                OutlinedTextField(value = prompt, onValueChange = { prompt = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Ask VYBE about music…") })
                Button(enabled = prompt.isNotBlank() && !busy && aiConfigured, onClick = {
                    val userPrompt = prompt.trim()
                    runTask("Thinking…") {
                        val response = aiApi.assistant(AiAssistantRequest(userPrompt, mapOf("current_song" to (currentSong?.let { "${it.title} — ${it.artist}" } ?: "none"))))
                        val answer = response.text ?: "I couldn't generate a response."
                        chatLines.add(AiChatLine(userPrompt, answer))
                        resultText = answer
                        prompt = ""
                    }
                }) { Text("Ask VYBE") }
                if (!aiConfigured) Text("Configure VYBE_AI_BASE_URL to enable the AI assistant.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }

        item {
            FeatureCard(title = "5. Lyrics Intelligence", subtitle = "Understand the themes and emotion in lyrics you provide.", icon = Icons.Default.MusicNote) {
                OutlinedTextField(value = lyricsInput, onValueChange = { lyricsInput = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, placeholder = { Text("Paste lyrics you have permission to use…") })
                OutlinedTextField(value = lyricsQuestion, onValueChange = { lyricsQuestion = it }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(enabled = aiConfigured && lyricsInput.isNotBlank() && !busy, onClick = {
                    runTask("Understanding lyrics…") {
                        val response = aiApi.lyricsInsights(AiLyricsRequest(lyricsInput, lyricsQuestion))
                        resultText = response.text ?: "No explanation returned."
                    }
                }) { Text("Explain lyrics") }
            }
        }

        item {
            FeatureCard(title = "6. Smart DJ Queue", subtitle = "Sequence your current queue for a smoother energy flow.", icon = Icons.Default.QueueMusic) {
                Button(enabled = !busy && currentSong != null && playerState.queue.size > 2, onClick = {
                    runTask("Sequencing your queue…") {
                        val queue = playerState.queue
                        val mood = when {
                            prompt.contains("chill", true) || prompt.contains("calm", true) -> MoodCategory.CHILL
                            prompt.contains("party", true) || prompt.contains("dance", true) -> MoodCategory.UPBEAT
                            prompt.contains("intense", true) || prompt.contains("villain", true) -> MoodCategory.INTENSE
                            prompt.contains("sad", true) || prompt.contains("soul", true) -> MoodCategory.SOULFUL
                            else -> null
                        }
                        val localSequence = SmartShuffleEngine.generateCohesiveSequence(queue, currentSong?.id, mood)
                        val aiIds = if (aiConfigured) runCatching { aiApi.djSequence(AiDjRequest(queue.map { it.toAiDto() }, currentSong?.id, mood?.id)).songIds }.getOrNull().orEmpty() else emptyList()
                        val sequence = if (aiIds.isNotEmpty()) aiIds.mapNotNull { id -> queue.find { it.id == id } }.let { it + localSequence.filterNot { song -> song.id in aiIds } } else localSequence
                        if (sequence.isNotEmpty()) playerManager.replaceUpcomingQueue(currentSong ?: sequence.first(), sequence.dropWhile { it.id == currentSong?.id })
                        resultText = if (aiIds.isNotEmpty()) "AI DJ sequence applied to your upcoming queue." else "Smart DJ sequence applied using VYBE's local mood and energy estimator."
                    }
                }) { Text("Optimize current queue") }
            }
        }

        if (busy) item { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { CircularProgressIndicator() ; Text(busyLabel, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("VYBE response", fontWeight = FontWeight.SemiBold)
                    Text(resultText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        }

        if (chatLines.isNotEmpty()) item { Text("Recent assistant replies", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
        items(chatLines.reversed()) { line ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(line.prompt, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(line.answer, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }

        if (foundSongs.isNotEmpty()) {
            item { Text("Matches for you", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
            items(foundSongs, key = { it.id }) { song ->
                Card(onClick = { playerManager.playSong(song, foundSongs, "VYBE AI") }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        androidx.compose.material3.Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(song.title, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                androidx.compose.material3.Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

private fun Song.toAiDto() = AiSongDto(
    id = id,
    title = title,
    artist = artist,
    album = album,
    language = language,
    durationSeconds = durationSeconds
)
