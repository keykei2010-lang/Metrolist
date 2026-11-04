package com.metrolist.music.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAutoBoostTest {
    @Test fun `boost works without normalization metadata and stops immediately when cleared`() {
        val processor = processor()
        processor.boostDb = 6f
        assertEquals(1995, sample(processor, 1000))
        processor.boostDb = 0f
        assertEquals(1000, sample(processor, 1000))
    }

    @Test fun `boost limits loud positive and negative samples below clipping`() {
        val processor = processor()
        processor.boostDb = 12f
        assertEquals(29204, sample(processor, 30000))
        assertEquals(-29204, sample(processor, -30000))
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

    @Test fun `boost limits floating point PCM without flattening its peaks`() {
        val processor = VolumeNormalizationAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(48000, 1, C.ENCODING_PCM_FLOAT))
        processor.flush()
        processor.boostDb = 6f
        val input = ByteBuffer.allocateDirect(12).order(ByteOrder.LITTLE_ENDIAN)
        input.putFloat(0.1f).putFloat(0.9f).putFloat(-0.9f).flip()
        val output = process(processor, input)
        assertEquals((LookAheadPeakLimiter.CEILING / 9).toFloat(), output.float, 0.000001f)
        assertEquals(LookAheadPeakLimiter.CEILING.toFloat(), output.float, 0.000001f)
        assertEquals(-LookAheadPeakLimiter.CEILING.toFloat(), output.float, 0.000001f)
    }

    @Test fun `boost limits signed 32 bit PCM`() {
        val processor = VolumeNormalizationAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(48000, 1, C.ENCODING_PCM_32BIT))
        processor.flush()
        processor.boostDb = 12f
        val input = ByteBuffer.allocateDirect(8).order(ByteOrder.LITTLE_ENDIAN)
        input.putInt(Int.MAX_VALUE).putInt(Int.MIN_VALUE).flip()
        val output = process(processor, input)
        val positive = output.int
        val negative = output.int
        assertTrue(positive > 0 && positive.toDouble() / 2147483648.0 <= LookAheadPeakLimiter.CEILING)
        assertTrue(negative < 0 && -negative.toDouble() / 2147483648.0 <= LookAheadPeakLimiter.CEILING)
    }

    @Test fun `boost limits signed 24 bit PCM`() {
        val processor = VolumeNormalizationAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(48000, 1, C.ENCODING_PCM_24BIT))
        processor.flush()
        processor.boostDb = 12f
        val input = ByteBuffer.allocateDirect(6).order(ByteOrder.LITTLE_ENDIAN)
        input.put(byteArrayOf(-1, -1, 127, 0, 0, -128)).flip()
        val output = process(processor, input)
        fun read24(): Int {
            val low = output.get().toInt() and 255
            val middle = output.get().toInt() and 255
            return (output.get().toInt() shl 16) or (middle shl 8) or low
        }
        val positive = read24()
        val negative = read24()
        assertTrue(positive > 0 && positive / 8388608.0 <= LookAheadPeakLimiter.CEILING)
        assertTrue(negative < 0 && -negative / 8388608.0 <= LookAheadPeakLimiter.CEILING)
    }

    @Suppress("DEPRECATION")
    private fun processor() = VolumeNormalizationAudioProcessor().apply {
        configure(AudioProcessor.AudioFormat(48000, 1, C.ENCODING_PCM_16BIT))
        flush()
    }

    private fun sample(processor: VolumeNormalizationAudioProcessor, value: Int): Int {
        val buffer = ByteBuffer.allocateDirect(2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(value.toShort()).flip()
        return process(processor, buffer).short.toInt()
    }

    private fun process(processor: VolumeNormalizationAudioProcessor, input: ByteBuffer): ByteBuffer {
        processor.queueInput(input)
        val first = processor.output.let { ByteArray(it.remaining()).also(it::get) }
        processor.queueEndOfStream()
        val tail = processor.output.let { ByteArray(it.remaining()).also(it::get) }
        processor.flush()
        return ByteBuffer.wrap(first + tail).order(ByteOrder.LITTLE_ENDIAN)
    }
}
