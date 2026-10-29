package com.metrolist.music.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerColorExtractorTest {
    @Test fun `artwork gradients keep white labels readable even on white covers`() {
        listOf(Color.White, Color.Black, Color.Red, Color.Green, Color.Blue, Color(0xFFFFB3C1)).forEach { seed ->
            PlayerColorExtractor.gradientColorsFromSeed(seed).forEach { background ->
                val contrast = 1.05f / (background.luminance() + 0.05f)
                assertTrue("White text contrast for $seed is $contrast", contrast >= 4.5f)
            }
        }
    }
}
