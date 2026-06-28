package com.shohan.cleanspace.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

data class DonutSlice(val value: Float, val color: Color, val label: String)

@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    centerLabel: String = "",
    centerSubLabel: String = ""
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(0.001f)

    val progress = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(durationMillis = 900))
    }

    // Fix 4a: modifier is applied first (caller controls size),
    // then aspectRatio(1f) ensures square — no more hardcoded size(200.dp) override
    Box(
        modifier = modifier.aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = size.minDimension * 0.10f
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            var startAngle = -90f
            slices.forEach { slice ->
                val sweep = (slice.value / total) * 360f * progress.value
                val coercedSweep = sweep.coerceAtLeast(if (sweep > 0f) 0.5f else 0f)
                drawArc(
                    color = slice.color,
                    startAngle = startAngle,
                    sweepAngle = coercedSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    // Fix 4b: StrokeCap.Butt — slices no longer bleed into each other
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )
                startAngle += coercedSweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = centerLabel, style = MaterialTheme.typography.headlineMedium)
            Text(
                text = centerSubLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
