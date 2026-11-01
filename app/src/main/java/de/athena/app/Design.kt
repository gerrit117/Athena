package de.athena.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.*

/** Semantic tokens; individual cards and visualizers do not own the app palette. */
data class AthenaTheme(val id: String, val label: String, val colors: ColorScheme)
object ThemeRegistry {
    val themes = listOf(
        AthenaTheme("light", "Athena Light", lightColorScheme(primary = Color(0xFF1976D2), onPrimary = Color.White,
            primaryContainer = Color(0xFFE5F0FC), onPrimaryContainer = Color(0xFF153C64), background = Color(0xFFF5F6F8),
            surface = Color.White, surfaceVariant = Color(0xFFECF0F5), onSurface = Color(0xFF17283C), onSurfaceVariant = Color(0xFF617084), outlineVariant = Color(0xFFE1E6ED))),
        AthenaTheme("dark", "Athena Dark", darkColorScheme(primary = Color(0xFF80BAF5), onPrimary = Color(0xFF092D51),
            primaryContainer = Color(0xFF233C57), onPrimaryContainer = Color(0xFFCAE4FF), background = Color(0xFF121820),
            surface = Color(0xFF1C2530), surfaceVariant = Color(0xFF263340), onSurface = Color(0xFFEDF1F6), onSurfaceVariant = Color(0xFFA7B5C5), outlineVariant = Color(0xFF354353)))
    )
    fun find(id: String) = themes.firstOrNull { it.id == id } ?: themes.first()
}
data class VisualizerInput(val phase: VoicePhase, val level: Float?, val reducedMotion: Boolean)
interface VoiceVisualizer {
    val id: String
    val label: String
    @Composable fun Render(input: VisualizerInput, modifier: Modifier)
}
object VisualizerRegistry {
    val renderers: List<VoiceVisualizer> = listOf(WaveVisualizer, PulseVisualizer)
    fun find(id: String) = renderers.firstOrNull { it.id == id } ?: WaveVisualizer
}
private object WaveVisualizer: VoiceVisualizer {
    override val id = "wave"; override val label = "Wellenform"
    @Composable override fun Render(input: VisualizerInput, modifier: Modifier) {
        val active = input.phase in setOf(VoicePhase.LISTENING, VoicePhase.THINKING, VoicePhase.SPEAKING)
        val time by rememberInfiniteTransition(label = "wave").animateFloat(0f, (PI * 2).toFloat(), infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "time")
        val color = MaterialTheme.colorScheme.primary
        val background = MaterialTheme.colorScheme.primaryContainer
        Canvas(modifier) {
            val radius = size.minDimension * .46f
            drawCircle(background, radius)
            val t = if (input.reducedMotion || !active) 0f else time
            for (i in 0..6) {
                // This is a phase animation. It does not pretend to measure sound when no level is supplied.
                val envelope = 1f - abs(i - 3) / 4f
                val motion = if (!active) .12f else .38f + .45f * abs(sin(t + i * .6f))
                val h = radius * (0.18f + envelope * motion)
                val w = radius * .12f
                drawRoundRect(color, Offset(center.x + (i - 3) * radius * .24f - w / 2, center.y - h / 2), Size(w, h), CornerRadius(w / 2))
            }
        }
    }
}
private object PulseVisualizer: VoiceVisualizer {
    override val id = "pulse"; override val label = "Sanfter Kreis"
    @Composable override fun Render(input: VisualizerInput, modifier: Modifier) {
        val active = input.phase in setOf(VoicePhase.LISTENING, VoicePhase.THINKING, VoicePhase.SPEAKING)
        val progress by rememberInfiniteTransition(label = "pulse").animateFloat(.72f, 1f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "radius")
        val color = MaterialTheme.colorScheme.primary; val bg = MaterialTheme.colorScheme.primaryContainer
        Canvas(modifier) {
            val r = size.minDimension * .43f
            drawCircle(bg, r)
            val factor = if (input.reducedMotion || !active) .72f else progress
            drawCircle(color.copy(alpha = .2f), r * factor)
            drawCircle(color, r * .35f)
            drawCircle(color.copy(alpha = .5f), r * .7f, style = Stroke(2.dp.toPx()))
        }
    }
}
