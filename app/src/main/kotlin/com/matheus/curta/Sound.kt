package com.matheus.curta

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Trilha sonora do curta, sintetizada por código (sem arquivos de áudio):
 * pad de acordes + arpejo, estrela cadente, impacto, sininhos e o clímax final.
 * Qualquer falha de áudio é ignorada: o filme continua sem som.
 */
class Sound {
    companion object {
        const val SR = 16000
    }

    var positionProvider: (() -> Float)? = null

    private val lock = Any()
    private var track: AudioTrack? = null
    private var totalFrames = 0
    private var wantPlay = false
    private var released = false

    fun prepare() {
        Thread {
            try {
                val pcm = generate()
                val t = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SR)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                t.write(pcm, 0, pcm.size)
                synchronized(lock) {
                    if (released) {
                        t.release()
                    } else {
                        track = t
                        totalFrames = pcm.size
                        if (wantPlay) startAt(t, positionProvider?.invoke() ?: 0f)
                    }
                }
            } catch (e: Throwable) {
                // sem som, tudo bem
            }
        }.start()
    }

    private fun startAt(t: AudioTrack, pos: Float) {
        try {
            t.stop()
            val frame = (pos * SR).toInt().coerceIn(0, maxOf(0, totalFrames - 1))
            t.setPlaybackHeadPosition(frame)
            t.play()
        } catch (e: Throwable) {
        }
    }

    fun play(pos: Float) {
        synchronized(lock) {
            wantPlay = true
            val t = track
            if (t != null) startAt(t, pos)
        }
    }

    fun pause() {
        synchronized(lock) {
            wantPlay = false
            try {
                track?.pause()
            } catch (e: Throwable) {
            }
        }
    }

    fun release() {
        synchronized(lock) {
            released = true
            wantPlay = false
            try {
                track?.release()
            } catch (e: Throwable) {
            }
            track = null
        }
    }

    // ------------------------------------------------------------------ síntese

    private fun intensity(t: Float): Float {
        val ks = floatArrayOf(0f, 2.5f, 19f, 37f, 46f, 51f, 65f, 70f, 75f, 79f, Film.DURATION)
        val vs = floatArrayOf(0f, 0.5f, 0.55f, 0.45f, 0.5f, 0.65f, 0.85f, 0.9f, 1.2f, 1.0f, 0f)
        for (i in 0 until ks.size - 1) {
            if (t < ks[i + 1]) {
                val u = (t - ks[i]) / (ks[i + 1] - ks[i])
                return vs[i] + (vs[i + 1] - vs[i]) * u
            }
        }
        return 0f
    }

    private fun addTone(buf: FloatArray, start: Float, dur: Float, f: Float, amp: Float, decay: Float, attack: Float = 0.01f) {
        val i0 = (start * SR).toInt().coerceAtLeast(0)
        val i1 = ((start + dur) * SR).toInt().coerceAtMost(buf.size)
        val w = 2.0 * PI * f
        for (i in i0 until i1) {
            val tt = i.toDouble() / SR - start
            val env = min(1.0, tt / attack) * exp(-tt * decay)
            buf[i] += (amp * env * (sin(w * tt) + 0.3 * sin(2.0 * w * tt))).toFloat()
        }
    }

    private fun generate(): ShortArray {
        val seconds = Film.DURATION.toInt() + 2
        val n = SR * seconds
        val buf = FloatArray(n)
        val rnd = Random(7)

        val chords = arrayOf(
            floatArrayOf(110.00f, 130.81f, 164.81f),
            floatArrayOf(87.31f, 110.00f, 130.81f),
            floatArrayOf(130.81f, 164.81f, 196.00f),
            floatArrayOf(98.00f, 123.47f, 146.83f)
        )
        val arp = arrayOf(
            floatArrayOf(440.00f, 523.25f, 659.25f, 523.25f),
            floatArrayOf(349.23f, 440.00f, 523.25f, 440.00f),
            floatArrayOf(523.25f, 659.25f, 783.99f, 659.25f),
            floatArrayOf(392.00f, 493.88f, 587.33f, 493.88f)
        )
        val segLen = 4.1f

        // pad de acordes
        var k = 0
        while (k * segLen < Film.DURATION) {
            val start = k * segLen
            val ch = chords[k % 4]
            val i0 = (start * SR).toInt()
            val i1 = minOf(n, ((start + segLen + 1.2f) * SR).toInt())
            for (i in i0 until i1) {
                val t = i.toDouble() / SR
                val u = ((t - start) / 0.9).coerceIn(0.0, 1.0)
                val a = u * u * (3.0 - 2.0 * u)
                val r = ((t - start - segLen) / 1.2).coerceIn(0.0, 1.0)
                val rel = 1.0 - r * r * (3.0 - 2.0 * r)
                var s = 0.0
                for (f in ch) {
                    val w = 2.0 * PI * f
                    s += sin(w * t) + 0.3 * sin(2.0 * w * t)
                }
                buf[i] += (s * a * rel * 0.11 * intensity(t.toFloat())).toFloat()
            }
            k++
        }

        // arpejo
        val step = segLen / 8f
        var j = 0
        while (j * step < Film.DURATION - 1f) {
            val tm = j * step
            if (tm >= 4f) {
                val seg = (tm / segLen).toInt()
                val f = arp[seg % 4][j % 4]
                addTone(buf, tm, 1.1f, f, 0.10f * intensity(tm), 4.5f)
            }
            j++
        }

        // estrela cadente (5s + 4s .. 5s + 7s): ruído filtrado
        run {
            val i0 = 9 * SR
            val i1 = (12.4f * SR).toInt()
            var y = 0f
            for (i in i0 until i1) {
                val u = (i - i0).toFloat() / (i1 - i0)
                val env = sin(u * PI.toFloat()).let { it * it }
                val a = 0.02f + 0.3f * u
                val x = rnd.nextFloat() * 2f - 1f
                y += (x - y) * a
                buf[i] += y * env * 0.35f
            }
        }

        // impacto
        addTone(buf, 12.0f, 1.6f, 55f, 0.45f, 3.2f, 0.005f)
        addTone(buf, 12.0f, 2.8f, 880f, 0.12f, 2.0f)
        addTone(buf, 12.05f, 2.8f, 1318.5f, 0.08f, 2.5f)
        run {
            val i0 = 12 * SR
            val i1 = i0 + (0.25f * SR).toInt()
            for (i in i0 until i1) {
                val tt = (i - i0).toFloat() / SR
                buf[i] += (rnd.nextFloat() * 2f - 1f) * exp(-tt * 18f) * 0.18f
            }
        }

        // a estrela acorda nas mãos da Lia
        addTone(buf, 47.3f, 1.6f, 659.25f, 0.14f, 2.2f)
        addTone(buf, 47.6f, 1.6f, 880.00f, 0.14f, 2.2f)
        addTone(buf, 47.9f, 2.2f, 1318.5f, 0.14f, 1.8f)

        // a estrela sobe: sininhos cada vez mais agudos
        var tm = 71.0f
        while (tm < 75.0f) {
            val u = (tm - 71f) / 4f
            val f = 1800f + 1800f * rnd.nextFloat() + 600f * u
            addTone(buf, tm, 0.5f, f, 0.05f + 0.03f * u, 9f)
            tm += 0.11f
        }

        // chegada ao céu
        for (f in floatArrayOf(440f, 523.25f, 659.25f, 880f, 1318.5f)) {
            addTone(buf, 75.0f, 6.0f, f, 0.10f, 0.55f, 0.15f)
        }
        addTone(buf, 75.0f, 2.0f, 55f, 0.35f, 2.0f, 0.01f)

        val out = ShortArray(n)
        for (i in 0 until n) {
            // fade de saída final
            val t = i.toFloat() / SR
            val tail = if (t > Film.DURATION - 1.2f) ((Film.DURATION + 0.2f - t) / 1.4f).coerceIn(0f, 1f) else 1f
            out[i] = (tanh(buf[i] * 1.4f * tail) * 0.8f * 32767f).toInt().toShort()
        }
        return out
    }
}
