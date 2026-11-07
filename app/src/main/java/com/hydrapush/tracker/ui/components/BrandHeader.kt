package com.hydrapush.tracker.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
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
    date: LocalDate = LocalDate.now(),
    onEditToday: (() -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val resources = LocalContext.current.resources
        val runner = remember(resources) { BrandArtwork.runner(resources).asImageBitmap() }
        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = if (showDateBar) 112.dp else 94.dp)) {
            val fontScale = LocalDensity.current.fontScale
            val runnerWidth = if (showDateBar) maxWidth * .57f else maxWidth * .45f
            Image(runner, null,
                Modifier.align(Alignment.TopEnd).width(runnerWidth).height(if (showDateBar) 126.dp else 100.dp)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(Brush.verticalGradient(0f to Color.White, .7f to Color.White, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                    },
                contentScale = ContentScale.Fit, alpha = .62f)
            Column(Modifier.fillMaxWidth().padding(top = if (showDateBar) 20.dp else 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.ic_hydra_drop), "HydraPush", Modifier.size(30.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(color = HydraColors.DeepTeal)) { append("Hydra") }
                        withStyle(SpanStyle(color = HydraColors.PrimaryTeal)) { append("Push") }
                    }, fontSize = if (fontScale > 1.15f) 25.sp else 29.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.ExtraBold)
                }
                Text(subtitle, color = HydraColors.Muted, fontSize = 13.sp,
                    modifier = Modifier.padding(start = 36.dp, top = 2.dp))
            }
        }

        if (showDateBar) {
            Spacer(modifier = Modifier.height(4.dp))
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
                VerticalDivider(Modifier.padding(horizontal = 12.dp).height(20.dp), color = HydraColors.Stroke)
                val formatter = DateTimeFormatter.ofPattern("EEE, MMM d")
                Text(
                    text = date.format(formatter),
                    color = HydraColors.Muted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                if (onEditToday != null) IconButton(onClick = onEditToday, modifier = Modifier.testTag("today_logs_button")) {
                    Icon(Icons.Default.Edit, "Edit today's logs", tint = HydraColors.DeepTeal, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
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
