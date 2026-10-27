package com.metrolist.music.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidAutoBoostTest {
    @Test fun `boost works without normalization metadata and stops immediately when cleared`() {
        val processor = processor()
        processor.boostDb = 6f
        assertEquals(1995, sample(processor, 1000))
        processor.boostDb = 0f
        assertEquals(1000, sample(processor, 1000))
    }

    @Test fun `boost saturates rather than wrapping loud positive and negative samples`() {
        val processor = processor()
        processor.boostDb = 12f
        assertEquals(32767, sample(processor, 30000))
        assertEquals(-32768, sample(processor, -30000))
    }

    @Test fun `invalid boost cannot poison the audio pipeline`() {
        val processor = processor()
        processor.boostDb = Float.NaN
        assertEquals(1000, sample(processor, 1000))
        processor.boostDb = -12f
        assertEquals(1000, sample(processor, 1000))
        processor.boostDb = 100f
        assertEquals(12f, processor.boostDb, 0f)
    }

    @Test fun `boost combines with normalization rather than replacing it`() {
        val processor = processor()
        processor.setTargetGain(-600)
        processor.enabled = true
        processor.boostDb = 6f
        assertEquals(1000, sample(processor, 1000))
    }

    @Test fun `boost handles floating point PCM with saturation`() {
        val processor = VolumeNormalizationAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(48000, 1, C.ENCODING_PCM_FLOAT))
        processor.flush()
        processor.boostDb = 6f
        val input = ByteBuffer.allocateDirect(12).order(ByteOrder.LITTLE_ENDIAN)
        input.putFloat(0.1f).putFloat(0.9f).putFloat(-0.9f).flip()
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0.199526f, output.float, 0.000001f)
        assertEquals(1f, output.float, 0f)
        assertEquals(-1f, output.float, 0f)
    }

    @Test fun `boost saturates signed 32 bit PCM`() {
        val processor = VolumeNormalizationAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(48000, 1, C.ENCODING_PCM_32BIT))
        processor.flush()
        processor.boostDb = 12f
        val input = ByteBuffer.allocateDirect(8).order(ByteOrder.LITTLE_ENDIAN)
        input.putInt(Int.MAX_VALUE).putInt(Int.MIN_VALUE).flip()
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(Int.MAX_VALUE, output.int)
        assertEquals(Int.MIN_VALUE, output.int)
    }

    @Test fun `boost saturates signed 24 bit PCM`() {
        val processor = VolumeNormalizationAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(48000, 1, C.ENCODING_PCM_24BIT))
        processor.flush()
        processor.boostDb = 12f
        val input = ByteBuffer.allocateDirect(6).order(ByteOrder.LITTLE_ENDIAN)
        input.put(byteArrayOf(-1, -1, 127, 0, 0, -128)).flip()
        processor.queueInput(input)
        val output = processor.output
        val bytes = ByteArray(output.remaining()).also(output::get)
        org.junit.Assert.assertArrayEquals(byteArrayOf(-1, -1, 127, 0, 0, -128), bytes)
    }

    @Suppress("DEPRECATION")
    private fun processor() = VolumeNormalizationAudioProcessor().apply {
        configure(AudioProcessor.AudioFormat(48000, 1, C.ENCODING_PCM_16BIT))
        flush()
    }

    private fun sample(processor: VolumeNormalizationAudioProcessor, value: Int): Int {
        val buffer = ByteBuffer.allocateDirect(2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(value.toShort()).flip()
        processor.queueInput(buffer)
        return processor.output.order(ByteOrder.LITTLE_ENDIAN).short.toInt()
    }
}
