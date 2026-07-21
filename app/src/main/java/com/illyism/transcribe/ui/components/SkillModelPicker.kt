package com.illyism.transcribe.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.illyism.transcribe.data.SkillModelTier

/**
 * Model picker: pill shows current tier; tap opens a popup slider (no Advanced header).
 * Dragging updates the $ cost meter and track color (green → red).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillModelPicker(
    selected: SkillModelTier,
    onSelected: (SkillModelTier) -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false
) {
    var popupOpen by remember { mutableStateOf(initiallyExpanded) }
    var sliderValue by remember { mutableFloatStateOf(selected.sliderValue) }
    val sliderInteractionSource = remember { MutableInteractionSource() }
    val dragging by sliderInteractionSource.collectIsDraggedAsState()
    LaunchedEffect(selected) {
        if (!dragging) sliderValue = selected.sliderValue
    }

    val previewTier = SkillModelTier.fromSlider(sliderValue)
    val displayTier = if (popupOpen) previewTier else selected
    val scheme = MaterialTheme.colorScheme
    val trackColor by animateColorAsState(
        targetValue = tierColor(sliderValue),
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "trackColor"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = popupOpen,
            enter = fadeIn(tween(160)) + expandVertically(tween(220)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(180))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .shadow(12.dp, RoundedCornerShape(22.dp), clip = false)
                    .clip(RoundedCornerShape(22.dp))
                    .background(scheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                CostSpeedHeader(tier = previewTier, accent = trackColor)
                Spacer(modifier = Modifier.height(12.dp))
                TierSlider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = {
                        val tier = SkillModelTier.fromSlider(sliderValue)
                        sliderValue = tier.sliderValue
                        onSelected(tier)
                    },
                    trackColor = trackColor,
                    interactionSource = sliderInteractionSource,
                    dragging = dragging
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(scheme.surfaceVariant)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true),
                    onClick = { popupOpen = !popupOpen }
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                displayTier.label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = scheme.onSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                displayTier.qualityLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (popupOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (popupOpen) "Hide model slider" else "Choose model",
                tint = scheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CostSpeedHeader(tier: SkillModelTier, accent: Color) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(5) { index ->
                val active = index < tier.relativeCost
                val scale by animateFloatAsState(
                    targetValue = if (active) 1f else 0.85f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "dollarScale$index"
                )
                val alpha by animateFloatAsState(
                    targetValue = if (active) 1f else 0.22f,
                    animationSpec = tween(180),
                    label = "dollarAlpha$index"
                )
                Text(
                    text = "$",
                    color = accent.copy(alpha = alpha),
                    fontWeight = FontWeight.Bold,
                    fontSize = (18 * scale).sp,
                    modifier = Modifier.padding(end = 1.dp)
                )
            }
        }

        AnimatedContent(
            targetState = tier.speed,
            transitionSpec = {
                (fadeIn(tween(160)) + scaleIn(initialScale = 0.92f)) togetherWith
                    (fadeOut(tween(100)) + scaleOut(targetScale = 0.92f))
            },
            label = "speedLabel"
        ) { speed ->
            Text(
                speed,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = scheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TierSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    trackColor: Color,
    interactionSource: MutableInteractionSource,
    dragging: Boolean
) {
    val scheme = MaterialTheme.colorScheme
    val thumbSize by animateDpAsState(
        targetValue = if (dragging) 34.dp else 28.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "thumbSize"
    )
    val trackHeight = 28.dp
    val stopCount = SkillModelTier.entries.lastIndex
    val valueRange = 0f..SkillModelTier.entries.lastIndex.toFloat()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        contentAlignment = Alignment.Center
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = SkillModelTier.entries.size - 2,
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            ),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(thumbSize)
                        .shadow(if (dragging) 8.dp else 3.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            },
            track = { sliderState ->
                val fraction = (
                    (sliderState.value - valueRange.start) /
                        (valueRange.endInclusive - valueRange.start)
                    ).coerceIn(0f, 1f)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(trackHeight)
                ) {
                    val radius = size.height / 2
                    // Align fill/dots to thumb centers (Material slider inset).
                    val thumbR = 14.dp.toPx()
                    val travelStart = thumbR
                    val travelEnd = size.width - thumbR
                    val travel = (travelEnd - travelStart).coerceAtLeast(1f)
                    val thumbX = travelStart + travel * fraction

                    drawRoundRect(
                        color = scheme.outline.copy(alpha = 0.28f),
                        cornerRadius = CornerRadius(radius)
                    )

                    // Fill through thumb center — no stub capsule at value 0.
                    val activeWidth = thumbX.coerceIn(0f, size.width)
                    if (activeWidth > 1f) {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    trackColor.copy(alpha = 0.35f),
                                    trackColor.copy(alpha = 0.15f)
                                )
                            ),
                            topLeft = Offset(0f, -4f),
                            size = Size(activeWidth.coerceAtLeast(radius), size.height + 8f),
                            cornerRadius = CornerRadius(radius + 4f)
                        )
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    trackColor.copy(alpha = 0.9f),
                                    trackColor
                                )
                            ),
                            size = Size(activeWidth.coerceAtLeast(radius), size.height),
                            cornerRadius = CornerRadius(radius)
                        )
                    }

                    // Interior stops at tier centers 1..(last-1). Hide under the thumb,
                    // and never draw the final interior tick when the thumb is on Extra High
                    // (that rightmost speck against the white knob looked like a glitch).
                    val hideR = thumbR * 1.35f
                    for (i in 1 until stopCount) {
                        val x = travelStart + travel * (i.toFloat() / stopCount)
                        if (i == stopCount - 1 && fraction > 0.8f) continue
                        val dx = if (x >= thumbX) x - thumbX else thumbX - x
                        if (dx < hideR) continue
                        drawCircle(
                            color = Color.White.copy(
                                alpha = if (x <= thumbX) 0.4f else 0.22f
                            ),
                            radius = 3f,
                            center = Offset(x, size.height / 2)
                        )
                    }
                }
            }
        )
    }
}

/** Green (cheap) → amber → orange → red (costly) as the slider moves right. */
private fun tierColor(sliderValue: Float): Color {
    val t = (sliderValue / SkillModelTier.entries.lastIndex.toFloat()).coerceIn(0f, 1f)
    val stops = listOf(
        Color(0xFF34C759), // green
        Color(0xFF30B0C7), // teal
        Color(0xFFE8A838), // amber
        Color(0xFFFF8A3D), // orange
        Color(0xFFFF5A5F)  // red
    )
    val scaled = t * (stops.lastIndex)
    val i = scaled.toInt().coerceIn(0, stops.lastIndex - 1)
    val local = scaled - i
    return lerp(stops[i], stops[i + 1], local)
}
