package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.Coil
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.R
import com.example.domain.model.Song
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlayfairFontFamily
import com.example.ui.theme.neonGlow
import com.example.ui.theme.pulsingGlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

private val GreatVibesFontFamily = FontFamily(
    Font(
        resId = R.font.great_vibes,
        weight = FontWeight.Normal
    )
)

private val VibrantThemePalette = listOf(
    Color(0xFFFF6436),
    Color(0xFFFF2A85),
    Color(0xFF00E5FF),
    Color(0xFFA855F7),
    Color(0xFFFFB800),
    Color(0xFF10B981),
    Color(0xFFFF3366)
)

@Composable
fun HeroBanner(
    heroSong: Song?,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val artistName = heroSong?.artist?.ifBlank { "TOP ARTIST" } ?: "TOP ARTIST"
    val artistImageUrl = heroSong?.artworkUrl

    val fallbackTheme = remember(heroSong?.id) {
        val hash = abs((heroSong?.id ?: "vybe").hashCode())
        VibrantThemePalette[hash % VibrantThemePalette.size]
    }

    var targetThemeColor by remember(heroSong?.id) {
        mutableStateOf(fallbackTheme)
    }

    // Theme is extracted from the artist photo, not just a static placeholder.
    LaunchedEffect(artistImageUrl) {
        if (!artistImageUrl.isNullOrBlank()) {
            targetThemeColor = extractVibrantColorFromArtwork(
                context = context,
                imageUrl = artistImageUrl,
                fallback = fallbackTheme
            )
        } else {
            targetThemeColor = fallbackTheme
        }
    }

    val themeColor by animateColorAsState(
        targetValue = targetThemeColor,
        animationSpec = tween(650),
        label = "hero_artist_theme"
    )

    // Continuous smooth vinyl rotation.
    val rotationTransition = rememberInfiniteTransition(label = "vinyl_rotation")
    val vinylRotation by rotationTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 9000,
                easing = androidx.compose.animation.core.LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinyl_rotation_angle"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hero_banner")
    ) {
        val availableWidth = maxWidth

        // Adaptive breakpoint determinations
        val isCompactNarrow = availableWidth < 360.dp
        val isCompact = availableWidth < 430.dp
        val isMedium = availableWidth < 600.dp
        val isExpanded = availableWidth < 840.dp

        // 1. Adaptive Banner Height:
        // Scales proportionally to available width so compact screens are not overwhelmed
        // and tablets have generous cinematic visual impact.
        val bannerHeight = when {
            isCompactNarrow -> 310.dp
            isCompact -> 345.dp
            isMedium -> 365.dp
            isExpanded -> 385.dp
            else -> 415.dp
        }

        // 2. Adaptive Spacing & Insets
        val horizontalPadding = when {
            isCompactNarrow -> 14.dp
            isCompact -> 18.dp
            isMedium -> 22.dp
            isExpanded -> 28.dp
            else -> 36.dp
        }

        val verticalPadding = when {
            isCompactNarrow -> 6.dp
            isCompact -> 8.dp
            else -> 10.dp
        }

        // 3. Proportional Vinyl Sizing:
        // Adapts dynamically so it never collides with or covers left-side text.
        val discSize = when {
            isCompactNarrow -> (availableWidth * 0.44f).coerceIn(135.dp, 155.dp)
            isCompact -> (availableWidth * 0.46f).coerceIn(170.dp, 195.dp)
            isMedium -> (availableWidth * 0.42f).coerceIn(200.dp, 235.dp)
            isExpanded -> (availableWidth * 0.36f).coerceIn(240.dp, 280.dp)
            else -> (availableWidth * 0.30f).coerceIn(280.dp, 330.dp)
        }

        val vinylEndPadding = when {
            isCompactNarrow -> 8.dp
            isCompact -> 12.dp
            isMedium -> 16.dp
            isExpanded -> 24.dp
            else -> 36.dp
        }

        // 4. Safe Maximum Text Width:
        // Evaluates remaining space on the left to guarantee zero overlap with the vinyl disc.
        val safeGap = if (isCompactNarrow) 8.dp else 16.dp
        val maxTextWidth = (availableWidth - vinylEndPadding - discSize - horizontalPadding - safeGap)
            .coerceIn(130.dp, 580.dp)

        // 5. Adaptive Typography Sizing
        val scriptFontSize = when {
            isCompactNarrow -> 28.sp
            isCompact -> 36.sp
            isMedium -> 40.sp
            isExpanded -> 46.sp
            else -> 52.sp
        }

        val titleFontSize = when {
            isCompactNarrow -> 22.sp
            isCompact -> 28.sp
            isMedium -> 34.sp
            isExpanded -> 40.sp
            else -> 46.sp
        }

        val titleLineHeight = when {
            isCompactNarrow -> 26.sp
            isCompact -> 33.sp
            isMedium -> 39.sp
            isExpanded -> 46.sp
            else -> 52.sp
        }

        val artistFontSize = when {
            isCompactNarrow -> 12.sp
            isCompact -> 13.sp
            isMedium -> 14.sp
            else -> 15.sp
        }

        // 6. Adaptive Play Button Sizing
        val playButtonHorizontalPadding = if (isCompactNarrow) 16.dp else if (isCompact) 20.dp else 24.dp
        val playButtonVerticalPadding = if (isCompactNarrow) 8.dp else 10.dp
        val playButtonIconSize = if (isCompactNarrow) 16.dp else 18.dp
        val playButtonFontSize = if (isCompactNarrow) 12.sp else 13.sp

        // 7. Adaptive Top Bar Sizing
        val logoSize = if (isCompactNarrow) 28.dp else if (isCompact) 32.dp else 38.dp
        val logoInnerSize = if (isCompactNarrow) 38.dp else if (isCompact) 42.dp else 48.dp
        val vybeTitleFontSize = if (isCompactNarrow) 22.sp else if (isCompact) 25.sp else 28.sp
        val shareButtonSize = if (isCompactNarrow) 34.dp else 38.dp
        val shareIconSize = if (isCompactNarrow) 16.dp else 19.dp

        // 8. Vinyl Internal Components Proportional Dimensions
        val outerVinylSize = discSize * 0.93f
        val discGlowSize = discSize * 0.97f
        val discGlowRadius = (discSize * 0.18f).coerceIn(22.dp, 54.dp)
        val artistCenterSize = (discSize * 0.41f).coerceIn(56.dp, 135.dp)
        val centerHoleSize = (discSize * 0.045f).coerceIn(6.dp, 14.dp)
        val centerGlowRadius = (discSize * 0.07f).coerceIn(8.dp, 20.dp)

        // 9. Adaptive Bottom Separator Height
        val separatorHeight = if (isCompactNarrow) 24.dp else if (isCompact) 32.dp else 44.dp

        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bannerHeight)
                    .clipToBounds()
            ) {

                // --------------------------------------------------------
                // 1. SONG THUMBNAIL = FULL BLEED BLURRED BACKGROUND
                // --------------------------------------------------------
                if (heroSong != null && heroSong.artworkUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(heroSong.artworkUrl)
                            .crossfade(true)
                            .size(768, 768)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(1.18f)
                            .blur(28.dp)
                    )

                    // Dark cinematic overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.30f))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF26182B),
                                        MaterialTheme.colorScheme.background
                                    ),
                                    radius = 700f
                                )
                            )
                    )
                }

                // Artist-color ambient wash
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    themeColor.copy(alpha = 0.26f),
                                    Color.Transparent
                                ),
                                radius = (availableWidth.value * 1.5f).coerceAtLeast(600f)
                            )
                        )
                )

                // Left readability gradient with dynamic stop aligned to text width
                val gradientStop1 = ((maxTextWidth / availableWidth) * 0.80f).coerceIn(0.35f, 0.55f)
                val gradientStop2 = ((maxTextWidth / availableWidth) * 1.18f).coerceIn(0.58f, 0.85f)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0f to Color(0xD9000000),
                                gradientStop1 to Color(0x88000000),
                                gradientStop2 to Color.Transparent,
                                1f to Color.Transparent
                            )
                        )
                )

                // Bottom fade gradient
                val bottomFadeHeight = (bannerHeight * 0.28f).coerceIn(80.dp, 120.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bottomFadeHeight)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.35f to Color(0x77000000),
                                1f to Color.Black
                            )
                        )
                )

                // --------------------------------------------------------
                // 2. PROPORTIONALLY SCALED ROTATING VINYL + ARTIST PHOTO
                // --------------------------------------------------------
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = vinylEndPadding)
                        .size(discSize)
                        .graphicsLayer {
                            rotationZ = vinylRotation
                        },
                    contentAlignment = Alignment.Center
                ) {

                    // Big soft glow behind the disc
                    Box(
                        modifier = Modifier
                            .size(discGlowSize)
                            .neonGlow(
                                color = themeColor,
                                radius = discGlowRadius,
                                alpha = 0.75f
                            )
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        themeColor.copy(alpha = 0.34f),
                                        Color.Transparent
                                    )
                                ),
                                CircleShape
                            )
                    )

                    // Outer vinyl body
                    Box(
                        modifier = Modifier
                            .size(outerVinylSize)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF343434),
                                        Color(0xFF101010),
                                        Color.Black,
                                        Color(0xFF181818)
                                    )
                                )
                            )
                            .border(
                                1.5.dp,
                                themeColor.copy(alpha = 0.65f),
                                CircleShape
                            )
                    ) {
                        // Proportional Vinyl Grooves
                        for (i in 0..7) {
                            val grooveSize = outerVinylSize * (0.86f - i * 0.072f)
                            if (grooveSize > artistCenterSize + 6.dp) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(grooveSize)
                                        .border(
                                            1.dp,
                                            Color.White.copy(alpha = 0.10f),
                                            CircleShape
                                        )
                                )
                            }
                        }

                        // Artist image in the center
                        if (!artistImageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(artistImageUrl)
                                    .crossfade(true)
                                    .size(240, 240)
                                    .build(),
                                contentDescription = artistName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(artistCenterSize)
                                    .clip(CircleShape)
                                    .border(
                                        2.dp,
                                        themeColor.copy(alpha = 0.95f),
                                        CircleShape
                                    )
                                    .neonGlow(
                                        color = themeColor,
                                        radius = centerGlowRadius,
                                        alpha = 0.75f
                                    )
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(artistCenterSize)
                                    .clip(CircleShape)
                                    .background(themeColor.copy(alpha = 0.25f))
                            )
                        }

                        // Center spindle hole
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(centerHoleSize)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f))
                                .border(
                                    1.dp,
                                    Color.White.copy(alpha = 0.25f),
                                    CircleShape
                                )
                        )
                    }
                }

                // --------------------------------------------------------
                // 3. FOREGROUND CONTENT (TOP BAR, TITLES & CONTROLS)
                // --------------------------------------------------------
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(
                            start = horizontalPadding,
                            end = horizontalPadding,
                            top = verticalPadding,
                            bottom = verticalPadding + 4.dp
                        ),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.Start
                ) {

                    // Top Bar (Branding + Share Action)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (isCompactNarrow) 8.dp else 10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(logoSize)
                                    .neonGlow(
                                        color = themeColor,
                                        radius = if (isCompactNarrow) 8.dp else 10.dp,
                                        alpha = 0.6f
                                    )
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary,
                                                themeColor
                                            )
                                        ),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.vybe_logo),
                                    contentDescription = "VYBE Logo",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(logoInnerSize)
                                )
                            }

                            Text(
                                text = "VYBE",
                                color = Color.White,
                                fontSize = vybeTitleFontSize,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = OutfitFontFamily,
                                letterSpacing = 1.5.sp
                            )
                        }

                        IconButton(
                            onClick = { shareVybeApp(context) },
                            modifier = Modifier
                                .size(shareButtonSize)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.16f))
                                .border(
                                    1.dp,
                                    Color.White.copy(alpha = 0.22f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = "Share VYBE",
                                tint = Color.White,
                                modifier = Modifier.size(shareIconSize)
                            )
                        }
                    }

                    // Just Vibe + Song Title + Artist Metadata
                    Column(
                        modifier = Modifier
                            .widthIn(max = maxTextWidth)
                            .padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = "Just Vibe",
                            color = themeColor,
                            fontSize = scriptFontSize,
                            fontFamily = GreatVibesFontFamily,
                            fontWeight = FontWeight.Normal,
                            style = TextStyle(
                                shadow = Shadow(
                                    color = themeColor.copy(alpha = 0.90f),
                                    blurRadius = 28f
                                )
                            )
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = heroSong?.title?.ifBlank { "Ghera Hua" } ?: "Ghera Hua",
                            color = Color.White,
                            fontSize = titleFontSize,
                            lineHeight = titleLineHeight,
                            fontFamily = PlayfairFontFamily,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                shadow = Shadow(
                                    color = themeColor.copy(alpha = 0.50f),
                                    blurRadius = 18f
                                )
                            )
                        )

                        if (!isCompactNarrow && artistName.isNotBlank() && artistName != "TOP ARTIST") {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = artistName,
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = artistFontSize,
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Play / Pause Button with Touch Accessibility Target
                    Box(
                        modifier = Modifier
                            .pulsingGlow(
                                color = themeColor,
                                minRadius = 8.dp,
                                maxRadius = 20.dp,
                                cornerRadius = 24.dp
                            )
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        themeColor.copy(alpha = 0.92f),
                                        themeColor.copy(alpha = 0.58f)
                                    )
                                )
                            )
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.38f),
                                RoundedCornerShape(24.dp)
                            )
                            .clickable { onPlayPauseClick() }
                            .padding(
                                horizontal = playButtonHorizontalPadding,
                                vertical = playButtonVerticalPadding
                            )
                            .testTag("hero_play_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AnimatedContent(
                                targetState = isPlaying,
                                transitionSpec = {
                                    fadeIn(tween(150)) togetherWith
                                        androidx.compose.animation.fadeOut(tween(150))
                                },
                                label = "hero_play_icon"
                            ) { playing ->
                                Icon(
                                    imageVector = if (playing) {
                                        Icons.Default.Pause
                                    } else {
                                        Icons.Default.PlayArrow
                                    },
                                    contentDescription = if (playing) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(playButtonIconSize)
                                )
                            }

                            Text(
                                text = if (isPlaying) "PAUSE" else "PLAY",
                                color = Color.White,
                                fontSize = playButtonFontSize,
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                        }
                    }
                }
            }

            // Clean bottom separator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(separatorHeight)
                    .background(Color.Black)
            )
        }
    }
}

/**
 * Extracts a vibrant colour from the artist image.
 */
private suspend fun extractVibrantColorFromArtwork(
    context: Context,
    imageUrl: String,
    fallback: Color
): Color = withContext(Dispatchers.IO) {
    try {
        val loader = Coil.imageLoader(context)

        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .allowHardware(false)
            .size(64, 64)
            .build()

        val result = (loader.execute(request) as? SuccessResult)?.drawable
        val bitmap = (result as? BitmapDrawable)?.bitmap
            ?: return@withContext fallback

        var bestColor = fallback
        var highestScore = -1f

        val width = bitmap.width
        val height = bitmap.height
        val step = maxOf(1, width / 12)

        for (x in 0 until width step step) {
            for (y in 0 until height step step) {
                val pixel = bitmap.getPixel(x, y)

                val r = android.graphics.Color.red(pixel) / 255f
                val g = android.graphics.Color.green(pixel) / 255f
                val b = android.graphics.Color.blue(pixel) / 255f

                val max = maxOf(r, g, b)
                val min = minOf(r, g, b)
                val delta = max - min

                val saturation = if (max == 0f) 0f else delta / max
                val value = max

                if (value in 0.35f..0.98f && saturation > 0.35f) {
                    val score = saturation * 2.0f + value

                    if (score > highestScore) {
                        highestScore = score
                        bestColor = Color(r, g, b)
                    }
                }
            }
        }

        bestColor
    } catch (_: Exception) {
        fallback
    }
}

private fun shareVybeApp(context: Context) {
    val shareMessage = """
        🎧 I'm listening to music on VYBE — your personal music experience.

        Listen to music ad-free, discover songs made for your vibe, and enjoy your music your way.

        Download VYBE:
        https://t.me/VyomaOfficial

        🎵 VYBE — Made For Your Vibe.
    """.trimIndent()

    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, shareMessage)
        type = "text/plain"
    }

    context.startActivity(
        Intent.createChooser(
            sendIntent,
            "Share VYBE via"
        )
    )
}
