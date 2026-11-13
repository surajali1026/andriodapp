package com.tradingview.binancefutures.presentation.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tradingview.binancefutures.domain.model.AlertCondition
import com.tradingview.binancefutures.domain.model.PriceAlert
import com.tradingview.binancefutures.presentation.ui.theme.*
import java.util.Locale

@Composable
fun AlertsDialogCompose(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    symbol: String,
    currentPrice: Float,
    alerts: List<PriceAlert>,
    onAddAlert: (Float, AlertCondition, Boolean, Boolean) -> Unit,
    onToggleAlert: (String) -> Unit,
    onDeleteAlert: (String) -> Unit
) {
    if (!isOpen) return

    var targetPriceText by remember(currentPrice) {
        mutableStateOf(if (currentPrice > 0f) String.format(Locale.US, "%.2f", currentPrice) else "")
    }
    var selectedCondition by remember { mutableStateOf(AlertCondition.CROSSES_ABOVE) }
    var soundEnabled by remember { mutableStateOf(true) }
    var vibrateEnabled by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = TVSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(0xFFFF9800).copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Price Alerts · $symbol",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Live: ${String.format(Locale.US, "%.2f", currentPrice)} USD",
                                color = TVBullishGreen,
                                fontSize = 11.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TVTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Target Price Input
                Text("Target Price (USD)", color = TVTextSecondary, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = targetPriceText,
                    onValueChange = { targetPriceText = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = TVBackground,
                        unfocusedContainerColor = TVBackground,
                        focusedBorderColor = TVPrimaryBlue,
                        unfocusedBorderColor = TVBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Condition Selection (4 conditions)
                Text("Trigger Condition", color = TVTextSecondary, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            AlertCondition.CROSSES_ABOVE to "Crosses Above",
                            AlertCondition.CROSSES_BELOW to "Crosses Below"
                        ).forEach { (cond, label) ->
                            val isSelected = selectedCondition == cond
                            Surface(
                                onClick = { selectedCondition = cond },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) TVPrimaryBlue else TVBackground,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else TVTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            AlertCondition.CANDLE_CLOSES_ABOVE to "Candle Closes Above",
                            AlertCondition.CANDLE_CLOSES_BELOW to "Candle Closes Below"
                        ).forEach { (cond, label) ->
                            val isSelected = selectedCondition == cond
                            Surface(
                                onClick = { selectedCondition = cond },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) TVPrimaryBlue else TVBackground,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else TVTextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Sound & Vibration Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Checkbox(
                            checked = soundEnabled,
                            onCheckedChange = { soundEnabled = it },
                            colors = CheckboxDefaults.colors(checkedColor = TVPrimaryBlue)
                        )
                        Text("Sound chime", color = Color.White, fontSize = 11.sp)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Checkbox(
                            checked = vibrateEnabled,
                            onCheckedChange = { vibrateEnabled = it },
                            colors = CheckboxDefaults.colors(checkedColor = TVPrimaryBlue)
                        )
                        Text("Vibration", color = Color.White, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        val p = targetPriceText.toFloatOrNull() ?: currentPrice
                        if (p > 0f) {
                            onAddAlert(p, selectedCondition, soundEnabled, vibrateEnabled)
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                ) {
                    Text("Add Alert Dotted Line", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                if (alerts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Active Alerts on Chart (${alerts.size})", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
                        items(alerts, key = { it.id }) { a ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(TVBackground, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Switch(
                                        checked = a.isEnabled && !a.isTriggered,
                                        onCheckedChange = { onToggleAlert(a.id) },
                                        modifier = Modifier.scale(0.75f)
                                    )
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "${a.symbol} ${String.format(Locale.US, "%.2f", a.targetPrice)}",
                                                color = if (a.isTriggered) TVTextSecondary else Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (a.isTriggered) {
                                                Text(
                                                    text = "TRIGGERED",
                                                    color = Color(0xFFFF9800),
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier
                                                        .background(Color(0xFFFF9800).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = a.condition.displayName,
                                            color = TVTextSecondary,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                IconButton(onClick = { onDeleteAlert(a.id) }, modifier = Modifier.size(26.dp)) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = TVBearishRed, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
