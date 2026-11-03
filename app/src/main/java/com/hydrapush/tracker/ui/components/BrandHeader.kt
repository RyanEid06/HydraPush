package com.hydrapush.tracker.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.ic_hydra_drop),
                        contentDescription = "HydraPush Drop",
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Hydra",
                        color = Color(0xFF0F3E3B),
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Push",
                        color = HydraColors.PrimaryTeal,
                        fontSize = 25.sp,
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

            // Athlete runner decoration
            Image(
                painter = painterResource(R.drawable.header_runner),
                contentDescription = null,
                modifier = Modifier
                    .width(96.dp)
                    .height(68.dp),
                contentScale = ContentScale.Fit
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
