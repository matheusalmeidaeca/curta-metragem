package com.matheus.curta

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Player do curta: mantém o relógio do filme, escala o quadro 16:9 para a tela (barras pretas
 * como no cinema), desenha legendas e os controles (play/pause, barra de tempo, reiniciar).
 */
class FilmView(context: Context) : View(context) {
    private val sound = Sound()
    private val dp = resources.displayMetrics.density

    private var time = 0f
    private var playing = false
    private var ended = false
    private var started = false
    private var lastNanos = 0L
    private var controlsUntil = 0L
    private var dragging = false
    private var downTap = false

    private val ui = Paint(Paint.ANTI_ALIAS_FLAG)
    private val uiText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 14f * dp
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        color = Color.WHITE
    }
    private val subBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x73000000 }
    private val rect = RectF()
    private val tri = Path()
    private var scrim: LinearGradient? = null

    init {
        setBackgroundColor(Color.BLACK)
        sound.positionProvider = { time }
        sound.prepare()
    }

    // ------------------------------------------------------------------ controle

    fun autoStart() {
        if (!started) {
            started = true
            setPlaying(true)
        }
    }

    fun pauseFilm() {
        setPlaying(false)
    }

    fun release() {
        sound.release()
    }

    private fun setPlaying(p: Boolean) {
        if (p == playing) return
        if (p) {
            if (time >= Film.DURATION - 0.05f) time = 0f
            ended = false
            playing = true
            lastNanos = 0L
            sound.play(time)
        } else {
            playing = false
            sound.pause()
        }
        invalidate()
    }

    private fun controlsVisible(): Boolean = !playing || dragging || SystemClock.uptimeMillis() < controlsUntil

    private fun barLeft() = 96f * dp
    private fun barRight() = width - 110f * dp
    private fun barY() = height - 64f * dp

    private fun seekTo(x: Float) {
        val f = ((x - barLeft()) / (barRight() - barLeft())).coerceIn(0f, 1f)
        time = f * (Film.DURATION - 0.02f)
        ended = false
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        scrim = LinearGradient(0f, h - 170f * dp, 0f, h.toFloat(), 0x00000000, 0xC0000000.toInt(), Shader.TileMode.CLAMP)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val onBar = controlsVisible() && e.y > height - 110f * dp &&
                    e.x > barLeft() - 24f * dp && e.x < barRight() + 24f * dp
                if (onBar) {
                    dragging = true
                    downTapWasPlaying = playing
                    setPlaying(false)
                    seekTo(e.x)
                } else {
                    downTap = true
                }
            }
            MotionEvent.ACTION_MOVE -> if (dragging) seekTo(e.x)
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    dragging = false
                    if (downTapWasPlaying) setPlaying(true)
                } else if (downTap) {
                    if (ended) setPlaying(true) else setPlaying(!playing)
                    controlsUntil = SystemClock.uptimeMillis() + 2500L
                }
                downTap = false
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                downTap = false
            }
        }
        invalidate()
        return true
    }

    private var downTapWasPlaying = false

    // ------------------------------------------------------------------ desenho

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        if (playing) {
            if (lastNanos != 0L) time += min((now - lastNanos) / 1e9f, 0.1f)
            lastNanos = now
            if (time >= Film.DURATION - 0.01f) {
                time = Film.DURATION - 0.01f
                playing = false
                ended = true
                sound.pause()
            }
        } else {
            lastNanos = 0L
        }

        canvas.drawColor(Color.BLACK)
        val w = width.toFloat()
        val h = height.toFloat()
        val sc = min(w / VW, h / VH)
        canvas.save()
        canvas.translate((w - VW * sc) / 2f, (h - VH * sc) / 2f)
        canvas.scale(sc, sc)
        canvas.clipRect(0f, 0f, VW, VH)
        Film.draw(canvas, time)
        drawSubtitle(canvas)
        canvas.restore()

        if (controlsVisible()) drawControls(canvas)

        if (playing || dragging) postInvalidateOnAnimation()
        else if (controlsUntil > SystemClock.uptimeMillis()) postInvalidateOnAnimation()
    }

    private fun drawSubtitle(c: Canvas) {
        for (cue in Film.cues) {
            if (time >= cue.start && time <= cue.end) {
                val a = min(seg(time, cue.start, cue.start + 0.3f), 1f - seg(time, cue.end - 0.3f, cue.end))
                subPaint.textSize = 46f
                val tw = subPaint.measureText(cue.text)
                if (tw > 1700f) subPaint.textSize = 46f * 1700f / tw
                val w2 = subPaint.measureText(cue.text)
                subBg.alpha = (115f * a).toInt()
                rect.set(VW / 2f - w2 / 2f - 28f, 940f, VW / 2f + w2 / 2f + 28f, 1010f)
                c.drawRoundRect(rect, 16f, 16f, subBg)
                subPaint.alpha = (255f * a).toInt()
                c.drawText(cue.text, VW / 2f, 990f, subPaint)
                return
            }
        }
    }

    private fun drawControls(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        ui.style = Paint.Style.FILL
        ui.shader = scrim
        c.drawRect(0f, h - 170f * dp, w, h, ui)
        ui.shader = null
        ui.color = Color.WHITE

        // botão play / pause / reiniciar
        val cx = 48f * dp
        val cy = barY()
        val s = 14f * dp
        if (ended) {
            ui.style = Paint.Style.STROKE
            ui.strokeWidth = 3f * dp
            ui.strokeCap = Paint.Cap.ROUND
            rect.set(cx - s, cy - s, cx + s, cy + s)
            c.drawArc(rect, 40f, 280f, false, ui)
            val th = 320f * Math.PI.toFloat() / 180f
            val px = cx + cos(th) * s
            val py = cy + sin(th) * s
            val dx = -sin(th)
            val dy = cos(th)
            ui.style = Paint.Style.FILL
            tri.reset()
            tri.moveTo(px + dx * 9f * dp, py + dy * 9f * dp)
            tri.lineTo(px - dy * 7f * dp, py + dx * 7f * dp)
            tri.lineTo(px + dy * 7f * dp, py - dx * 7f * dp)
            tri.close()
            c.drawPath(tri, ui)
        } else if (playing) {
            ui.style = Paint.Style.FILL
            rect.set(cx - s * 0.75f, cy - s, cx - s * 0.2f, cy + s)
            c.drawRoundRect(rect, 3f * dp, 3f * dp, ui)
            rect.set(cx + s * 0.2f, cy - s, cx + s * 0.75f, cy + s)
            c.drawRoundRect(rect, 3f * dp, 3f * dp, ui)
        } else {
            ui.style = Paint.Style.FILL
            tri.reset()
            tri.moveTo(cx - s * 0.7f, cy - s)
            tri.lineTo(cx - s * 0.7f, cy + s)
            tri.lineTo(cx + s, cy)
            tri.close()
            c.drawPath(tri, ui)
        }

        // barra de tempo
        val l = barLeft()
        val r = barRight()
        val y = barY()
        ui.style = Paint.Style.STROKE
        ui.strokeCap = Paint.Cap.ROUND
        ui.strokeWidth = 4f * dp
        ui.color = 0x55FFFFFF
        c.drawLine(l, y, r, y, ui)
        val px = l + (r - l) * (time / Film.DURATION).coerceIn(0f, 1f)
        ui.color = 0xFFFFD966.toInt()
        c.drawLine(l, y, px, y, ui)
        ui.style = Paint.Style.FILL
        c.drawCircle(px, y, (if (dragging) 11f else 8f) * dp, ui)
        ui.color = Color.WHITE

        c.drawText(fmt(time) + " / " + fmt(Film.DURATION), r + 16f * dp, y + 5f * dp, uiText)
    }

    private fun fmt(t: Float): String {
        val s = t.toInt()
        return (s / 60).toString() + ":" + (s % 60).toString().padStart(2, '0')
    }
}
