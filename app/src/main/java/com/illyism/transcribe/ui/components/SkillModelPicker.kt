package com.illyism.transcribe.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.illyism.transcribe.data.SkillModelTier
import kotlin.random.Random

/**
 * Cursor-style skills model picker: Advanced quality slider + pill selector.
 * Tiers: Luna → Terra → Sol.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillModelPicker(
    selected: SkillModelTier,
    onSelected: (SkillModelTier) -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = true
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    var menuOpen by remember { mutableStateOf(false) }
    var sliderValue by remember(selected) { mutableFloatStateOf(selected.sliderValue) }
    val scheme = MaterialTheme.colorScheme
    val trackColor = scheme.primary

    Column(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(scheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true),
                            onClick = { expanded = false }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Advanced",
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = trackColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                SparkleSlider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = {
                        val tier = SkillModelTier.fromSlider(sliderValue)
                        sliderValue = tier.sliderValue
                        onSelected(tier)
                    },
                    valueRange = 0f..SkillModelTier.entries.lastIndex.toFloat(),
                    steps = SkillModelTier.entries.size - 2,
                    trackColor = trackColor
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SkillModelTier.entries.forEach { tier ->
                        Text(
                            tier.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (tier == selected) trackColor else scheme.onSurfaceVariant,
                            fontWeight = if (tier == selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (!expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickable { expanded = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Advanced",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant
                )
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(scheme.surfaceVariant)
                    .clickable { menuOpen = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = scheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    selected.label,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    selected.qualityLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = "Choose model",
                    tint = scheme.onSurfaceVariant
                )
            }

            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                SkillModelTier.entries.forEach { tier ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Bolt,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (tier == selected) trackColor else scheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        tier.label,
                                        fontWeight = if (tier == selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        tier.qualityLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    "${tier.subtitle} · ${tier.modelId}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            menuOpen = false
                            sliderValue = tier.sliderValue
                            onSelected(tier)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SparkleSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    trackColor: Color
) {
    val scheme = MaterialTheme.colorScheme
    val sparkles = remember {
        List(18) {
            Sparkle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                radius = 1.2f + Random.nextFloat() * 2.2f,
                alpha = 0.25f + Random.nextFloat() * 0.55f
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
        contentAlignment = Alignment.Center
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
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
                        .size(22.dp)
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
                        .height(14.dp)
                        .clip(RoundedCornerShape(50))
                ) {
                    // Inactive track
                    drawRoundRect(
                        color = scheme.outline.copy(alpha = 0.35f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                    )
                    // Active track with soft gradient
                    val activeWidth = size.width * fraction
                    if (activeWidth > 0f) {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    trackColor.copy(alpha = 0.85f),
                                    trackColor
                                )
                            ),
                            size = androidx.compose.ui.geometry.Size(activeWidth, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                        )
                        // Sparkles inside the filled portion
                        sparkles.forEach { s ->
                            val x = s.x * activeWidth
                            if (x in 0f..activeWidth) {
                                drawCircle(
                                    color = Color.White.copy(alpha = s.alpha),
                                    radius = s.radius,
                                    center = Offset(x, size.height * s.y)
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}

private data class Sparkle(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float
)
