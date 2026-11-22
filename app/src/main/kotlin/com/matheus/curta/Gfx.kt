package com.matheus.curta

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Tamanho do "quadro" virtual do filme (16:9). Tudo é desenhado nesse espaço e escalado para a tela. */
const val VW = 1920f
const val VH = 1080f

fun clamp01(x: Float): Float = if (x < 0f) 0f else if (x > 1f) 1f else x
fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
fun seg(t: Float, a: Float, b: Float): Float = clamp01((t - a) / (b - a))
fun smooth(x: Float): Float {
    val t = clamp01(x)
    return t * t * (3f - 2f * t)
}
fun smoothSeg(t: Float, a: Float, b: Float): Float = smooth(seg(t, a, b))

/** Número pseudo-aleatório determinístico em [0,1). */
fun hash(i: Int, seed: Int = 0): Float {
    val s = sin(i * 12.9898f + seed * 78.233f) * 43758.5453f
    return s - floor(s)
}

fun pmod(a: Float, b: Float): Float {
    val r = a - floor(a / b) * b
    return r
}

fun withAlpha(rgb: Int, a: Float): Int =
    ((clamp01(a) * 255f + 0.5f).toInt() shl 24) or (rgb and 0xFFFFFF)

fun mixColor(a: Int, b: Int, t: Float): Int {
    val k = clamp01(t)
    val r = (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt()
    val g = (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt()
    val bl = (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt()
    return (r shl 16) or (g shl 8) or bl
}

/** Ferramentas de desenho 2D usadas por todas as cenas. Cores são RGB de 24 bits (sem alfa). */
object Gfx {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowP = Paint(Paint.ANTI_ALIAS_FLAG)
    val path = Path()
    private val rf = RectF()
    private val glowCache = HashMap<Int, RadialGradient>()
    private var vignetteShader: RadialGradient? = null

    // Posições (no espaço do quadro) deixadas pela última chamada de girl()
    var handX = 0f
    var handY = 0f
    var lanX = 0f
    var lanY = 0f

    private fun fillStyle(color: Int, alpha: Float) {
        p.shader = null
        p.style = Paint.Style.FILL
        p.color = withAlpha(color, alpha)
    }

    private fun strokeStyle(color: Int, alpha: Float, w: Float) {
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = w
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.color = withAlpha(color, alpha)
    }

    fun rect(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, alpha: Float = 1f) {
        fillStyle(color, alpha)
        c.drawRect(l, t, r, b, p)
    }

    fun roundRect(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int, alpha: Float = 1f) {
        fillStyle(color, alpha)
        rf.set(l, t, r, b)
        c.drawRoundRect(rf, rad, rad, p)
    }

    fun circle(c: Canvas, x: Float, y: Float, r: Float, color: Int, alpha: Float = 1f) {
        fillStyle(color, alpha)
        c.drawCircle(x, y, r, p)
    }

    fun oval(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, color: Int, alpha: Float = 1f) {
        fillStyle(color, alpha)
        rf.set(cx - rx, cy - ry, cx + rx, cy + ry)
        c.drawOval(rf, p)
    }

    fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, w: Float, color: Int, alpha: Float = 1f) {
        strokeStyle(color, alpha, w)
        c.drawLine(x1, y1, x2, y2, p)
    }

    fun arc(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, start: Float, sweep: Float,
            w: Float, color: Int, alpha: Float = 1f) {
        strokeStyle(color, alpha, w)
        rf.set(cx - rx, cy - ry, cx + rx, cy + ry)
        c.drawArc(rf, start, sweep, false, p)
    }

    fun cap(c: Canvas, cx: Float, cy: Float, r: Float, start: Float, sweep: Float, color: Int, alpha: Float = 1f) {
        fillStyle(color, alpha)
        rf.set(cx - r, cy - r, cx + r, cy + r)
        c.drawArc(rf, start, sweep, false, p)
    }

    fun poly(c: Canvas, color: Int, alpha: Float, vararg pts: Float) {
        path.reset()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) {
            path.lineTo(pts[i], pts[i + 1])
            i += 2
        }
        path.close()
        fillStyle(color, alpha)
        c.drawPath(path, p)
    }

    fun fillPath(c: Canvas, color: Int, alpha: Float = 1f) {
        fillStyle(color, alpha)
        c.drawPath(path, p)
    }

    /** Halo de luz suave (gradiente radial), útil para lanternas, estrelas e lua. */
    fun glow(c: Canvas, x: Float, y: Float, r: Float, color: Int, alpha: Float = 1f) {
        if (alpha <= 0.003f || r <= 0.5f) return
        if (glowCache.size > 300) glowCache.clear()
        val rgb = color and 0xFFFFFF
        val g = glowCache.getOrPut(rgb) {
            RadialGradient(
                0f, 0f, 1f,
                intArrayOf(rgb or (0xFF shl 24), rgb or (0x59 shl 24), rgb),
                floatArrayOf(0f, 0.35f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        glowP.shader = g
        glowP.alpha = (clamp01(alpha) * 255f).toInt()
        c.save()
        c.translate(x, y)
        c.scale(r, r)
        c.drawCircle(0f, 0f, 1f, glowP)
        c.restore()
    }

    fun sky(c: Canvas, top: Int, bottom: Int) {
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, 0f, VH, withAlpha(top, 1f), withAlpha(bottom, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, VW, VH, p)
        p.shader = null
    }

    fun gradientRect(c: Canvas, l: Float, t: Float, r: Float, b: Float, top: Int, bottom: Int, alphaTop: Float, alphaBottom: Float) {
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, t, 0f, b, withAlpha(top, alphaTop), withAlpha(bottom, alphaBottom), Shader.TileMode.CLAMP)
        c.drawRect(l, t, r, b, p)
        p.shader = null
    }

    fun vignette(c: Canvas) {
        var s = vignetteShader
        if (s == null) {
            s = RadialGradient(
                VW / 2f, VH / 2f, 1250f,
                intArrayOf(0x00000000, 0x00000000, (0x8C shl 24)),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            vignetteShader = s
        }
        p.style = Paint.Style.FILL
        p.shader = s
        c.drawRect(0f, 0f, VW, VH, p)
        p.shader = null
    }

    // ---------- céu ----------
    private const val NSTARS = 170
    private val starX = FloatArray(NSTARS) { hash(it, 1) * VW }
    private val starY = FloatArray(NSTARS) { hash(it, 2) * 700f }
    private val starR = FloatArray(NSTARS) { 1.2f + hash(it, 3) * 2.4f }

    fun starfield(c: Canvas, t: Float, alpha: Float) {
        for (i in 0 until NSTARS) {
            val tw = 0.55f + 0.45f * sin(t * (1.2f + hash(i, 4) * 2.2f) + hash(i, 5) * 6.28f)
            circle(c, starX[i], starY[i], starR[i], 0xFFFFFF, alpha * tw * (0.45f + 0.55f * hash(i, 6)))
        }
    }

    fun moon(c: Canvas, x: Float, y: Float, r: Float) {
        glow(c, x, y, r * 4.5f, 0xBFD4FF, 0.35f)
        circle(c, x, y, r, 0xF4F1DE)
        circle(c, x - r * 0.3f, y - r * 0.2f, r * 0.2f, 0xDAD6BC, 0.7f)
        circle(c, x + r * 0.25f, y + r * 0.25f, r * 0.28f, 0xDAD6BC, 0.6f)
        circle(c, x + r * 0.3f, y - r * 0.4f, r * 0.12f, 0xDAD6BC, 0.6f)
    }

    fun sparkle(c: Canvas, x: Float, y: Float, s: Float, color: Int, alpha: Float) {
        if (alpha <= 0.005f || s <= 0.5f) return
        val k = s * 0.18f
        poly(c, color, alpha,
            x, y - s, x + k, y - k, x + s, y, x + k, y + k,
            x, y + s, x - k, y + k, x - s, y, x - k, y - k)
    }

    // ---------- terreno ----------
    fun hillY(x: Float, base: Float, a1: Float, f1: Float, p1: Float, a2: Float, f2: Float, p2: Float, ox: Float): Float {
        val wx = x - ox
        return base + a1 * sin(wx * f1 + p1) + a2 * sin(wx * f2 + p2)
    }

    fun hills(c: Canvas, base: Float, a1: Float, f1: Float, p1: Float, a2: Float, f2: Float, p2: Float,
              color: Int, ox: Float = 0f) {
        path.reset()
        path.moveTo(-40f, VH + 20f)
        var x = -40f
        while (x <= VW + 40f) {
            path.lineTo(x, hillY(x, base, a1, f1, p1, a2, f2, p2, ox))
            x += 30f
        }
        path.lineTo(VW + 40f, VH + 20f)
        path.close()
        fillPath(c, color)
    }

    fun pine(c: Canvas, x: Float, base: Float, h: Float, color: Int, alpha: Float = 1f) {
        val w = h * 0.46f
        rect(c, x - h * 0.03f, base - h * 0.14f, x + h * 0.03f, base, color, alpha)
        for (k in 0..2) {
            val yb = base - h * 0.10f - k * h * 0.26f
            val yt = yb - h * 0.42f * (1f - k * 0.16f)
            val hw = w * (1f - k * 0.26f) * 0.5f
            poly(c, color, alpha, x - hw, yb, x + hw, yb, x, yt)
        }
    }

    fun house(c: Canvas, x: Float, base: Float, w: Float, h: Float, wall: Int, roof: Int,
              gt: Float, seed: Int, smoke: Boolean) {
        rect(c, x, base - h, x + w, base + 20f, wall)
        poly(c, roof, 1f, x - 10f, base - h, x + w + 10f, base - h, x + w * 0.5f, base - h - h * 0.55f)
        rect(c, x + w * 0.72f, base - h - h * 0.5f, x + w * 0.72f + w * 0.1f, base - h - h * 0.12f, roof)
        // janela acesa com leve tremulação
        val fl = 0.85f + 0.15f * sin(gt * (3f + hash(seed, 1) * 3f) + seed)
        val wx = x + w * 0.18f
        val wy = base - h * 0.72f
        glow(c, wx + w * 0.13f, wy + h * 0.15f, w * 0.9f, 0xFFB84D, 0.30f * fl)
        rect(c, wx, wy, wx + w * 0.26f, wy + h * 0.3f, 0xFFD27A, fl)
        rect(c, wx + w * 0.125f, wy, wx + w * 0.135f, wy + h * 0.3f, 0x7A4A22, 0.8f)
        if (hash(seed, 2) > 0.35f) {
            val w2 = x + w * 0.58f
            rect(c, w2, wy, w2 + w * 0.22f, wy + h * 0.3f, 0xFFD27A, 0.75f + 0.25f * fl)
        }
        rect(c, x + w * 0.42f, base - h * 0.42f, x + w * 0.52f, base, 0x2A1A18)
        if (smoke) {
            val sx = x + w * 0.77f
            val sy = base - h - h * 0.52f
            for (k in 0..4) {
                val ph = pmod(gt * 0.22f + k * 0.2f, 1f)
                circle(c, sx + sin(ph * 6f + k) * 9f + ph * 45f, sy - ph * 130f, 8f + ph * 20f, 0x8C93B8, (1f - ph) * 0.28f)
            }
        }
    }

    // ---------- personagens ----------
    /** Posição local da mão livre (antes da translação de ajoelhar). */
    private var hlx = 0f
    private var hly = 0f

    private fun freeHandLocal(walk: Float, amp: Float, carry: Float, raise: Float, wave: Float) {
        val hx = -6f - sin(walk) * 18f * amp
        val hy = -112f
        val cx = lerp(hx, 30f, carry)
        val cy = lerp(hy, -130f, carry)
        hlx = lerp(cx, 34f + wave, raise)
        hly = lerp(cy, -268f, raise)
    }

    /** Calcula (sem desenhar) onde fica a mão livre da menina no espaço do quadro. */
    fun girlHand(x: Float, y: Float, s: Float, dir: Float, carry: Float, raise: Float, wave: Float, kneel: Float) {
        freeHandLocal(0f, 0f, carry, raise, wave)
        handX = x + dir * s * hlx
        handY = y + s * (hly + 52f * kneel)
    }

    /**
     * Lia: menina de casaco vermelho e cachecol amarelo.
     * (x,y) = pés; s = escala; dir = +1 olha para a direita; walk = fase do passo; amp = amplitude do passo.
     */
    fun girl(c: Canvas, x: Float, y: Float, s: Float, dir: Float, walk: Float, amp: Float,
             lantern: Boolean, carry: Float = 0f, raise: Float = 0f, wave: Float = 0f,
             kneel: Float = 0f, smile: Float = 1f) {
        val d = 52f * kneel
        val bob = abs(sin(walk)) * 4f * amp
        val a = sin(walk) * 0.55f * amp
        c.save()
        c.translate(x, y)
        c.scale(dir * s, s)

        // ---- pernas ----
        val hipX = 0f
        val hipY = -98f + d * 0f
        // pé da frente / de trás em pé
        val f1x = sin(a) * 52f
        val f1y = -98f + cos(a) * 98f
        val f2x = -sin(a) * 52f
        val f2y = -98f + cos(-a) * 98f
        val k1x = f1x * 0.5f + 6f
        val k1y = (hipY + f1y) * 0.5f
        val k2x = f2x * 0.5f + 2f
        val k2y = (hipY + f2y) * 0.5f
        // ajoelhada: perna da frente dobrada, de trás com joelho no chão
        val hkY = -98f + d
        val legColor = 0x2B2538
        // perna de trás
        val bkx = lerp(k2x, -6f, kneel)
        val bky = lerp(k2y, -6f, kneel)
        val bfx = lerp(f2x, -52f, kneel)
        val bfy = lerp(f2y, -8f, kneel)
        val bhy = lerp(hipY, hkY, kneel)
        line(c, hipX, bhy, bkx, bky, 15f, legColor)
        line(c, bkx, bky, bfx, bfy, 14f, legColor)
        oval(c, bfx + 6f, bfy, 13f, 7f, 0x1A1420)
        // perna da frente
        val ffx = lerp(f1x, 40f, kneel)
        val ffy = lerp(f1y, -2f, kneel)
        val fkx = lerp(k1x, 44f, kneel)
        val fky = lerp(k1y, -56f, kneel)
        line(c, hipX, bhy, fkx, fky, 15f, legColor)
        line(c, fkx, fky, ffx, ffy, 14f, legColor)
        oval(c, ffx + 6f, ffy - 2f, 14f, 7f, 0x1A1420)

        // ---- tronco ----
        c.save()
        c.translate(0f, d - bob)
        // rabo de cavalo
        c.save()
        c.rotate(18f + sin(walk - 0.8f) * 14f * amp, -26f, -226f)
        oval(c, -36f, -204f, 10f, 24f, 0x2A1F2D)
        c.restore()
        // cachecol (ponta que balança)
        poly(c, 0xF2C14E, 1f,
            -14f, -186f, -44f - sin(walk) * 6f * amp, -170f, -48f - sin(walk) * 8f * amp, -150f, -20f, -166f)
        // casaco
        poly(c, 0xC8504A, 1f, -22f, -188f, 24f, -188f, 38f, -92f, -36f, -92f)
        rect(c, -3f, -186f, 3f, -94f, 0x9E3A36)
        rect(c, -36f, -96f, 38f, -90f, 0x9E3A36)
        oval(c, 1f, -188f, 21f, 10f, 0xF2C14E)
        // cabeça
        circle(c, -3f, -219f, 31f, 0x2A1F2D)
        circle(c, 2f, -216f, 28f, 0xF1C6A0)
        cap(c, 2f, -216f, 31f, 198f, 144f, 0x2A1F2D)
        circle(c, 14f, -213f, 3.4f, 0x2A1F2D)
        circle(c, 17f, -205f, 6f, 0xE88C8C, 0.35f)
        arc(c, 14f, -204f, 6f, 5f, 15f, 130f * smile, 2.2f, 0x7A3B3B)

        // braço da lanterna
        if (lantern) {
            val lhx = 28f + sin(walk + 1.5f) * 8f * amp
            val lhy = -122f + bob * 0.5f
            line(c, 4f, -182f, lhx, lhy, 14f, 0xC8504A)
            circle(c, lhx, lhy, 8f, 0xF1C6A0)
            line(c, lhx, lhy, lhx, lhy + 20f, 2f, 0x3A2A1A)
            roundRect(c, lhx - 12f, lhy + 18f, lhx + 12f, lhy + 50f, 5f, 0xFFE29A)
            rect(c, lhx - 13f, lhy + 16f, lhx + 13f, lhy + 21f, 0x3A2A1A)
            rect(c, lhx - 13f, lhy + 48f, lhx + 13f, lhy + 53f, 0x3A2A1A)
            lanX = x + dir * s * lhx
            lanY = y + s * (lhy + 34f + d - bob)
        }
        // braço livre
        freeHandLocal(walk, amp, carry, raise, wave)
        line(c, -2f, -182f, hlx, hly, 14f, 0xC8504A)
        circle(c, hlx, hly, 8f, 0xF1C6A0)
        handX = x + dir * s * hlx
        handY = y + s * (hly + d - bob)
        c.restore()
        c.restore()
    }

    /** Estrelinha-personagem: corpo de estrela com rosto. */
    fun starCreature(c: Canvas, x: Float, y: Float, r: Float, rot: Float, bright: Float, awake: Float, t: Float) {
        val b = clamp01(bright)
        glow(c, x, y, r * (3.2f + 2.6f * b), 0xFFD966, 0.30f + 0.60f * b)
        path.reset()
        for (i in 0 until 10) {
            val ang = rot + i * (PI.toFloat() / 5f) - (PI.toFloat() / 2f)
            val rad = if (i % 2 == 0) r else r * 0.5f
            val px = x + cos(ang) * rad
            val py = y + sin(ang) * rad
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        fillPath(c, mixColor(0xB9A778, 0xFFE27A, b))
        // rosto (acompanha a rotação suave)
        c.save()
        c.translate(x, y)
        c.rotate(rot * 57.2958f * 0.35f)
        val ex = r * 0.22f
        val ey = -r * 0.04f
        if (awake < 0.5f) {
            arc(c, -ex, ey, r * 0.1f, r * 0.07f, 0f, 180f, r * 0.05f, 0x5A4A2A)
            arc(c, ex, ey, r * 0.1f, r * 0.07f, 0f, 180f, r * 0.05f, 0x5A4A2A)
        } else {
            circle(c, -ex, ey, r * 0.085f, 0x3A2A1A)
            circle(c, ex, ey, r * 0.085f, 0x3A2A1A)
            circle(c, -ex + r * 0.025f, ey - r * 0.03f, r * 0.03f, 0xFFFFFF)
            circle(c, ex + r * 0.025f, ey - r * 0.03f, r * 0.03f, 0xFFFFFF)
            arc(c, 0f, r * 0.12f, r * 0.12f, r * 0.09f, 10f, 160f * awake, r * 0.045f, 0x7A3B2B)
            circle(c, -ex - r * 0.1f, ey + r * 0.14f, r * 0.07f, 0xFF8A8A, 0.4f)
            circle(c, ex + r * 0.1f, ey + r * 0.14f, r * 0.07f, 0xFF8A8A, 0.4f)
        }
        c.restore()
        if (b > 0.3f) {
            val tw = 0.5f + 0.5f * sin(t * 4f)
            sparkle(c, x + r * 1.5f, y - r * 1.2f, r * 0.35f * tw, 0xFFF3B0, b)
        }
    }
}
