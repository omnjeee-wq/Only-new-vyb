package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.domain.model.Song
import com.example.ui.components.hero.CinematicBackdrop
import com.example.ui.components.hero.parseArtists
import com.example.ui.components.hero.splitTitle
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.animation.pressScale
import com.example.ui.theme.neonGlow
import kotlin.math.exp
import kotlin.math.sin

private val GreatVibesFontFamily = FontFamily(
    Font(resId = R.font.great_vibes, weight = FontWeight.Normal)
)

private val BrandPurple = Color(0xFF8B3DFF)
private val BrandMagenta = Color(0xFFC84BFF)
private val BrandPink = Color(0xFFFF4FD8)

/**
 * Cinematic hero banner.
 *
 * Only the presentation lives here. Which song is featured is still decided by the ViewModel,
 * and the artwork URL is the same 1:1 thumbnail as before.
 *
 * Layers (bottom -> top):
 *  1. [CinematicBackdrop]  adaptive blurred background generated from the artwork
 *  2. feathered sharp artwork (blends into the backdrop, no visible edges)
 *  3. curved bottom edge that melts into the app background
 *  4. foreground: top bar (unchanged), title, artists, actions, waveform, play orb
 */
@Composable
fun HeroBanner(
    heroSong: Song?,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFavoriteClick: (Song) -> Unit = {},
    onAddToQueueClick: (Song) -> Unit = {}
) {
    val context = LocalContext.current
    val screenBackground = MaterialTheme.colorScheme.background

    val artworkUrl = heroSong?.artworkUrl?.takeIf { it.isNotBlank() }
    val titleParts = remember(heroSong?.title) { splitTitle(heroSong?.title.orEmpty()) }
    val artistInfo = remember(heroSong?.artist) { parseArtists(heroSong?.artist.orEmpty(), limit = 3) }
    val infoLine = remember(heroSong?.id, titleParts) {
        val first = titleParts.sub
            ?: heroSong?.album?.let { splitTitle(it).main }?.takeIf { it.isNotBlank() && !it.equals(titleParts.main, ignoreCase = true) }
        listOfNotNull(first, heroSong?.year?.takeIf { it.isNotBlank() }).joinToString("  •  ")
    }

    // Local optimistic heart state (the repository call itself is unchanged).
    var liked by remember(heroSong?.id) { mutableStateOf(heroSong?.isLiked == true) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hero_banner")
    ) {
        val w = maxWidth
        val sideBySide = w >= 520.dp
        val isCompactNarrow = w < 360.dp
        val isCompact = w < 430.dp

        val bannerHeight = if (sideBySide) {
            (w * 0.72f).coerceIn(430.dp, 520.dp)
        } else {
            (w * 0.94f).coerceIn(370.dp, 450.dp)
        }
        val hPad = when {
            isCompactNarrow -> 14.dp
            isCompact -> 18.dp
            sideBySide -> 32.dp
            else -> 22.dp
        }
        val arcHeight = if (sideBySide) 52.dp else 104.dp
        val orbSize = if (isCompactNarrow) 48.dp else 56.dp
        val textMaxWidth = if (sideBySide) w * 0.56f else w - hPad * 2
        val separatorHeight = if (!sideBySide) 2.dp else if (isCompactNarrow) 24.dp else if (isCompact) 32.dp else 44.dp

        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bannerHeight)
                    .clipToBounds()
            ) {
                // 1. Adaptive cinematic background (artwork -> blur -> palette -> glow -> overlays)
                CinematicBackdrop(
                    artworkUrl = artworkUrl,
                    seed = heroSong?.id,
                    baseColor = screenBackground,
                    modifier = Modifier.matchParentSize()
                )

                // 2. Sharp artwork, feathered into the backdrop
                if (artworkUrl != null) {
                    Crossfade(
                        targetState = artworkUrl,
                        animationSpec = tween(500),
                        label = "hero_artwork_crossfade",
                        modifier = Modifier.matchParentSize()
                    ) { url ->
                        Box(Modifier.fillMaxSize()) {
                            if (sideBySide) {
                                val side = bannerHeight * 1.12f
                                FeatheredArtwork(
                                    url = url,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(side)
                                        .offset(x = side * 0.10f, y = -(side * 0.05f))
                                )
                            } else {
                                val side = w * 1.05f
                                FeatheredArtwork(
                                    url = url,
                                    wideView = true,
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .requiredSize(side)
                                        .offset(y = -(side * 0.09f))
                                )
                            }
                        }
                    }
                }

                // 3. Curved bottom edge in the app background colour (with a soft violet rim)
                if (sideBySide) {
                    BottomArc(
                        height = arcHeight,
                        background = screenBackground,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                } else {
                    PhoneWave(
                        height = arcHeight,
                        background = screenBackground,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                // 4. Foreground
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(
                            start = hPad,
                            end = hPad,
                            top = if (isCompact) 8.dp else 10.dp,
                            bottom = if (sideBySide) arcHeight * 0.8f else 12.dp
                        ),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    HeroTopBar(
                        context = context,
                        isCompactNarrow = isCompactNarrow,
                        isCompact = isCompact
                    )

                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        val titleBudget = (maxHeight - (if (sideBySide) 224.dp else 168.dp)).coerceAtLeast(60.dp)
                        val waveWidth = if (sideBySide) textMaxWidth else w - hPad * 2 - orbSize - 12.dp

                        if (!sideBySide) {
                            PhoneInfoColumn(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .widthIn(max = textMaxWidth),
                                mainTitle = titleParts.main.ifBlank { "Discover your vibe" },
                                infoLine = infoLine,
                                infoMaxWidth = w * 0.62f,
                                artists = artistInfo.first,
                                extraArtists = artistInfo.second,
                                isPlaying = isPlaying,
                                liked = liked,
                                titleMaxSize = if (isCompactNarrow) 42.sp else 50.sp,
                                titleMinSize = 26.sp,
                                titleMaxHeight = titleBudget,
                                compactNarrow = isCompactNarrow,
                                onPlayPause = onPlayPauseClick,
                                onLike = {
                                    heroSong?.let {
                                        liked = !liked
                                        onFavoriteClick(it)
                                    }
                                },
                                onQueue = {
                                    heroSong?.let {
                                        onAddToQueueClick(it)
                                        Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onShare = { heroSong?.let { shareSong(context, it, artistInfo.first) } }
                            )
                        } else {
                        HeroInfoColumn(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .widthIn(max = textMaxWidth),
                                mainTitle = titleParts.main.ifBlank { "Discover your vibe" },
                                infoLine = infoLine,
                                artists = artistInfo.first,
                                extraArtists = artistInfo.second,
                                isPlaying = isPlaying,
                                liked = liked,
                                titleMaxSize = when {
                                    isCompactNarrow -> 40.sp
                                    isCompact -> 48.sp
                                    sideBySide && w >= 700.dp -> 64.sp
                                    sideBySide -> 54.sp
                                    else -> 52.sp
                                },
                                titleMinSize = 26.sp,
                                titleMaxHeight = titleBudget,
                                waveWidth = waveWidth,
                                compactNarrow = isCompactNarrow,
                                onPlayPause = onPlayPauseClick,
                                onLike = {
                                    heroSong?.let {
                                        liked = !liked
                                        onFavoriteClick(it)
                                    }
                                },
                                onQueue = {
                                    heroSong?.let {
                                        onAddToQueueClick(it)
                                        Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onShare = { heroSong?.let { shareSong(context, it, artistInfo.first) } }
                            )
                        }
                    }
                }

                // Round play / pause orb (bottom end)
                if (sideBySide) PlayOrb(
                    isPlaying = isPlaying,
                    size = orbSize,
                    onClick = onPlayPauseClick,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = hPad, bottom = arcHeight * 0.8f - (orbSize - 34.dp) / 2)
                )
            }

            // Spacer matching the current app theme (unchanged spacing below the banner).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(separatorHeight)
                    .background(MaterialTheme.colorScheme.background)
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Top bar: VYBE logo + VYBE text + share button. Kept exactly as the previous design.
// -------------------------------------------------------------------------------------------------
@Composable
private fun HeroTopBar(
    context: Context,
    isCompactNarrow: Boolean,
    isCompact: Boolean
) {
    val vybeTitleFontSize = if (isCompactNarrow) 22.sp else if (isCompact) 25.sp else 28.sp
    val shareButtonSize = if (isCompactNarrow) 34.dp else 38.dp
    val shareIconSize = if (isCompactNarrow) 16.dp else 19.dp

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
            Image(
                painter = painterResource(id = R.drawable.vybe_logo),
                contentDescription = "VYBE logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(
                    if (isCompactNarrow) 44.dp
                    else if (isCompact) 50.dp
                    else 56.dp
                )
            )

            Text(
                text = "VYBE",
                color = Color.White,
                fontSize = vybeTitleFontSize,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = (-0.6).sp
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
}

// -------------------------------------------------------------------------------------------------
// Title / artists / actions / waveform
// -------------------------------------------------------------------------------------------------
@Composable
private fun HeroInfoColumn(
    modifier: Modifier,
    mainTitle: String,
    infoLine: String,
    artists: List<String>,
    extraArtists: Int,
    isPlaying: Boolean,
    liked: Boolean,
    titleMaxSize: TextUnit,
    titleMinSize: TextUnit,
    titleMaxHeight: Dp,
    waveWidth: Dp,
    compactNarrow: Boolean,
    onPlayPause: () -> Unit,
    onLike: () -> Unit,
    onQueue: () -> Unit,
    onShare: () -> Unit
) {
    val iconButtonSize = if (compactNarrow) 38.dp else 44.dp

    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        // Badge
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(
                        listOf(BrandPurple.copy(alpha = 0.60f), BrandMagenta.copy(alpha = 0.34f))
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "Just Vibe",
                color = Color.White,
                fontSize = 12.sp,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(8.dp))

        // Title: script font, auto-shrinks (max 2 lines) so long names never overflow.
        FitScriptTitle(
            text = mainTitle,
            maxFontSize = titleMaxSize,
            minFontSize = titleMinSize,
            maxHeight = titleMaxHeight
        )

        TitleUnderline()

        Spacer(Modifier.height(8.dp))

        // Artists: max 3, wraps to 2 lines max, never overlaps anything.
        if (artists.isNotEmpty()) {
            val annotated = buildAnnotatedString {
                artists.forEachIndexed { index, name ->
                    if (index > 0) {
                        withStyle(SpanStyle(color = BrandMagenta)) { append("  •  ") }
                    }
                    append(name.uppercase())
                }
                if (extraArtists > 0) {
                    withStyle(SpanStyle(color = BrandMagenta)) { append("  +$extraArtists") }
                }
            }
            Text(
                text = annotated,
                color = Color.White.copy(alpha = 0.94f),
                fontSize = if (compactNarrow) 11.sp else 12.5.sp,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.3.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (infoLine.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = infoLine,
                color = Color.White.copy(alpha = 0.62f),
                fontSize = if (compactNarrow) 12.sp else 13.sp,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(14.dp))

        // Actions
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compactNarrow) 8.dp else 10.dp)
        ) {
            PlayNowButton(isPlaying = isPlaying, compactNarrow = compactNarrow, onClick = onPlayPause)

            RoundActionButton(
                size = iconButtonSize,
                onClick = onLike,
                contentDescription = if (liked) "Remove from favourites" else "Add to favourites"
            ) {
                Icon(
                    imageVector = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = if (liked) BrandPink else Color.White,
                    modifier = Modifier.size(21.dp)
                )
            }
            RoundActionButton(
                size = iconButtonSize,
                onClick = onQueue,
                contentDescription = "Add to queue"
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.PlaylistAdd,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            RoundActionButton(
                size = iconButtonSize,
                onClick = onShare,
                contentDescription = "Share song"
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        HeroWaveform(
            isPlaying = isPlaying,
            modifier = Modifier
                .width(waveWidth)
                .height(34.dp)
        )
    }
}

/** Phone layout: badge, title, artists, info line, then one row of actions. */
@Composable
private fun PhoneInfoColumn(
    modifier: Modifier,
    mainTitle: String,
    infoLine: String,
    infoMaxWidth: Dp,
    artists: List<String>,
    extraArtists: Int,
    isPlaying: Boolean,
    liked: Boolean,
    titleMaxSize: TextUnit,
    titleMinSize: TextUnit,
    titleMaxHeight: Dp,
    compactNarrow: Boolean,
    onPlayPause: () -> Unit,
    onLike: () -> Unit,
    onQueue: () -> Unit,
    onShare: () -> Unit
) {
    val iconButtonSize = if (compactNarrow) 36.dp else 40.dp
    var menuOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(
                        listOf(BrandPurple.copy(alpha = 0.60f), BrandMagenta.copy(alpha = 0.34f))
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "Just Vibe",
                color = Color.White,
                fontSize = 12.sp,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(4.dp))

        FitScriptTitle(
            text = mainTitle,
            maxFontSize = titleMaxSize,
            minFontSize = titleMinSize,
            maxHeight = titleMaxHeight
        )

        if (artists.isNotEmpty()) {
            val annotated = buildAnnotatedString {
                artists.forEachIndexed { index, name ->
                    if (index > 0) {
                        withStyle(SpanStyle(color = BrandMagenta)) { append("  •  ") }
                    }
                    append(name.uppercase())
                }
                if (extraArtists > 0) {
                    withStyle(SpanStyle(color = BrandMagenta)) { append("  +$extraArtists") }
                }
            }
            Text(
                text = annotated,
                color = Color.White.copy(alpha = 0.94f),
                fontSize = if (compactNarrow) 11.sp else 12.5.sp,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.3.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (infoLine.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                modifier = Modifier.widthIn(max = infoMaxWidth),
                text = infoLine,
                color = Color.White.copy(alpha = 0.62f),
                fontSize = if (compactNarrow) 12.sp else 13.sp,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Room for the glowing wave to run between the text and the buttons
        Spacer(Modifier.height(30.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compactNarrow) 8.dp else 10.dp)
        ) {
            PlayNowButton(isPlaying = isPlaying, compactNarrow = compactNarrow, onClick = onPlayPause)

            RoundActionButton(
                size = iconButtonSize,
                onClick = onLike,
                contentDescription = if (liked) "Remove from favourites" else "Add to favourites"
            ) {
                Icon(
                    imageVector = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = if (liked) BrandPink else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            RoundActionButton(
                size = iconButtonSize,
                onClick = onQueue,
                contentDescription = "Add to queue"
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.PlaylistAdd,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(21.dp)
                )
            }
            RoundActionButton(
                size = iconButtonSize,
                onClick = onShare,
                contentDescription = "Share song"
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }
            Box {
                RoundActionButton(
                    size = iconButtonSize,
                    onClick = { menuOpen = true },
                    contentDescription = "More options"
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Add to queue") },
                        onClick = {
                            menuOpen = false
                            onQueue()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share song") },
                        onClick = {
                            menuOpen = false
                            onShare()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Script title that shrinks until it fits in [maxHeight] with at most two lines,
 * then falls back to an ellipsis at [minFontSize].
 */
@Composable
private fun FitScriptTitle(
    text: String,
    maxFontSize: TextUnit,
    minFontSize: TextUnit,
    maxHeight: Dp
) {
    val measurer: TextMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val baseStyle = remember {
        TextStyle(
            fontFamily = GreatVibesFontFamily,
            fontWeight = FontWeight.Normal,
            color = Color.White,
            shadow = Shadow(
                color = Color(0x99000000),
                offset = Offset(0f, 4f),
                blurRadius = 14f
            )
        )
    }

    BoxWithConstraints {
        val widthPx = constraints.maxWidth
        val heightPx = with(density) { maxHeight.roundToPx() }

        val fontSize = remember(text, widthPx, heightPx, maxFontSize, minFontSize) {
            var size = maxFontSize.value
            var result = minFontSize.value
            while (size >= minFontSize.value) {
                val layout = measurer.measure(
                    text = text,
                    style = baseStyle.copy(fontSize = size.sp),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2,
                    constraints = Constraints(maxWidth = widthPx)
                )
                if (!layout.hasVisualOverflow && layout.size.height <= heightPx) {
                    result = size
                    break
                }
                size -= 2f
            }
            result.sp
        }

        Text(
            text = text,
            style = baseStyle,
            fontSize = fontSize,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TitleUnderline() {
    Canvas(
        modifier = Modifier
            .padding(start = 28.dp, top = 2.dp)
            .width(130.dp)
            .height(8.dp)
    ) {
        val path = Path().apply {
            moveTo(0f, size.height * 0.85f)
            quadraticBezierTo(size.width * 0.5f, size.height * 0.05f, size.width, size.height * 0.45f)
        }
        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                listOf(BrandPurple, BrandMagenta, BrandMagenta.copy(alpha = 0f))
            ),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun PlayNowButton(isPlaying: Boolean, compactNarrow: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .neonGlow(color = BrandPurple, radius = 14.dp, alpha = 0.55f, cornerRadius = 50.dp)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(BrandPurple, BrandMagenta)))
            .border(1.dp, Color.White.copy(alpha = 0.30f), shape)
            .pressScale(pressedScale = 0.94f) { onClick() }
            .padding(
                horizontal = if (compactNarrow) 16.dp else 22.dp,
                vertical = if (compactNarrow) 10.dp else 12.dp
            )
            .testTag("hero_play_button"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                label = "hero_play_icon"
            ) { playing ->
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(if (compactNarrow) 18.dp else 22.dp)
                )
            }
            Text(
                text = if (isPlaying) "Pause" else "Play Now",
                color = Color.White,
                fontSize = if (compactNarrow) 13.sp else 15.sp,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RoundActionButton(
    size: Dp,
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.28f))
            .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
            .pressScale(pressedScale = 0.90f) { onClick() }
            .testTag(contentDescription),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun PlayOrb(
    isPlaying: Boolean,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .neonGlow(color = BrandMagenta, radius = 18.dp, alpha = 0.50f, cornerRadius = size / 2)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF2A1450).copy(alpha = 0.92f), Color(0xFF120A26).copy(alpha = 0.92f))
                )
            )
            .border(
                2.dp,
                Brush.linearGradient(listOf(BrandPurple, BrandPink)),
                CircleShape
            )
            .pressScale(pressedScale = 0.92f) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
            label = "hero_orb_icon"
        ) { playing ->
            Icon(
                imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(size * 0.50f)
            )
        }
    }
}

/**
 * Decorative waveform. Bars move only while music is playing (frame loop is cancelled when paused),
 * and all per-frame work happens in the draw phase, so it never causes recomposition.
 */
@Composable
private fun HeroWaveform(isPlaying: Boolean, modifier: Modifier = Modifier) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                withFrameMillis { time = it / 1000f }
            }
        }
    }
    val amplitude by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.30f,
        animationSpec = tween(400),
        label = "hero_wave_amp"
    )

    Canvas(modifier = modifier) {
        val bars = 46
        val step = size.width / bars
        val cy = size.height / 2f
        val maxHalf = size.height / 2f
        val strokeW = 2.2.dp.toPx()
        val t = time
        val amp = amplitude

        for (i in 0 until bars) {
            val n = i / (bars - 1f)
            val envelope = exp(-((n - 0.40f) * (n - 0.40f)) / 0.05f)
            val irregular = 0.45f + 0.55f * (((i * 37) % 11) / 10f)
            val motion = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * 5.2f + i * 0.72f))
            val half = maxHalf * (0.05f + envelope * irregular * motion * amp).coerceIn(0.05f, 1f)
            val x = step * (i + 0.5f)
            val color = lerp(BrandPurple, BrandPink, n)
                .copy(alpha = (0.30f + 0.70f * envelope).coerceIn(0f, 1f))
            drawLine(
                color = color,
                start = Offset(x, cy - half),
                end = Offset(x, cy + half),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Artwork + bottom edge
// -------------------------------------------------------------------------------------------------

/** Sharp artwork whose edges fade out, so it merges into the blurred backdrop with no boundary. */
@Composable
private fun FeatheredArtwork(url: String, modifier: Modifier = Modifier, wideView: Boolean = false) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .size(768, 768)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // Left + right feather
                drawRect(
                    brush = if (wideView) {
                        Brush.horizontalGradient(
                            0f to Color.Transparent,
                            0.07f to Color.Black,
                            0.93f to Color.Black,
                            1f to Color.Transparent
                        )
                    } else {
                        Brush.horizontalGradient(
                            0f to Color.Transparent,
                            0.30f to Color.Black,
                            0.80f to Color.Black,
                            1f to Color.Transparent
                        )
                    },
                    blendMode = BlendMode.DstIn
                )
                // Top + bottom feather (also hides any text printed on the cover's lower half)
                drawRect(
                    brush = if (wideView) {
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.14f to Color.Black,
                            0.48f to Color.Black,
                            0.68f to Color.Transparent
                        )
                    } else {
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.10f to Color.Black,
                            0.42f to Color.Black,
                            0.64f to Color.Transparent
                        )
                    },
                    blendMode = BlendMode.DstIn
                )
            }
    )
}

/**
 * Premium glowing wave for the phone layout: a violet haze that melts into the app background,
 * a bright swelling rim (soft glow built from stacked strokes) and two thinner ribbons beneath it.
 * The rim stays above the button row, like in the design mockup.
 */
@Composable
private fun PhoneWave(height: Dp, background: Color, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val w = size.width
        val h = size.height

        fun ribbon(dy: Float): Path = Path().apply {
            moveTo(0f, h * 0.31f + dy)
            cubicTo(w * 0.15f, h * 0.38f + dy, w * 0.32f, h * 0.40f + dy, w * 0.48f, h * 0.33f + dy)
            cubicTo(w * 0.64f, h * 0.26f + dy, w * 0.74f, h * 0.06f + dy, w * 0.90f, h * 0.06f + dy)
            cubicTo(w * 0.95f, h * 0.06f + dy, w * 0.98f, h * 0.07f + dy, w, h * 0.08f + dy)
        }

        // Light bloom above the swell
        drawRect(
            brush = Brush.radialGradient(
                0f to BrandMagenta.copy(alpha = 0.30f),
                0.6f to BrandPurple.copy(alpha = 0.10f),
                1f to Color.Transparent,
                center = Offset(w * 0.86f, h * 0.10f),
                radius = w * 0.60f
            )
        )

        // Violet haze under the rim, fading into the screen background
        val filled = ribbon(0f).apply {
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = filled,
            brush = Brush.verticalGradient(
                0f to BrandPurple.copy(alpha = 0.46f),
                0.38f to Color(0xFF3A1673).copy(alpha = 0.60f),
                0.78f to background.copy(alpha = 0.94f),
                1f to background
            )
        )

        val rimBrush = Brush.horizontalGradient(
            0f to Color.Transparent,
            0.07f to BrandPurple.copy(alpha = 0.85f),
            0.50f to BrandMagenta,
            0.86f to Color(0xFFE2B8FF),
            1f to Color.Transparent
        )
        val pinkBrush = Brush.horizontalGradient(
            0f to Color.Transparent,
            0.10f to BrandMagenta.copy(alpha = 0.7f),
            0.60f to BrandPink,
            1f to Color.Transparent
        )

        // Soft glow = a few wide, faint strokes under the sharp core line
        listOf(18.dp to 0.06f, 11.dp to 0.10f, 6.dp to 0.18f).forEach { (width, alpha) ->
            drawPath(ribbon(0f), brush = rimBrush, alpha = alpha, style = Stroke(width = width.toPx(), cap = StrokeCap.Round))
        }
        drawPath(ribbon(0f), brush = rimBrush, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))

        // Secondary ribbons for depth
        drawPath(ribbon(7.dp.toPx()), brush = pinkBrush, alpha = 0.55f, style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round))
        drawPath(ribbon(14.dp.toPx()), brush = rimBrush, alpha = 0.25f, style = Stroke(width = 0.8.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun BottomArc(height: Dp, background: Color, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val w = size.width
        val h = size.height

        fun curve(): Path = Path().apply {
            moveTo(0f, h * 0.38f)
            cubicTo(w * 0.30f, h * 1.05f, w * 0.64f, h * 0.90f, w, h * 0.10f)
        }

        val filled = curve().apply {
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(filled, color = background)

        val rimBrush = Brush.horizontalGradient(
            0f to Color.Transparent,
            0.30f to BrandPurple.copy(alpha = 0.85f),
            0.65f to BrandMagenta.copy(alpha = 0.90f),
            1f to Color.Transparent
        )
        drawPath(curve(), brush = rimBrush, alpha = 0.25f, style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round))
        drawPath(curve(), brush = rimBrush, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

// -------------------------------------------------------------------------------------------------
// Sharing (app share is unchanged)
// -------------------------------------------------------------------------------------------------
private fun shareSong(context: Context, song: Song, artists: List<String>) {
    val by = artists.joinToString(", ").ifBlank { song.artist }
    val message = "🎵 ${song.title}" +
        (if (by.isNotBlank()) " — $by" else "") +
        "\n\nListening on VYBE — ad-free music made for your vibe.\nhttps://t.me/VyomaOfficial"

    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, message)
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(sendIntent, "Share song via"))
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
