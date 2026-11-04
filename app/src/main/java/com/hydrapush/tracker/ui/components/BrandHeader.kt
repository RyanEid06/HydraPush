package com.hydrapush.tracker.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydrapush.tracker.R
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
        val resources = LocalContext.current.resources
        val runner = remember(resources) { BrandArtwork.runner(resources).asImageBitmap() }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            val runnerWidth = (maxWidth * .24f / scale).coerceAtLeast(48.dp)
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painterResource(R.drawable.ic_hydra_drop), "HydraPush",
                            Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = HydraColors.DeepTeal)) { append("Hydra") }
                                withStyle(SpanStyle(color = HydraColors.PrimaryTeal)) { append("Push") }
                            },
                            fontSize = 23.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    Text(subtitle, color = HydraColors.Muted, fontSize = 13.sp,
                        modifier = Modifier.padding(start = 34.dp, top = 3.dp))
                }
                Image(
                    runner, null,
                    modifier = Modifier.width(runnerWidth)
                        .heightIn(max = 92.dp),
                    contentScale = ContentScale.Fit
                )
            }
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
                Spacer(Modifier.width(12.dp))
                val formatter = DateTimeFormatter.ofPattern("EEE, MMM d")
                Text(
                    text = date.format(formatter),
                    color = HydraColors.Muted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        } else {
            Spacer(modifier = Modifier.height(12.dp))
        }
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
