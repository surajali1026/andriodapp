package com.tradingview.binancefutures.presentation.ui.timeframe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tradingview.binancefutures.domain.model.Timeframe
import com.tradingview.binancefutures.presentation.ui.theme.*

@Composable
fun CustomTimeframeDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    currentTimeframe: Timeframe,
    onSelectTimeframe: (Timeframe) -> Unit
) {
    if (!isOpen) return

    var customNumberText by remember { mutableStateOf("10") }
    var selectedUnit by remember { mutableStateOf("m") } // "m", "h", "d"

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
                    Text(
                        text = "Timeframe & Custom Interval",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TVTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Custom Timeframe Creator Section
                Text("ADD CUSTOM TIMEFRAME", color = TVPrimaryBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customNumberText,
                        onValueChange = { customNumberText = it.filter { ch -> ch.isDigit() } },
                        singleLine = true,
                        placeholder = { Text("10", color = TVTextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = TVBackground,
                            unfocusedContainerColor = TVBackground,
                            focusedBorderColor = TVPrimaryBlue,
                            unfocusedBorderColor = TVBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(48.dp)
                    )

                    // Unit Selectors
                    listOf("m" to "Min", "h" to "Hour", "d" to "Day").forEach { (u, label) ->
                        val isSelected = selectedUnit == u
                        Surface(
                            onClick = { selectedUnit = u },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) TVPrimaryBlue else TVBackground,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
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

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val num = customNumberText.toIntOrNull() ?: 1
                        val intervalStr = "${num}${selectedUnit}"
                        val tf = Timeframe.fromInterval(intervalStr)
                        onSelectTimeframe(tf)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TVPrimaryBlue)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Apply Custom Timeframe (${customNumberText}${selectedUnit})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = TVBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // Standard Presets
                Text("STANDARD PRESETS", color = TVTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                val presetGroups = listOf(
                    "MINUTES" to listOf(Timeframe.M1, Timeframe.M3, Timeframe.M5, Timeframe.M15, Timeframe.M30),
                    "HOURS" to listOf(Timeframe.H1, Timeframe.H2, Timeframe.H4, Timeframe.H6, Timeframe.H8, Timeframe.H12),
                    "DAYS / WEEKS" to listOf(Timeframe.D1, Timeframe.D3, Timeframe.W1, Timeframe.MO1)
                )

                presetGroups.forEach { (title, list) ->
                    Text(title, color = TVTextSecondary.copy(alpha = 0.7f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        list.forEach { tf ->
                            val isSelected = tf == currentTimeframe
                            Surface(
                                onClick = {
                                    onSelectTimeframe(tf)
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) TVPrimaryBlue else TVBackground,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = tf.displayName,
                                        color = if (isSelected) Color.White else TVTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
