package com.equinoxtrack.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.equinoxtrack.app.ui.theme.Eq
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** One-shot 0→1 entrance progress for chart drawing. */
@Composable
fun rememberEntrance(key: Any?, durationMs: Int = 900): Float {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(key) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
    }
    return anim.value
}

/* ------------------------------------------------------------------ */
/* Donut                                                               */
/* ------------------------------------------------------------------ */

data class Slice(val value: Float, val color: Color)

@Composable
fun DonutChart(
    slices: List<Slice>,
    modifier: Modifier = Modifier,
    selected: Int? = null,
    onSelect: (Int?) -> Unit = {},
    thickness: Dp = 22.dp,
    center: @Composable BoxScope.() -> Unit = {},
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    val progress = rememberEntrance(slices.size to total)
    val track = Eq.colors.surfaceSunken
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(slices) {
                    detectTapGestures { pos ->
                        if (total <= 0f) return@detectTapGestures
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dist = hypot(pos.x - cx, pos.y - cy)
                        val outer = minOf(size.width, size.height) / 2f
                        if (dist < outer - thickness.toPx() * 1.8f || dist > outer + 8f) { onSelect(null); return@detectTapGestures }
                        var angle = Math.toDegrees(atan2((pos.y - cy).toDouble(), (pos.x - cx).toDouble())).toFloat() + 90f
                        if (angle < 0) angle += 360f
                        var acc = 0f
                        slices.forEachIndexed { i, s ->
                            val sweep = s.value / total * 360f
                            if (angle >= acc && angle < acc + sweep) { onSelect(if (selected == i) null else i); return@detectTapGestures }
                            acc += sweep
                        }
                    }
                }
        ) {
            val stroke = thickness.toPx()
            val d = minOf(size.width, size.height) - stroke * 1.4f
            val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
            drawArc(track, 0f, 360f, false, topLeft, Size(d, d), style = Stroke(stroke))
            if (total <= 0f) return@Canvas
            val gap = if (slices.size > 1) 2.2f else 0f
            var start = -90f
            slices.forEachIndexed { i, s ->
                val sweep = s.value / total * 360f * progress
                val isSel = selected == i
                val dim = selected != null && !isSel
                val w = if (isSel) stroke * 1.35f else stroke
                if (sweep - gap > 0.1f) {
                    drawArc(
                        color = if (dim) s.color.copy(alpha = 0.35f) else s.color,
                        startAngle = start + gap / 2,
                        sweepAngle = sweep - gap,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(d, d),
                        style = Stroke(w, cap = StrokeCap.Round),
                    )
                }
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { Box(content = center) }
    }
}

/* ------------------------------------------------------------------ */
/* Grouped income / expense bars                                       */
/* ------------------------------------------------------------------ */

data class BarGroup(val label: String, val a: Long, val b: Long)

@Composable
fun GroupedBarChart(
    groups: List<BarGroup>,
    modifier: Modifier = Modifier,
    colorA: Color = Eq.colors.income,
    colorB: Color = Eq.colors.expense,
    selected: Int? = null,
    onSelect: (Int?) -> Unit = {},
    showLabels: Boolean = true,
    labelEvery: Int = 1,
) {
    val progress = rememberEntrance(groups)
    val maxV = max(1L, groups.maxOfOrNull { max(it.a, it.b) } ?: 1L).toFloat()
    val grid = Eq.colors.stroke
    val labelColor = Eq.colors.textTertiary
    val highlight = Eq.colors.accentSoft
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(groups) {
                    detectTapGestures { pos ->
                        if (groups.isEmpty()) return@detectTapGestures
                        val slot = size.width / groups.size.toFloat()
                        val idx = (pos.x / slot).toInt().coerceIn(0, groups.lastIndex)
                        onSelect(if (selected == idx) null else idx)
                    }
                }
        ) {
            val h = size.height
            for (k in 0..3) {
                val y = h * k / 3f
                drawLine(grid.copy(alpha = 0.6f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            if (groups.isEmpty()) return@Canvas
            val slot = size.width / groups.size
            val barW = (slot * 0.30f).coerceAtMost(14.dp.toPx())
            val gap = (barW * 0.25f).coerceAtLeast(2f)
            groups.forEachIndexed { i, g ->
                val cx = slot * i + slot / 2
                if (selected == i) {
                    drawRoundRect(highlight, Offset(slot * i + 2f, 0f), Size(slot - 4f, h), CornerRadius(10f, 10f))
                }
                val dim = selected != null && selected != i
                val ha = h * (g.a / maxV) * progress
                val hb = h * (g.b / maxV) * progress
                val r = CornerRadius(barW / 2, barW / 2)
                if (ha > 0f) drawRoundRect(
                    brush = Brush.verticalGradient(listOf(colorA, colorA.copy(alpha = 0.55f)), startY = h - ha, endY = h),
                    topLeft = Offset(cx - gap / 2 - barW, h - ha), size = Size(barW, ha), cornerRadius = r,
                    alpha = if (dim) 0.35f else 1f,
                )
                if (hb > 0f) drawRoundRect(
                    brush = Brush.verticalGradient(listOf(colorB, colorB.copy(alpha = 0.55f)), startY = h - hb, endY = h),
                    topLeft = Offset(cx + gap / 2, h - hb), size = Size(barW, hb), cornerRadius = r,
                    alpha = if (dim) 0.35f else 1f,
                )
            }
        }
        if (showLabels && groups.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                groups.forEachIndexed { i, g ->
                    Text(
                        if (i % labelEvery == 0 || i == groups.lastIndex) g.label else "",
                        style = Eq.type.labelSmall,
                        color = if (selected == i) Eq.colors.textPrimary else labelColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Smooth trend line with gradient fill                                */
/* ------------------------------------------------------------------ */

@Composable
fun TrendLine(
    values: List<Long>,
    modifier: Modifier = Modifier,
    color: Color = Eq.colors.accent,
    selected: Int? = null,
    onSelect: (Int?) -> Unit = {},
    fill: Boolean = true,
    strokeWidth: Dp = 2.5.dp,
) {
    val progress = rememberEntrance(values)
    val dotBg = Eq.colors.surface
    Canvas(
        modifier.pointerInput(values) {
            detectTapGestures { pos ->
                if (values.size < 2) return@detectTapGestures
                val step = size.width / (values.size - 1).toFloat()
                val idx = ((pos.x + step / 2) / step).toInt().coerceIn(0, values.lastIndex)
                onSelect(if (selected == idx) null else idx)
            }
        }
    ) {
        if (values.size < 2) return@Canvas
        val minV = values.minOrNull()!!.toFloat()
        val maxV = values.maxOrNull()!!.toFloat()
        val span = if (maxV - minV == 0f) 1f else maxV - minV
        val pad = 6.dp.toPx()
        val h = size.height - pad * 2
        val step = size.width / (values.size - 1)
        val pts = values.mapIndexed { i, v -> Offset(step * i, pad + h - (v - minV) / span * h) }
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                val p0 = pts[i - 1]
                val p1 = pts[i]
                val cx = (p0.x + p1.x) / 2
                cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
            }
        }
        val clipW = size.width * progress
        drawContext.canvas.save()
        drawContext.canvas.clipRect(0f, 0f, clipW, size.height)
        if (fill) {
            val area = Path().apply {
                addPath(path)
                lineTo(pts.last().x, size.height)
                lineTo(pts.first().x, size.height)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), Color.Transparent)))
        }
        drawPath(path, color, style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round))
        drawContext.canvas.restore()
        selected?.let { idx ->
            if (idx in pts.indices) {
                val p = pts[idx]
                drawLine(color.copy(alpha = 0.35f), Offset(p.x, 0f), Offset(p.x, size.height), strokeWidth = 1.dp.toPx())
                drawCircle(dotBg, 7.dp.toPx(), p)
                drawCircle(color, 4.5.dp.toPx(), p)
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Horizon gauge – the Safe-to-Spend signature                         */
/* ------------------------------------------------------------------ */

/**
 * A half-circle "horizon": the arc fills as the period's discretionary money is used, and a glowing
 * sun marks where today sits on it. Dawn on the left, dusk on the right — an equinox in miniature.
 */
@Composable
fun HorizonGauge(
    spentFraction: Float,
    modifier: Modifier = Modifier,
    timeFraction: Float? = null,
    color: Color = Eq.colors.accent,
    colorAlt: Color = Eq.colors.accentAlt,
    track: Color = Color.White.copy(alpha = 0.10f),
    tickColor: Color = Color.White,
    sunColor: Color = Color.White,
    glowColor: Color = Color.White,
    thickness: Dp = 12.dp,
) {
    val anim by animateFloatAsState(spentFraction.coerceIn(0f, 1f), tween(1100, easing = FastOutSlowInEasing), label = "horizon")
    val sunAnim by animateFloatAsState((timeFraction ?: -1f), tween(1100, easing = FastOutSlowInEasing), label = "sun")
    Canvas(modifier) {
        val stroke = thickness.toPx()
        val w = size.width - stroke * 2
        val radius = minOf(w / 2, size.height - stroke)
        val center = Offset(size.width / 2, stroke + radius)
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2, radius * 2)
        drawArc(track, 180f, 180f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        if (anim > 0.005f) {
            drawArc(
                brush = Brush.sweepGradient(listOf(colorAlt, color, colorAlt), center = center),
                startAngle = 180f, sweepAngle = 180f * anim, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        // tick marks along the inside of the horizon
        for (k in 0..12) {
            val a = PI + PI * k / 12.0
            val r1 = radius - stroke * 1.4f
            val r2 = r1 - (if (k % 6 == 0) stroke * 0.9f else stroke * 0.45f)
            drawLine(
                tickColor.copy(alpha = if (k % 6 == 0) 0.35f else 0.16f),
                Offset(center.x + (r1 * cos(a)).toFloat(), center.y + (r1 * sin(a)).toFloat()),
                Offset(center.x + (r2 * cos(a)).toFloat(), center.y + (r2 * sin(a)).toFloat()),
                strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round,
            )
        }
        if (sunAnim >= 0f) {
            val a = PI + PI * sunAnim.coerceIn(0f, 1f)
            val p = Offset(center.x + (radius * cos(a)).toFloat(), center.y + (radius * sin(a)).toFloat())
            drawCircle(Brush.radialGradient(listOf(glowColor.copy(alpha = 0.55f), Color.Transparent), center = p, radius = stroke * 2.2f), stroke * 2.2f, p)
            drawCircle(sunColor, stroke * 0.62f, p)
        }
    }
}

/* ------------------------------------------------------------------ */
/* Ring progress (goals, budgets)                                      */
/* ------------------------------------------------------------------ */

@Composable
fun RingProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = Eq.colors.accent,
    thickness: Dp = 8.dp,
    track: Color = Eq.colors.surfaceSunken,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = thickness.toPx()
            val d = minOf(size.width, size.height) - s
            val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
            drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(s))
            if (anim > 0f) drawArc(
                brush = Brush.sweepGradient(listOf(color.copy(alpha = 0.6f), color, color.copy(alpha = 0.6f))),
                startAngle = -90f, sweepAngle = 360f * anim, useCenter = false,
                topLeft = tl, size = Size(d, d), style = Stroke(s, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

/** Horizontal stacked bar used for net-worth composition. */
@Composable
fun StackedBar(parts: List<Slice>, modifier: Modifier = Modifier, height: Dp = 12.dp) {
    val total = parts.sumOf { it.value.toDouble() }.toFloat()
    val progress = rememberEntrance(parts.size to total)
    val track = Eq.colors.surfaceSunken
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        if (total <= 0f) return@Canvas
        var x = 0f
        val gap = 3.dp.toPx()
        parts.filter { it.value > 0f }.forEach { p ->
            val w = size.width * (p.value / total) * progress
            if (w - gap > 1f) drawRoundRect(p.color, Offset(x, 0f), Size(w - gap, size.height), r)
            x += w
        }
    }
}

@Composable
fun Sparkline(values: List<Long>, color: Color, modifier: Modifier = Modifier.size(80.dp, 28.dp)) {
    TrendLine(values, modifier, color = color, fill = true, strokeWidth = 2.dp)
}

@Composable
fun ChartLegend(items: List<Pair<Color, String>>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items.forEach { LegendItem(it.first, it.second) }
    }
}
