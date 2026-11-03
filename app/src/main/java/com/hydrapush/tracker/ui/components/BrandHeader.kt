package com.hydrapush.tracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydrapush.tracker.ui.theme.HydraColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun BrandHeader(
    subtitle: String,
    modifier: Modifier = Modifier,
    showDateBar: Boolean = false,
    date: LocalDate = LocalDate.now()
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Water drop icon with teal gradient
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    listOf(HydraColors.Aqua, HydraColors.PrimaryTeal)
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "💧",
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Hydra",
                        color = HydraColors.Navy,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Push",
                        color = HydraColors.PrimaryTeal,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = HydraColors.Muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            // Dynamic athletic running figure silhouette canvas in teal tones
            AthleteSilhouette(
                modifier = Modifier
                    .width(90.dp)
                    .height(68.dp)
            )
        }

        if (showDateBar) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = "Calendar",
                    tint = HydraColors.PrimaryTeal,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Today",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = HydraColors.Navy
                )
                Text(
                    text = "   |   ",
                    color = Color(0xFFD6E8E9),
                    fontSize = 16.sp
                )
                val formatter = DateTimeFormatter.ofPattern("EEE, MMM d")
                Text(
                    text = date.format(formatter),
                    color = HydraColors.Muted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        } else {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun AthleteSilhouette(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Dynamic background speed / accent lines
        val tealAlpha = HydraColors.Aqua.copy(alpha = 0.22f)
        val brightTeal = HydraColors.PrimaryTeal.copy(alpha = 0.45f)

        // Diagonal dynamic speed wedge
        val wedgePath = Path().apply {
            moveTo(w * 0.1f, h * 0.85f)
            lineTo(w * 0.85f, h * 0.15f)
            lineTo(w, h * 0.15f)
            lineTo(w * 0.45f, h * 0.95f)
            close()
        }
        drawPath(wedgePath, color = tealAlpha)

        // Accent speed stripe
        val stripePath = Path().apply {
            moveTo(w * 0.35f, h * 0.9f)
            lineTo(w * 0.95f, h * 0.4f)
            lineTo(w * 0.98f, h * 0.43f)
            lineTo(w * 0.42f, h * 0.95f)
            close()
        }
        drawPath(stripePath, color = brightTeal)

        // Silhouette running runner figure
        val runnerColor = HydraColors.PrimaryTeal.copy(alpha = 0.55f)

        // Head
        drawCircle(
            color = runnerColor,
            radius = h * 0.12f,
            center = Offset(w * 0.65f, h * 0.22f)
        )

        // Torso & running limbs
        val bodyPath = Path().apply {
            // Neck / shoulder
            moveTo(w * 0.62f, h * 0.32f)
            // Forward chest
            lineTo(w * 0.52f, h * 0.45f)
            // Forward leading arm
            lineTo(w * 0.30f, h * 0.52f)
            lineTo(w * 0.33f, h * 0.56f)
            lineTo(w * 0.50f, h * 0.50f)
            // Waist
            lineTo(w * 0.58f, h * 0.65f)
            // Forward running bent leg
            lineTo(w * 0.40f, h * 0.72f)
            lineTo(w * 0.35f, h * 0.88f)
            lineTo(w * 0.39f, h * 0.90f)
            lineTo(w * 0.46f, h * 0.76f)
            // Pelvis
            lineTo(w * 0.62f, h * 0.68f)
            // Trailing back leg
            lineTo(w * 0.82f, h * 0.60f)
            lineTo(w * 0.92f, h * 0.70f)
            lineTo(w * 0.89f, h * 0.73f)
            lineTo(w * 0.78f, h * 0.64f)
            // Back torso & trailing arm
            lineTo(w * 0.66f, h * 0.45f)
            lineTo(w * 0.80f, h * 0.38f)
            lineTo(w * 0.82f, h * 0.42f)
            lineTo(w * 0.66f, h * 0.48f)
            lineTo(w * 0.64f, h * 0.32f)
            close()
        }
        drawPath(bodyPath, color = runnerColor, style = Fill)
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}
