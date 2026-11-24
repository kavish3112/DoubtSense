package com.example.audio

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Pure Kotlin PCM-to-WAV encoder and real-time audio amplitude/RMS utilities
 * used when capturing live microphone audio via AudioRecord.
 */
object AudioWavEncoder {

    const val DEFAULT_SAMPLE_RATE = 16_000
    const val CHANNELS_MONO = 1
    const val BITS_PER_SAMPLE = 16

    /**
     * Computes normalized RMS energy (0.0f..1.0f) from 16-bit signed PCM samples.
     */
    fun computeNormalizedRms(samples: ShortArray, sampleCount: Int): Float {
        if (sampleCount <= 0 || samples.isEmpty()) return 0f
        val count = sampleCount.coerceAtMost(samples.size)
        var sumSquares = 0.0
        for (i in 0 until count) {
            val normalized = samples[i].toDouble() / 32768.0
            sumSquares += normalized * normalized
        }
        val rms = sqrt(sumSquares / count.toDouble()).toFloat()
        // Boost typical speech RMS (~0.02..0.25) smoothly into visual range 0..1
        return (rms * 4.5f).coerceIn(0f, 1f)
    }

    /**
     * Converts little-endian 16-bit PCM sample array into a byte array.
     */
    fun shortsToLittleEndianBytes(samples: ShortArray, sampleCount: Int = samples.size): ByteArray {
        val count = sampleCount.coerceIn(0, samples.size)
        val buffer = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) {
            buffer.putShort(samples[i])
        }
        return buffer.array()
    }

    /**
     * Prepends a valid 44-byte RIFF/WAVE PCM header to raw 16-bit PCM audio bytes.
     */
    fun encodePcm16ToWav(
        pcmData: ByteArray,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        channels: Int = CHANNELS_MONO
    ): ByteArray {
        val byteRate = sampleRate * channels * (BITS_PER_SAMPLE / 8)
        val blockAlign = channels * (BITS_PER_SAMPLE / 8)
        val totalAudioLen = pcmData.size
        val totalDataLen = totalAudioLen + 36

        val out = ByteArrayOutputStream(44 + totalAudioLen)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF chunk descriptor
        header.put('R'.code.toByte())
        header.put('I'.code.toByte())
        header.put('F'.code.toByte())
        header.put('F'.code.toByte())
        header.putInt(totalDataLen)
        header.put('W'.code.toByte())
        header.put('A'.code.toByte())
        header.put('V'.code.toByte())
        header.put('E'.code.toByte())

        // fmt sub-chunk
        header.put('f'.code.toByte())
        header.put('m'.code.toByte())
        header.put('t'.code.toByte())
        header.put(' '.code.toByte())
        header.putInt(16) // PCM sub-chunk size
        header.putShort(1.toShort()) // AudioFormat = 1 (PCM)
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(BITS_PER_SAMPLE.toShort())

        // data sub-chunk
        header.put('d'.code.toByte())
        header.put('a'.code.toByte())
        header.put('t'.code.toByte())
        header.put('a'.code.toByte())
        header.putInt(totalAudioLen)

        out.write(header.array())
        out.write(pcmData)
        return out.toByteArray()
    }
}
