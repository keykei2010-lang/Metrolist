package com.metrolist.music.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class PeakLimiterTest {
    @Test fun `full level boosted sine retains its waveform rather than hard clipping`() {
        val input = FloatArray(48000) { (0.95 * sin(2 * PI * 1000 * it / 48000)).toFloat() }
        val output = render(input, channels = 1, chunkFrames = 1024)
        assertEquals(input.size, output.size)
        assertTrue(output.all { abs(it) <= LookAheadPeakLimiter.CEILING + 0.000001 })
        var xy = 0.0
        var xx = 0.0
        for (i in 1000 until 40000) {
            xy += input[i] * output[i]
            xx += input[i] * input[i]
        }
        val gain = xy / xx
        var error = 0.0
        var energy = 0.0
        for (i in 1000 until 40000) {
            error += (output[i] - input[i] * gain).pow(2)
            energy += output[i] * output[i]
        }
        assertTrue("Waveform error ratio ${sqrt(error / energy)}", sqrt(error / energy) < 0.00001)
    }

    @Test fun `quiet material receives the requested boost`() {
        val input = FloatArray(6000) { (0.05 * sin(2 * PI * 440 * it / 48000)).toFloat() }
        val output = render(input, channels = 1, chunkFrames = 211)
        val requested = 10.0.pow(12.0 / 20)
        for (i in input.indices) assertEquals(input[i] * requested, output[i].toDouble(), 0.000001)
    }

    @Test fun `stereo limiter preserves channel balance and is independent of buffer size`() {
        val input = FloatArray(24000) { i ->
            val sample = 0.95 * sin(2 * PI * 1000 * (i / 2) / 48000)
            (if (i % 2 == 0) sample else sample / 4).toFloat()
        }
        val small = render(input, channels = 2, chunkFrames = 31)
        val large = render(input, channels = 2, chunkFrames = 4096)
        assertArrayEquals(large, small, 0f)
        assertEquals(input.size, small.size)
        for (i in small.indices step 2) assertEquals(small[i] / 4, small[i + 1], 0.000001f)
    }

    @Test fun `limiter anticipates a peak then restores quiet passage gain gradually`() {
        val input = FloatArray(60000) { 0.05f }
        val peak = 10000
        input[peak] = 1f
        val output = render(input, channels = 1, chunkFrames = 173)
        assertEquals(LookAheadPeakLimiter.CEILING, output[peak].toDouble(), 0.000001)
        assertTrue(output[peak - 1] < output[peak - 240])
        for (i in peak - 239 until peak - 1) assertTrue(abs(output[i + 1] - output[i]) < 0.002f)
        assertTrue(output[peak + 1] < output[peak + 1000])
        assertTrue(output[peak + 1000] < output[peak + 20000])
        assertTrue(output[peak + 40000] > 0.19f)
    }

    @Test fun `zero boost drains pending audio unboosted without losing frames`() {
        val processor = processor(1)
        processor.boostDb = 12f
        processor.queueInput(buffer(FloatArray(40) { 0.1f }))
        assertFalse(processor.output.hasRemaining())
        processor.boostDb = 0f
        processor.queueInput(buffer(FloatArray(20) { 0.2f }))
        val output = floats(processor.output)
        assertArrayEquals(FloatArray(40) { 0.1f } + FloatArray(20) { 0.2f }, output, 0f)
        processor.queueEndOfStream()
        assertFalse(processor.output.hasRemaining())
        assertTrue(processor.isEnded)
    }

    @Test fun `seek flush removes delayed samples and limiter gain history`() {
        val processor = processor(1)
        processor.boostDb = 12f
        processor.queueInput(buffer(FloatArray(80) { 0.99f }))
        processor.output
        processor.flush()
        processor.queueInput(buffer(FloatArray(80) { 0.05f }))
        assertFalse(processor.output.hasRemaining())
        processor.queueEndOfStream()
        val output = floats(processor.output)
        assertEquals(80, output.size)
        output.forEach { assertEquals(0.05f * 10.0.pow(12.0 / 20).toFloat(), it, 0.000001f) }
    }

    @Test fun `gain changes while samples are buffered cannot exceed the ceiling`() {
        val processor = processor(1)
        processor.boostDb = 3f
        processor.queueInput(buffer(FloatArray(100) { 0.9f }))
        assertFalse(processor.output.hasRemaining())
        processor.boostDb = 12f
        processor.queueInput(buffer(FloatArray(1000) { -0.9f }))
        val first = floats(processor.output)
        processor.queueEndOfStream()
        val output = first + floats(processor.output)
        assertEquals(1100, output.size)
        assertTrue(output.all { it.isFinite() && abs(it) <= LookAheadPeakLimiter.CEILING + 0.000001 })
    }

    @Test fun `nonfinite floating point input cannot poison later samples`() {
        val output = render(floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0.05f), 1, 4)
        assertTrue(output.all { it.isFinite() })
        assertEquals(0.05f * 10.0.pow(12.0 / 20).toFloat(), output.last(), 0.000001f)
    }

    @Test fun `unboosted PCM bypass is bit exact and has no lookahead delay`() {
        for ((encoding, bytes) in listOf(C.ENCODING_PCM_16BIT to 2, C.ENCODING_PCM_24BIT to 3,
            C.ENCODING_PCM_32BIT to 4, C.ENCODING_PCM_FLOAT to 4)) {
            val processor = VolumeNormalizationAudioProcessor()
            processor.configure(AudioProcessor.AudioFormat(44100, 2, encoding))
            processor.flush()
            val data = ByteArray(bytes * 2 * 64) { (it * 17).toByte() }
            processor.queueInput(ByteBuffer.allocateDirect(data.size).apply { put(data); flip() })
            assertArrayEquals(data, processor.output.let { ByteArray(it.remaining()).also(it::get) })
            processor.queueEndOfStream()
            assertFalse(processor.output.hasRemaining())
        }
    }

    @Test fun `boosted processing reuses its direct output buffer`() {
        val processor = processor(2)
        processor.boostDb = 12f
        processor.queueInput(buffer(FloatArray(2048) { 0.05f }))
        val first = processor.output
        first.position(first.limit())
        processor.queueInput(buffer(FloatArray(2048) { 0.05f }))
        assertSame(first, processor.output)
    }

    @Test fun `configuring the next format does not discard the previous song tail`() {
        val processor = processor(1)
        processor.boostDb = 12f
        processor.queueInput(buffer(FloatArray(80) { 0.05f }))
        processor.output
        processor.configure(AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT))
        processor.queueEndOfStream()
        val tail = floats(processor.output)
        assertEquals(80, tail.size)
        tail.forEach { assertEquals(0.05f * 10.0.pow(12.0 / 20).toFloat(), it, 0.000001f) }
        processor.flush()
        val input = ByteBuffer.allocateDirect(4).order(ByteOrder.LITTLE_ENDIAN)
            .apply { putShort(1000); putShort(-1000); flip() }
        processor.queueInput(input)
        processor.output
        processor.queueEndOfStream()
        val output = processor.output.order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(4, output.remaining())
        assertEquals(3981, output.short.toInt())
        assertEquals(-3981, output.short.toInt())
    }

    private fun render(input: FloatArray, channels: Int, chunkFrames: Int): FloatArray {
        val processor = processor(channels)
        processor.boostDb = 12f
        val bytes = ByteArrayOutputStream()
        var offset = 0
        while (offset < input.size) {
            val end = min(input.size, offset + chunkFrames * channels)
            processor.queueInput(buffer(input.copyOfRange(offset, end)))
            processor.output.let { bytes.write(ByteArray(it.remaining()).also(it::get)) }
            offset = end
        }
        processor.queueEndOfStream()
        processor.output.let { bytes.write(ByteArray(it.remaining()).also(it::get)) }
        assertTrue(processor.isEnded)
        return floats(ByteBuffer.wrap(bytes.toByteArray()))
    }

    private fun processor(channels: Int) = VolumeNormalizationAudioProcessor().apply {
        configure(AudioProcessor.AudioFormat(48000, channels, C.ENCODING_PCM_FLOAT))
        flush()
    }

    private fun buffer(samples: FloatArray): ByteBuffer = ByteBuffer.allocateDirect(samples.size * 4)
        .order(ByteOrder.LITTLE_ENDIAN).apply { samples.forEach { putFloat(it) }; flip() }

    private fun floats(buffer: ByteBuffer): FloatArray = buffer.order(ByteOrder.LITTLE_ENDIAN)
        .let { FloatArray(it.remaining() / 4) { _ -> it.float } }
}
