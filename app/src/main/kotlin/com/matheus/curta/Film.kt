package com.matheus.curta

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * "A Estrela Caída" — curta-metragem 2D desenhado 100% por código.
 * Tudo é função do tempo t (em segundos): por isso dá para pausar, avançar e voltar o filme.
 */
object Film {
    const val DURATION = 82f

    private const val T_VILA = 5f
    private const val T_FLORESTA = 19f
    private const val T_CLAREIRA = 37f
    private const val T_COLINA = 51f
    private const val T_TOPO = 65f

    class Cue(val start: Float, val end: Float, val text: String)

    val cues = listOf(
        Cue(6.0f, 9.0f, "Naquela noite, a pequena Lia observava o céu."),
        Cue(9.3f, 12.5f, "De repente, uma estrela riscou a escuridão..."),
        Cue(12.8f, 16.5f, "...e caiu bem além da floresta."),
        Cue(19.8f, 24.0f, "Lia pegou sua lanterna e foi atrás da luz."),
        Cue(25.0f, 29.5f, "A floresta era escura, mas ela não tinha medo."),
        Cue(30.0f, 34.5f, "Pelo caminho, os vaga-lumes faziam companhia."),
        Cue(34.8f, 36.8f, "Uma luz dourada brilhava mais adiante."),
        Cue(38.0f, 42.0f, "Na clareira, ela encontrou a estrela. Estava fraquinha."),
        Cue(43.5f, 47.0f, "— Não tenha medo. Eu vou te levar de volta para casa."),
        Cue(47.5f, 50.5f, "A estrela sorriu pela primeira vez."),
        Cue(52.0f, 56.5f, "Subiram juntas a colina mais alta do vale."),
        Cue(57.0f, 61.5f, "A cada passo, a estrelinha brilhava mais forte."),
        Cue(62.0f, 64.8f, "— Olha, é ali o seu lugar."),
        Cue(66.0f, 70.0f, "No topo, o céu inteiro parecia esperar."),
        Cue(71.5f, 75.5f, "Lia ergueu as mãos, e a estrela voltou para casa."),
        Cue(75.8f, 78.8f, "Desde então, uma estrela a mais olha por ela, toda noite.")
    )

    private val boundaries = floatArrayOf(T_VILA, T_FLORESTA, T_CLAREIRA, T_COLINA, T_TOPO)

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, alpha: Float, spacing: Float = 0f) {
        textPaint.textSize = size
        textPaint.letterSpacing = spacing
        textPaint.color = withAlpha(color, alpha)
        c.drawText(s, x, y, textPaint)
    }

    fun draw(c: Canvas, t: Float) {
        when {
            t < T_VILA -> title(c, t)
            t < T_FLORESTA -> vila(c, t - T_VILA, t)
            t < T_CLAREIRA -> floresta(c, t - T_FLORESTA, t)
            t < T_COLINA -> clareira(c, t - T_CLAREIRA, t)
            t < T_TOPO -> colina(c, t - T_COLINA, t)
            else -> topo(c, t - T_TOPO, t)
        }
        Gfx.vignette(c)

        // fade para o preto nas transições de cena, no começo e no fim
        var f = 1f - t / 0.6f
        for (b in boundaries) f = max(f, 1f - abs(t - b) / 0.6f)
        if (t > DURATION - 1.2f) f = max(f, (t - (DURATION - 1.2f)) / 1.2f)
        if (f > 0.003f) Gfx.rect(c, 0f, 0f, VW, VH, 0x000000, f)
    }

    // ------------------------------------------------------------------ utilidades

    private fun stepAmp(x: Float, xPrev: Float): Float = clamp01(abs(x - xPrev) / 0.04f / 80f)

    private fun grass(c: Canvas, scroll: Float, xMin: Float, span: Float, yTop: Float, yBot: Float,
                      n: Int, color: Int, gt: Float) {
        for (i in 0 until n) {
            val x = pmod(hash(i, 21) * span - scroll, span) + xMin
            val y = yTop + hash(i, 22) * (yBot - yTop)
            val h = 14f + hash(i, 23) * 26f
            val sw = sin(gt * 1.5f + i) * 5f
            Gfx.line(c, x, y, x + sw, y - h, 3f, color)
            Gfx.line(c, x + 7f, y, x + 7f + sw * 1.3f, y - h * 0.8f, 3f, color)
        }
    }

    private fun trees(c: Canvas, scroll: Float, spacing: Float, base: Float, h0: Float, hv: Float, color: Int, seed: Int) {
        val first = floor(scroll / spacing).toInt() - 1
        val n = (VW / spacing).toInt() + 4
        for (k in 0 until n) {
            val i = first + k
            val x = i * spacing + (hash(i, seed) - 0.5f) * spacing * 0.7f - scroll
            val h = h0 + hash(i, seed + 1) * hv
            Gfx.pine(c, x, base + (hash(i, seed + 2) - 0.5f) * 20f, h, color)
        }
    }

    private fun fireflies(c: Canvas, gt: Float, scroll: Float, count: Int, yMin: Float, yRange: Float) {
        for (i in 0 until count) {
            val x = pmod(hash(i, 7) * 2400f - scroll * (0.8f + hash(i, 8) * 0.4f) + sin(gt * 0.6f + i) * 40f, 2400f) - 240f
            val y = yMin + hash(i, 9) * yRange + sin(gt * 0.9f + i * 1.3f) * 28f
            val a = 0.5f + 0.5f * sin(gt * 1.7f + i * 2.1f)
            Gfx.glow(c, x, y, 26f, 0xC8FF6A, 0.75f * a)
            Gfx.circle(c, x, y, 3f, 0xF2FFC0, a)
        }
    }

    private val cxs = floatArrayOf(900f, 1030f, 1170f, 1290f, 1430f, 1530f)
    private val cys = floatArrayOf(330f, 250f, 290f, 200f, 260f, 360f)

    /** Constelação de 6 estrelas: a de índice 3 está faltando (é o lugar da estrelinha). */
    private fun constellation(c: Canvas, gt: Float, link: Float, alpha: Float) {
        for (i in 0 until 5) {
            val a = if (i == 2 || i == 3) link else 1f
            Gfx.line(c, cxs[i], cys[i], cxs[i + 1], cys[i + 1], 2.5f, 0xBFD4FF, alpha * 0.35f * a)
        }
        for (i in 0 until 6) {
            if (i == 3) continue
            val tw = 0.7f + 0.3f * sin(gt * 2f + i)
            Gfx.glow(c, cxs[i], cys[i], 42f, 0xBFD4FF, alpha * 0.5f * tw)
            Gfx.sparkle(c, cxs[i], cys[i], 15f, 0xFFFFFF, alpha * tw)
        }
    }

    private fun bez(a: Float, b: Float, c: Float, p: Float): Float {
        val q = 1f - p
        return q * q * a + 2f * q * p * b + p * p * c
    }

    // ------------------------------------------------------------------ cena 0: título

    private fun title(c: Canvas, gt: Float) {
        Gfx.sky(c, 0x03040E, 0x151338)
        Gfx.starfield(c, gt, 0.9f * smoothSeg(gt, 0.2f, 1.5f))
        val a = smoothSeg(gt, 0.8f, 2.2f)
        Gfx.glow(c, 960f, 470f, 420f, 0xFFD966, 0.10f * a)
        Gfx.sparkle(c, 960f, 330f, 28f + 5f * sin(gt * 3f), 0xFFE9A8, a)
        Gfx.glow(c, 960f, 330f, 90f, 0xFFD966, 0.35f * a)
        text(c, "A ESTRELA CAÍDA", 960f, 530f, 118f, 0xFFF3D6, smoothSeg(gt, 1.0f, 2.4f), 0.16f)
        text(c, "um curta-metragem", 960f, 610f, 40f, 0xBFD4FF, smoothSeg(gt, 2.2f, 3.4f) * 0.85f, 0.1f)
    }

    // ------------------------------------------------------------------ cena 1: a vila

    private fun vila(c: Canvas, lt: Float, gt: Float) {
        val pan = lt * 12f
        Gfx.sky(c, 0x070C26, 0x4A3470)
        Gfx.starfield(c, gt, 1f)
        Gfx.moon(c, 1450f - pan * 0.05f, 210f, 64f)

        // estrela cadente
        if (lt >= 4f && lt < 7f) {
            val p = seg(lt, 4f, 7f)
            for (k in 0..14) {
                val q = p - k * 0.02f
                if (q > 0f) {
                    val f = 1f - k / 15f
                    Gfx.glow(c, lerp(820f, 1560f, q), lerp(40f, 690f, q), 8f + 26f * f, 0xFFE9A8, f * 0.8f)
                }
            }
            val hx = lerp(820f, 1560f, p)
            val hy = lerp(40f, 690f, p)
            Gfx.glow(c, hx, hy, 80f, 0xFFD966, 1f)
            Gfx.sparkle(c, hx, hy, 30f, 0xFFFFFF, 1f)
        }
        // impacto atrás da floresta
        if (lt >= 7f) {
            val flash = exp(-(lt - 7f) * 1.6f)
            Gfx.glow(c, 1560f, 690f, 120f + 900f * flash, 0xFFF0C0, flash)
            val amb = smoothSeg(lt, 7f, 8.5f) * (0.30f + 0.12f * sin(gt * 2.5f))
            Gfx.glow(c, 1560f, 710f, 320f, 0xFFB347, amb)
        }

        Gfx.hills(c, 720f, 40f, 0.0045f, 0.5f, 18f, 0.011f, 2f, 0x1B1F4A, -pan * 0.15f)

        // vila
        val mox = -pan * 0.35f
        Gfx.hills(c, 800f, 26f, 0.0035f, 1.2f, 12f, 0.009f, 0.3f, 0x151843, mox)
        val hxs = floatArrayOf(120f, 380f, 690f, 1010f, 1330f, 1650f)
        for (i in hxs.indices) {
            val sx = hxs[i] + mox
            val by = Gfx.hillY(sx, 800f, 26f, 0.0035f, 1.2f, 12f, 0.009f, 0.3f, mox) + 14f
            Gfx.house(c, sx, by, 90f + hash(i, 3) * 50f, 70f + hash(i, 4) * 40f, 0x0E1130, 0x0A0C26, gt, i + 1, i == 2)
        }

        Gfx.hills(c, 890f, 10f, 0.003f, 0f, 6f, 0.012f, 1f, 0x0A1128, -pan * 0.6f)

        // Lia: observa o céu, aponta para a estrela e depois segue para a floresta
        val lx = 520f + 480f * smoothSeg(lt, 10f, 14f)
        val lxPrev = 520f + 480f * smoothSeg(lt - 0.04f, 10f, 14f)
        val raise = smoothSeg(lt, 4.2f, 5.0f) * (1f - smoothSeg(lt, 8f, 9f))
        Gfx.girl(c, lx, 930f, 1.15f, 1f, (lx - 520f) / 45f, stepAmp(lx, lxPrev), true, raise = raise)
        Gfx.oval(c, lx + 30f, 948f, 230f, 30f, 0xFFC25A, 0.18f)
        Gfx.glow(c, Gfx.lanX, Gfx.lanY, 250f, 0xFFC25A, 0.55f + 0.05f * sin(gt * 7f))

        grass(c, pan * 0.9f, -100f, VW + 200f, 985f, 1075f, 46, 0x060A18, gt)
    }

    // ------------------------------------------------------------------ cena 2: a floresta

    private fun floresta(c: Canvas, lt: Float, gt: Float) {
        val cam = lt * 135f
        Gfx.sky(c, 0x060A1F, 0x1E1F4F)
        Gfx.starfield(c, gt, 0.75f)
        Gfx.moon(c, 1500f - cam * 0.02f, 170f, 56f)
        Gfx.hills(c, 650f, 36f, 0.004f, 0.3f, 14f, 0.012f, 1f, 0x141A3E, -cam * 0.08f)
        trees(c, cam * 0.25f, 150f, 760f, 330f, 110f, 0x101536, 31)

        // a luz dourada ao longe
        val gl = smoothSeg(lt, 8f, 18f)
        Gfx.glow(c, 1780f, 720f, 220f + lt * 14f, 0xFFB347, (0.15f + 0.45f * gl) * (0.85f + 0.15f * sin(gt * 3f)))

        Gfx.gradientRect(c, 0f, 600f, VW, 840f, 0x6A7FBF, 0x6A7FBF, 0f, 0.16f)
        trees(c, cam * 0.5f, 210f, 850f, 520f, 140f, 0x0B1029, 41)

        // coruja
        val oi = 8
        val ox = oi * 210f + (hash(oi, 41) - 0.5f) * 210f * 0.7f - cam * 0.5f
        val oh = 520f + hash(oi, 42) * 140f
        val oy = 850f + (hash(oi, 43) - 0.5f) * 20f - oh * 0.62f
        if (pmod(gt, 3.7f) < 0.18f) {
            Gfx.line(c, ox - 24f, oy, ox - 8f, oy, 3f, 0xFFE08A, 0.8f)
            Gfx.line(c, ox + 8f, oy, ox + 24f, oy, 3f, 0xFFE08A, 0.8f)
        } else {
            for (s in 0..1) {
                val ex = ox + (if (s == 0) -16f else 16f)
                Gfx.glow(c, ex, oy, 30f, 0xFFE08A, 0.55f)
                Gfx.circle(c, ex, oy, 9f, 0xFFE08A)
                Gfx.circle(c, ex, oy, 4f, 0x1A1000)
            }
        }

        // chão
        Gfx.gradientRect(c, 0f, 850f, VW, VH, 0x16203F, 0x070B18, 1f, 1f)
        Gfx.rect(c, 0f, 905f, VW, 985f, 0x1C2646, 0.55f)
        grass(c, cam, -100f, VW + 200f, 870f, 905f, 40, 0x1A2650, gt)

        // Lia caminhando
        val lx = 600f
        Gfx.oval(c, lx + 40f, 962f, 290f, 40f, 0xFFC25A, 0.22f)
        Gfx.girl(c, lx, 945f, 1.2f, 1f, cam / 46f, smoothSeg(lt, 0f, 1f), true)
        Gfx.glow(c, Gfx.lanX, Gfx.lanY, 330f, 0xFFC25A, 0.5f + 0.06f * sin(gt * 7f))

        fireflies(c, gt, cam, 36, 480f, 420f)

        // primeiro plano: capim e troncos que passam ao largo
        grass(c, cam, -100f, VW + 200f, 1010f, 1078f, 60, 0x03050C, gt)
        val sp = 760f
        val first = floor(cam / sp).toInt() - 1
        for (k in 0 until 5) {
            val i = first + k
            val x = i * sp + hash(i, 61) * 200f - cam
            val a = smoothSeg(abs(x - 650f), 330f, 620f)
            if (a > 0.01f) {
                val w = 36f + hash(i, 62) * 24f
                Gfx.rect(c, x - w, 0f, x + w, VH, 0x03050C, a)
            }
        }
    }

    // ------------------------------------------------------------------ cena 3: a clareira

    private fun clX(lt: Float): Float =
        lerp(-150f, 860f, smoothSeg(lt, 0.3f, 5.5f)) + 230f * smoothSeg(lt, 6.2f, 8.2f)

    private fun clareira(c: Canvas, lt: Float, gt: Float) {
        Gfx.sky(c, 0x0A1030, 0x2A2760)
        Gfx.starfield(c, gt, 1f)
        Gfx.moon(c, 960f, 190f, 90f)
        Gfx.poly(c, 0xBFD4FF, 0.05f, 880f, 200f, 1040f, 200f, 1500f, 900f, 640f, 900f)
        Gfx.poly(c, 0xBFD4FF, 0.04f, 920f, 200f, 1000f, 200f, 1250f, 900f, 780f, 900f)

        // anel de árvores
        for (i in 0..6) {
            Gfx.pine(c, 40f + i * 95f + hash(i, 51) * 20f, 810f + hash(i, 52) * 20f, 380f + hash(i, 53) * 160f, 0x0C112E)
            Gfx.pine(c, VW - 40f - i * 95f - hash(i, 54) * 20f, 810f + hash(i, 55) * 20f, 380f + hash(i, 56) * 160f, 0x0C112E)
        }
        Gfx.gradientRect(c, 0f, 820f, VW, VH, 0x14213A, 0x0A1224, 1f, 1f)
        grass(c, 0f, -100f, VW + 200f, 835f, 1060f, 70, 0x20406A, gt)

        // flores que brilham
        for (i in 0 until 14) {
            val fx = 380f + hash(i, 57) * 1160f
            val fy = 900f + hash(i, 58) * 140f
            val col = if (i % 2 == 0) 0x8CC8FF else 0xFF9AC8
            Gfx.glow(c, fx, fy, 22f, col, 0.35f + 0.15f * sin(gt * 2f + i))
            Gfx.circle(c, fx, fy, 3.5f, col)
            Gfx.line(c, fx, fy, fx, fy + 16f, 2f, 0x20406A)
        }

        // personagem e estrela
        val lx = clX(lt)
        val kneel = smoothSeg(lt, 8.4f, 9.4f) - smoothSeg(lt, 12f, 13f)
        val carry = smoothSeg(lt, 9.0f, 10.0f)
        val walk = lx / 46f
        val amp = stepAmp(lx, clX(lt - 0.04f))
        val pick = smoothSeg(lt, 9.8f, 11.2f)
        val awake = smoothSeg(lt, 10.6f, 11.6f)
        val bright = lerp(0.12f + 0.08f * sin(gt * 2.2f), 0.7f, smoothSeg(lt, 10.4f, 12.5f))

        Gfx.oval(c, lx + 30f, 962f, 250f, 34f, 0xFFC25A, 0.2f)
        Gfx.girl(c, lx, 950f, 1.2f, 1f, walk, amp, true, carry = carry, kneel = kneel)
        Gfx.glow(c, Gfx.lanX, Gfx.lanY, 300f, 0xFFC25A, 0.5f + 0.05f * sin(gt * 7f))

        val gx = 1230f
        val gy = 880f
        val sx = lerp(gx, Gfx.handX + 8f, pick)
        val sy = lerp(gy, Gfx.handY - 30f, pick) - sin(pick * PI.toFloat()) * 70f
        val sr = lerp(58f, 42f, pick)
        val rot = lerp(-0.35f, sin(gt * 0.8f) * 0.15f, pick)
        Gfx.oval(c, sx, 905f, 150f * (0.6f + bright), 26f, 0xFFD966, 0.25f * bright + 0.05f)
        Gfx.starCreature(c, sx, sy, sr, rot, bright, awake, gt)

        fireflies(c, gt, 0f, 18, 500f, 380f)
        Gfx.pine(c, 60f, 1010f, 800f, 0x05071A)
        Gfx.pine(c, VW - 70f, 1020f, 760f, 0x05071A)
    }

    // ------------------------------------------------------------------ cena 4: a colina

    private fun colina(c: Canvas, lt: Float, gt: Float) {
        val scroll = lt * 100f
        Gfx.sky(c, 0x070B26, 0x2E2A63)
        Gfx.starfield(c, gt, 1f)
        Gfx.moon(c, 260f, 180f, 60f)
        constellation(c, gt, 0f, 0.55f + 0.35f * seg(lt, 0f, 14f))
        Gfx.hills(c, 700f + lt * 4f, 36f, 0.004f, 0.8f, 14f, 0.012f, 1f, 0x1A1B4A, -scroll * 0.1f)
        Gfx.hills(c, 800f + lt * 6f, 30f, 0.0035f, 2.1f, 12f, 0.01f, 0.4f, 0x121440, -scroll * 0.2f)

        // o chão sobe: giramos o mundo -10°
        c.save()
        c.rotate(-10f, 700f, 930f)
        Gfx.rect(c, -2500f, 930f, 4500f, 3200f, 0x0C1330)
        Gfx.rect(c, -2500f, 930f, 4500f, 948f, 0x1B2A4D)
        for (i in -2..9) {
            val sx = 700f + i * 380f + hash(i, 71) * 120f - scroll
            if (hash(i + 100, 72) > i * 0.1f && sx > -250f && sx < 2300f) {
                Gfx.pine(c, sx, 935f, 300f + hash(i, 73) * 200f, 0x080B22)
            }
        }
        grass(c, scroll, -400f, 2800f, 950f, 1030f, 70, 0x14224A, gt)
        for (i in 0 until 16) {
            val x = pmod(hash(i, 82) * 2800f - scroll, 2800f) - 400f
            Gfx.oval(c, x, 960f + hash(i, 83) * 40f, 16f + hash(i, 84) * 14f, 8f, 0x1B2A4D)
        }

        val bright = 0.45f + 0.5f * seg(lt, 0f, 14f)
        // Lia fica em pé na vertical, inclinada um pouco para a frente (+14° compensa os -10°)
        c.save()
        c.rotate(14f, 700f, 930f)
        Gfx.oval(c, 760f, 944f, 200f + 260f * bright, 34f, 0xFFD966, 0.18f + 0.1f * bright)
        Gfx.girl(c, 700f, 930f, 1.15f, 1f, scroll / 46f, 1f, true, carry = 1f)
        Gfx.glow(c, Gfx.lanX, Gfx.lanY, 230f, 0xFFC25A, 0.4f)
        Gfx.starCreature(c, Gfx.handX + 8f, Gfx.handY - 30f, 42f, sin(gt * 0.8f) * 0.15f, bright, 1f, gt)
        c.restore()
        c.restore()
    }

    // ------------------------------------------------------------------ cena 5: o topo

    private fun aurora(c: Canvas, gt: Float, amt: Float) {
        if (amt <= 0f) return
        val cols = intArrayOf(0x5DFFB0, 0xA070FF, 0x4DB8FF)
        val n = 64
        val ys = FloatArray(n + 1)
        for (r in 0..2) {
            val base = 200f + r * 90f
            for (i in 0..n) {
                val x = i * (VW / n)
                ys[i] = base + sin(x * 0.004f + gt * 0.4f + r * 1.7f) * 70f + sin(x * 0.011f + gt * 0.7f + r) * 22f
            }
            for (layer in 0..3) {
                val h = 60f + layer * 50f
                val path = Gfx.path
                path.reset()
                path.moveTo(0f, ys[0])
                for (i in 1..n) path.lineTo(i * (VW / n), ys[i])
                for (i in n downTo 0) path.lineTo(i * (VW / n), ys[i] + h)
                path.close()
                Gfx.fillPath(c, cols[r], 0.07f * amt)
            }
        }
    }

    private fun topo(c: Canvas, lt: Float, gt: Float) {
        Gfx.sky(c, 0x050824, 0x3A2F78)
        Gfx.starfield(c, gt, 1f)
        val arr = smoothSeg(lt, 10.5f, 14f)
        aurora(c, gt, arr)
        Gfx.moon(c, 260f, 170f, 58f)
        constellation(c, gt, smoothSeg(lt, 10f, 11.5f), 0.95f)
        Gfx.hills(c, 800f, 30f, 0.004f, 1f, 14f, 0.01f, 0.3f, 0x1A1B4A, 0f)
        Gfx.hills(c, 905f, 24f, 0.0032f, 2f, 8f, 0.011f, 1f, 0x0C1230, 0f)

        val lx = 760f
        val ly = Gfx.hillY(lx, 905f, 24f, 0.0032f, 2f, 8f, 0.011f, 1f, 0f) + 14f
        val sc = 1.2f
        val raise = smoothSeg(lt, 3f, 5.5f)
        val wave = sin(gt * 6f) * 16f * smoothSeg(lt, 10.5f, 11.5f)

        Gfx.oval(c, lx + 30f, ly + 14f, 240f, 30f, 0xFFD966, 0.2f)
        Gfx.girl(c, lx, ly, sc, 1f, 0f, 0f, true, carry = 1f, raise = raise, wave = wave)
        Gfx.glow(c, Gfx.lanX, Gfx.lanY, 240f, 0xFFC25A, 0.45f)

        // a estrelinha: nas mãos -> voa -> assume seu lugar na constelação
        val gx = cxs[3]
        val gy = cys[3]
        val sx: Float
        val sy: Float
        val sr: Float
        if (lt < 6f) {
            sx = Gfx.handX + 8f
            sy = Gfx.handY - 34f
            sr = 44f
        } else {
            Gfx.girlHand(lx, ly, sc, 1f, 1f, 1f, 0f, 0f)
            val x0 = Gfx.handX + 8f
            val y0 = Gfx.handY - 34f
            val p = smoothSeg(lt, 6f, 10f)
            if (p > 0f && p < 1f) {
                for (k in 1..10) {
                    val pk = max(0f, p - k * 0.025f)
                    val f = 1f - k / 11f
                    Gfx.sparkle(c, bez(x0, 880f, gx, pk), bez(y0, 60f, gy, pk), 10f * f + 3f, 0xFFF3B0, f * 0.9f)
                }
            }
            sx = bez(x0, 880f, gx, p)
            sy = bez(y0, 60f, gy, p)
            sr = lerp(44f, 30f, p)
        }
        Gfx.starCreature(c, sx, sy, sr, gt * 0.5f, 0.95f, 1f, gt)

        // explosão de brilho quando chega ao céu
        if (lt >= 10f && lt < 13f) {
            val bp = seg(lt, 10f, 13f)
            val inv = 1f - bp
            Gfx.glow(c, gx, gy, 120f + 520f * bp, 0xFFF0C0, inv * inv)
            for (k in 0 until 16) {
                val ang = k * (2f * PI.toFloat() / 16f)
                val dist = 30f + 320f * smooth(bp)
                Gfx.sparkle(c, gx + cos(ang) * dist, gy + sin(ang) * dist, 5f + 16f * inv, 0xFFF3B0, inv)
            }
        }

        grass(c, 0f, -100f, VW + 200f, 985f, 1075f, 46, 0x060A18, gt)

        // FIM
        val fa = smoothSeg(lt, 14f, 15.2f)
        if (fa > 0f) {
            text(c, "FIM", 960f, 560f, 150f, 0xFFF3D6, fa, 0.3f)
        }
    }
}
