/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette

object PlayerColorExtractor {
    fun extractGradientColors(
        palette: Palette,
        fallbackColor: Int,
    ): List<Color> {
        return gradientColorsFromSeed(Color(palette.rankedColors(1, fallbackColor).first()))
    }

    internal fun gradientColorsFromSeed(seed: Color): List<Color> {
        // White player text must remain readable even when the artwork is almost white.
        val primaryColor = Color(seed.red * 0.42f, seed.green * 0.42f, seed.blue * 0.42f)
        return listOf(
            primaryColor,
            primaryColor.copy(
                red = primaryColor.red * 0.6f,
                green = primaryColor.green * 0.6f,
                blue = primaryColor.blue * 0.6f,
            ),
            Color.Black,
        )
    }
}
