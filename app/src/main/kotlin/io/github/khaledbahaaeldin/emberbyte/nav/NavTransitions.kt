@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.khaledbahaaeldin.emberbyte.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme

/**
 * Tab-to-tab transitions come from the motion scheme (never Navigation's hand-written 700 ms default).
 * With reduced motion, tabs switch instantly.
 */
internal fun tabEnterTransition(motion: MotionScheme, reduceMotion: Boolean): EnterTransition =
    if (reduceMotion) EnterTransition.None else fadeIn(motion.defaultEffectsSpec())

internal fun tabExitTransition(motion: MotionScheme, reduceMotion: Boolean): ExitTransition =
    if (reduceMotion) ExitTransition.None else fadeOut(motion.fastEffectsSpec())
