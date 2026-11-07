package com.hydrapush.tracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydrapush.tracker.ui.theme.HydraColors
import java.util.Date

@Composable
internal fun HistoryEntryRow(amount: String, timestamp: Long, onRemove: (() -> Unit)? = null) {
    val time = android.text.format.DateFormat.getTimeFormat(LocalContext.current).format(Date(timestamp))
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(amount, Modifier.weight(1f), color = HydraColors.Navy, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(time, color = HydraColors.Muted, fontSize = 13.sp)
        if (onRemove != null) TextButton(onClick = onRemove) { Text("Remove") }
    }
}
