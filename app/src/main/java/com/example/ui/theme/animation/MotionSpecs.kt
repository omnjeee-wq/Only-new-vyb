package com.example.ui.theme.animation

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Standardized Apple-inspired animation tokens and motion specifications for VYBE.
 * Prioritizes natural physics-based spring curves for interactive elements and
 * clean easing for layout and screen transitions.
 */
object MotionSpecs {
    // -------------------------------------------------------------
    // 1. Duration Tokens (Milliseconds)
    // -------------------------------------------------------------
    const val MicroDuration = 150       // Toggles, button taps, icon scales
    const val ComponentDuration = 240   // Card expansions, sheets, popups
    const val ScreenDuration = 300      // Full screen crossfades and tab switches
    const val ModalDuration = 350       // Bottom sheets, dialogs

    // -------------------------------------------------------------
    // 2. Physics-Based Spring Specifications
    // -------------------------------------------------------------
    /**
     * Apple-style fluid interactive spring with natural slight bounce for buttons,
     * pills, and touch micro-interactions.
     */
    fun <T> interactiveSpring(): AnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    /**
     * Non-bouncy, refined, damped spring for layout shifts, content resizing,
     * and subtle element position transitions.
     */
    fun <T> smoothSpring(): AnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /**
     * Snappy spring for quick icon state changes (e.g. Play -> Pause, Like toggle).
     */
    fun <T> snappySpring(): AnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessHigh
    )

    // -------------------------------------------------------------
    // 3. Easing Curves
    // -------------------------------------------------------------
    fun <T> fastOutSlowIn(durationMs: Int = ComponentDuration): AnimationSpec<T> = tween(
        durationMillis = durationMs,
        easing = FastOutSlowInEasing
    )

    fun <T> linearOutSlowIn(durationMs: Int = ComponentDuration): AnimationSpec<T> = tween(
        durationMillis = durationMs,
        easing = LinearOutSlowInEasing
    )
}

/**
 * Reusable press-scale micro-interaction modifier.
 * Creates an Apple Music-like tactile response when pressed: smoothly scales down
 * to [pressedScale] while held, then springs back on release.
 */
fun Modifier.pressScale(
    pressedScale: Float = 0.96f,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = if (isPressed) {
            spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioNoBouncy)
        } else {
            spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioMediumBouncy)
        },
        label = "press_scale_animation"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null, // Custom scale replaces default heavy ripple
                    onClick = onClick
                )
            } else {
                Modifier.pointerInput(Unit) {
                    while (true) {
                        awaitPointerEventScope {
                            awaitFirstDown(requireUnconsumed = false)
                            isPressed = true
                            waitForUpOrCancellation()
                            isPressed = false
                        }
                    }
                }
            }
        )
}

/**
 * Reusable card bounce click feedback modifier.
 * Combines pointer press tracking with click callback.
 */
fun Modifier.bounceClick(
    pressedScale: Float = 0.95f,
    onClick: () -> Unit
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bounce_scale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            while (true) {
                awaitPointerEventScope {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    val up = waitForUpOrCancellation()
                    isPressed = false
                    if (up != null) {
                        onClick()
                    }
                }
            }
        }
}
