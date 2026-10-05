package de.waserlaubt.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import de.waserlaubt.app.osm.SpeedValue

private val SignRed = Color(0xFFD32F2F)
private val SignBlue = Color(0xFF1565C0)
private val SignGrey = Color(0xFF9E9E9E)

/** Zeichnet das Verkehrsschild für [value] in der Größe [size]. */
@Composable
fun SpeedSign(value: SpeedValue, size: Dp, dimmed: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }
    fun fontFor(fraction: Float): TextUnit = with(density) { (sizePx * fraction).toSp() }

    Box(modifier.size(size).alpha(if (dimmed) 0.5f else 1f), contentAlignment = Alignment.Center) {
        when (value) {
            is SpeedValue.Number -> {
                RingSign(SignRed)
                val text = value.value.toString()
                Text(
                    text,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = fontFor(if (text.length >= 3) 0.36f else 0.46f),
                    letterSpacing = (-1).sp,
                )
                if (value.mph) {
                    Text("mph", color = Color.Black, fontSize = fontFor(0.07f),
                        modifier = Modifier.align(Alignment.BottomCenter).alpha(0.8f))
                }
            }
            SpeedValue.None -> Canvas(Modifier.size(size)) {
                val r = this.size.minDimension / 2
                drawCircle(Color.White, r)
                drawCircle(Color.DarkGray, r * 0.97f, style = Stroke(r * 0.04f))
                // Diagonale Streifen wie beim Schild "Ende aller Streckenverbote"
                val step = r * 0.11f
                for (i in -2..2) {
                    val o = i * step
                    drawLine(
                        Color.Black,
                        Offset(center.x - r * 0.62f + o, center.y + r * 0.62f + o),
                        Offset(center.x + r * 0.62f + o, center.y - r * 0.62f + o),
                        strokeWidth = r * 0.045f, cap = StrokeCap.Round,
                    )
                }
            }
            SpeedValue.Walk -> {
                Canvas(Modifier.size(size)) {
                    drawRoundRect(SignBlue, cornerRadius = CornerRadius(this.size.minDimension * 0.08f))
                }
                Text("Schritt-\ntempo", color = Color.White, fontWeight = FontWeight.Bold,
                    fontSize = fontFor(0.16f), textAlign = TextAlign.Center, lineHeight = fontFor(0.18f))
            }
            SpeedValue.Variable -> {
                RingSign(SignGrey)
                Text("Anzeige\nbeachten", color = Color.Black, fontWeight = FontWeight.Bold,
                    fontSize = fontFor(0.1f), textAlign = TextAlign.Center)
            }
            SpeedValue.Unknown -> {
                RingSign(SignGrey)
                Text("?", color = SignGrey, fontWeight = FontWeight.Bold, fontSize = fontFor(0.46f))
            }
        }
    }
}

@Composable
private fun BoxScope.RingSign(ring: Color) {
    Canvas(Modifier.matchParentSize()) {
        val r = size.minDimension / 2
        drawCircle(Color.White, r)
        val w = r * 0.2f
        drawCircle(ring, r - w / 2, style = Stroke(w))
    }
}
