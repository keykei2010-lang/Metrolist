package com.metrolist.music.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

@UnstableApi
class VolumeNormalizationAudioProcessor : BaseAudioProcessor() {
    private var encoding = C.ENCODING_INVALID
    private var bytesPerSample = 0
    private var channels = 0
    private var sampleRate = 0
    private var frame = DoubleArray(0)
    private var limiter: LookAheadPeakLimiter? = null

    @Volatile var enabled = false

    @Volatile
    var boostDb: Float = 0f
        set(value) { field = if (value.isFinite()) value.coerceIn(0f, 12f) else 0f }

    @Volatile private var targetGainMb = 0

    fun setTargetGain(gainMb: Int) {
        targetGainMb = gainMb
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> Unit
            else -> throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        Timber.tag(TAG).d("Configured: $inputAudioFormat")
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val limiter = checkNotNull(limiter)
        val boost = boostDb
        val gain = requestedGain(boost)
        val frameBytes = channels * bytesPerSample
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        if (boost > 0f) {
            val output = replaceOutputBuffer(inputBuffer.remaining()).order(ByteOrder.LITTLE_ENDIAN)
            repeat(inputBuffer.remaining() / frameBytes) {
                for (channel in 0 until channels) frame[channel] = readSample(inputBuffer)
                limiter.enqueue(frame)
                if (limiter.bufferedFrames > limiter.lookAheadFrames) {
                    limiter.dequeue(frame, gain)
                    for (channel in 0 until channels) writeSample(output, frame[channel])
                }
            }
            output.flip()
        } else {
            val output = replaceOutputBuffer(inputBuffer.remaining() + limiter.bufferedFrames * frameBytes)
                .order(ByteOrder.LITTLE_ENDIAN)
            // On disconnect or 0 dB, emit pending audio unboosted before passing through new audio.
            while (limiter.bufferedFrames > 0) {
                limiter.dequeue(frame, gain, limit = false)
                for (channel in 0 until channels) writeSample(output, frame[channel])
            }
            limiter.reset()
            if (gain == 1.0) output.put(inputBuffer)
            else repeat(inputBuffer.remaining() / bytesPerSample) { writeSample(output, readSample(inputBuffer) * gain) }
            output.flip()
        }
    }

    override fun onQueueEndOfStream() {
        val limiter = limiter ?: return
        if (limiter.bufferedFrames == 0) return
        val boost = boostDb
        val gain = requestedGain(boost)
        val output = replaceOutputBuffer(limiter.bufferedFrames * channels * bytesPerSample).order(ByteOrder.LITTLE_ENDIAN)
        while (limiter.bufferedFrames > 0) {
            limiter.dequeue(frame, gain, limit = boost > 0f)
            for (channel in 0 until channels) writeSample(output, frame[channel])
        }
        output.flip()
    }

    private fun requestedGain(boost: Float) = 10.0.pow(((if (enabled) targetGainMb else 0) + boost * 100.0) / 2000.0)

    private fun readSample(input: ByteBuffer): Double = when (encoding) {
        C.ENCODING_PCM_16BIT -> input.short / 32768.0
        C.ENCODING_PCM_24BIT -> {
            val low = input.get().toInt() and 0xFF
            val middle = input.get().toInt() and 0xFF
            ((input.get().toInt() shl 16) or (middle shl 8) or low) / 8388608.0
        }
        C.ENCODING_PCM_32BIT -> input.int / 2147483648.0
        C.ENCODING_PCM_FLOAT -> input.float.toDouble()
        else -> error("Audio processor is not configured")
    }

    private fun writeSample(output: ByteBuffer, sample: Double) {
        when (encoding) {
            C.ENCODING_PCM_16BIT -> output.putShort((sample * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
            C.ENCODING_PCM_24BIT -> {
                val value = (sample * 8388608.0).coerceIn(-8388608.0, 8388607.0).toInt()
                output.put(value.toByte()).put((value shr 8).toByte()).put((value shr 16).toByte())
            }
            C.ENCODING_PCM_32BIT -> output.putInt((sample * 2147483648.0).coerceIn(-2147483648.0, 2147483647.0).toLong().toInt())
            C.ENCODING_PCM_FLOAT -> output.putFloat(sample.coerceIn(-1.0, 1.0).toFloat())
        }
    }

    override fun onFlush() {
        // configure() may describe the next song before the old song's delayed tail is drained.
        if (inputAudioFormat == AudioProcessor.AudioFormat.NOT_SET) return
        if (encoding != inputAudioFormat.encoding || channels != inputAudioFormat.channelCount ||
            sampleRate != inputAudioFormat.sampleRate) {
            encoding = inputAudioFormat.encoding
            bytesPerSample = when (encoding) {
                C.ENCODING_PCM_16BIT -> 2
                C.ENCODING_PCM_24BIT -> 3
                else -> 4
            }
            channels = inputAudioFormat.channelCount
            sampleRate = inputAudioFormat.sampleRate
            frame = DoubleArray(channels)
            limiter = LookAheadPeakLimiter(sampleRate, channels)
        } else limiter?.reset()
    }

    override fun onReset() {
        limiter = null
        encoding = C.ENCODING_INVALID
        bytesPerSample = 0
        channels = 0
        sampleRate = 0
        frame = DoubleArray(0)
    }

    private companion object {
        const val TAG = "VolumeNormalizationProcessor"
    }
}
