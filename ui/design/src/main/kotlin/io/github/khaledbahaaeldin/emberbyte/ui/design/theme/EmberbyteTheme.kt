@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.khaledbahaaeldin.emberbyte.ui.design.theme

import android.annotation.SuppressLint
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private const val DYNAMIC_COLOR_MIN_SDK = 31

/** True when the user turned system animations off. Springs become snaps and fades. */
val LocalReduceMotion: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

internal fun isReduceMotion(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

internal fun resolveColorScheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    amoledBlack: Boolean,
    sdkInt: Int,
    dynamicLight: () -> ColorScheme,
    dynamicDark: () -> ColorScheme,
): ColorScheme {
    val base = when {
        dynamicColor && sdkInt >= DYNAMIC_COLOR_MIN_SDK -> if (darkTheme) dynamicDark() else dynamicLight()
        darkTheme -> EmberDarkColors
        else -> EmberLightColors
    }
    return if (darkTheme && amoledBlack) {
        base.copy(background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black)
    } else {
        base
    }
}

// The dynamic lambdas are only invoked when resolveColorScheme sees sdkInt >= 31; lint cannot see through it.
@SuppressLint("NewApi")
@Composable
fun EmberbyteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    amoledBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = resolveColorScheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        amoledBlack = amoledBlack,
        sdkInt = Build.VERSION.SDK_INT,
        dynamicLight = { dynamicLightColorScheme(context) },
        dynamicDark = { dynamicDarkColorScheme(context) },
    )
    val reduceMotion = remember(context) {
        isReduceMotion(
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
        )
    }
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}
