package com.metrolist.music.playback.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** Linked-channel sample-peak limiter with 5 ms look-ahead and a 150 ms release. */
internal class LookAheadPeakLimiter(sampleRate: Int, private val channels: Int) {
    val lookAheadFrames = max(1, sampleRate / 200)
    private val capacity = lookAheadFrames + 1
    private val samples = DoubleArray(capacity * channels)
    private val framePeaks = DoubleArray(capacity)
    private val maxPositions = LongArray(capacity)
    private val maxValues = DoubleArray(capacity)
    private val release = exp(-1.0 / (sampleRate * 0.150))
    private var maxHead = 0
    private var maxCount = 0
    private var readPosition = 0L
    private var writePosition = 0L
    private var gain = 1.0
    private var attackStep = 0.0
    private var started = false

    val bufferedFrames: Int get() = (writePosition - readPosition).toInt()

    fun enqueue(frame: DoubleArray) {
        check(bufferedFrames < capacity)
        val slot = (writePosition % capacity).toInt()
        var peak = 0.0
        for (channel in 0 until channels) {
            val sample = frame[channel].takeIf { it.isFinite() } ?: 0.0
            samples[slot * channels + channel] = sample
            peak = max(peak, abs(sample))
        }
        framePeaks[slot] = peak
        // A monotonic deque keeps the future peak lookup O(1), without scanning each window.
        while (maxCount > 0 && maxValues[(maxHead + maxCount - 1) % capacity] <= peak) maxCount--
        val tail = (maxHead + maxCount) % capacity
        maxValues[tail] = peak
        maxPositions[tail] = writePosition
        maxCount++
        writePosition++
    }

    fun dequeue(frame: DoubleArray, requestedGain: Double, limit: Boolean = true) {
        check(bufferedFrames > 0)
        val slot = (readPosition % capacity).toInt()
        var multiplier = requestedGain
        if (limit) {
            val futurePeak = maxValues[maxHead] * requestedGain
            val target = if (futurePeak > CEILING) CEILING / futurePeak else 1.0
            if (!started) {
                gain = target
                started = true
            } else if (target < gain) {
                // Never slow an attack already scheduled for an earlier peak.
                attackStep = max(attackStep, (gain - target) / lookAheadFrames)
                gain = max(target, gain - attackStep)
            } else {
                attackStep = 0.0
                gain = min(target, 1.0 - (1.0 - gain) * release)
            }
            val outgoingPeak = framePeaks[slot] * requestedGain
            // Also protect against a gain-setting change while audio is already buffered.
            if (outgoingPeak > CEILING) gain = min(gain, CEILING / outgoingPeak)
            multiplier *= gain
        }
        for (channel in 0 until channels) frame[channel] = samples[slot * channels + channel] * multiplier
        if (maxCount > 0 && maxPositions[maxHead] == readPosition) {
            maxHead = (maxHead + 1) % capacity
            maxCount--
        }
        readPosition++
    }

    fun reset() {
        maxHead = 0
        maxCount = 0
        readPosition = 0
        writePosition = 0
        gain = 1.0
        attackStep = 0.0
        started = false
    }

    companion object {
        // -1 dBFS leaves headroom for downstream conversion; this is not a true-peak guarantee.
        const val CEILING = 0.8912509381337456
    }
}
