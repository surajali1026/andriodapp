package com.tradingview.binancefutures.presentation.ui.header

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tradingview.binancefutures.domain.model.formatMarketPrice
import com.tradingview.binancefutures.domain.model.PriceType
import com.tradingview.binancefutures.domain.model.Timeframe
import com.tradingview.binancefutures.domain.model.TradingPair
import com.tradingview.binancefutures.presentation.ui.theme.*
import java.util.Locale

@Composable
fun TradingHeader(
    symbol: String,
    currentPair: TradingPair?,
    timeframe: Timeframe,
    priceType: PriceType,
    alertsCount: Int,
    isFullScreen: Boolean,
    onOpenWatchlist: () -> Unit,
    onSelectTimeframe: (Timeframe) -> Unit,
    onOpenCustomTimeframe: () -> Unit,
    onSelectPriceType: (PriceType) -> Unit,
    onOpenAlerts: () -> Unit,
    onToggleFullScreen: () -> Unit,
    onToggleOrientation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isBull = (currentPair?.priceChangePercent ?: 0f) >= 0f
    val priceColor = if (isBull) TVBullishGreen else TVBearishRed
    val scrollState = rememberScrollState()

    // Auto-scroll to selected timeframe when it changes
    LaunchedEffect(timeframe) {
        // Smooth scroll ensures selected item is centered or in view
        scrollState.animateScrollTo(
            when (timeframe) {
                Timeframe.M1 -> 0
                Timeframe.M3 -> 40
                Timeframe.M5 -> 80
                Timeframe.M9 -> 120
                Timeframe.M15 -> 160
                Timeframe.M30 -> 200
                Timeframe.M45 -> 240
                Timeframe.H1 -> 280
                Timeframe.H2 -> 320
                Timeframe.H4 -> 360
                Timeframe.H6 -> 400
                Timeframe.H8 -> 440
                Timeframe.H12 -> 480
                Timeframe.D1 -> 520
                Timeframe.D3 -> 560
                Timeframe.W1 -> 600
                Timeframe.MO1 -> 640
                else -> 0
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TVSurface)
            .statusBarsPadding()
    ) {
        // Main Top Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Dedicated One-Click Watchlist Button + Symbol Selector + Live Price
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 1-Click Watchlist Drawer Icon Button
                Surface(
                    onClick = onOpenWatchlist,
                    shape = RoundedCornerShape(8.dp),
                    color = TVCard,
                    modifier = Modifier.height(34.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatListBulleted,
                            contentDescription = "Watchlist",
                            tint = TVPrimaryBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Watchlist",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Symbol Pill
                Surface(
                    onClick = onOpenWatchlist,
                    shape = RoundedCornerShape(8.dp),
                    color = TVCard,
                    modifier = Modifier.height(34.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = symbol,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "USD-M",
                            color = TVPrimaryBlue,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .background(TVPrimaryBlue.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                currentPair?.let { pair ->
                    Column {
                        Text(
                            text = formatMarketPrice(pair.price),
                            color = priceColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = String.format(Locale.US, "%s%.2f%%", if (isBull) "+" else "", pair.priceChangePercent),
                            color = priceColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Right: Full-screen Toggle + Alerts Button + Orientation
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // One-click Full-screen button
                IconButton(
                    onClick = onToggleFullScreen,
                    modifier = Modifier
                        .size(34.dp)
                        .background(if (isFullScreen) TVPrimaryBlue.copy(alpha = 0.25f) else TVCard, RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = "Full Screen",
                        tint = if (isFullScreen) TVPrimaryBlue else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Alerts Dialog Trigger with active badge
                IconButton(
                    onClick = onOpenAlerts,
                    modifier = Modifier
                        .size(34.dp)
                        .background(TVCard, RoundedCornerShape(8.dp))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Alerts",
                            tint = if (alertsCount > 0) Color(0xFFFF9800) else TVTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        if (alertsCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFFFF9800), RoundedCornerShape(3.dp))
                                    .align(Alignment.TopEnd)
                            )
                        }
                    }
                }

                // Screen Rotation Toggle
                IconButton(
                    onClick = onToggleOrientation,
                    modifier = Modifier
                        .size(34.dp)
                        .background(TVCard, RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.ScreenRotation,
                        contentDescription = "Rotate Screen",
                        tint = TVTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Secondary Row: Timeframe Horizontal Scroller & Single-Click Price Type Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Horizontal Scrollable Timeframe Bar (Single row, smooth touch scroll)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val commonTfs = listOf(
                    Timeframe.M1, Timeframe.M3, Timeframe.M5,
                    Timeframe.M15, Timeframe.M30, Timeframe.H1,
                    Timeframe.H2, Timeframe.H4, Timeframe.H6,
                    Timeframe.H8, Timeframe.H12, Timeframe.D1,
                    Timeframe.D3, Timeframe.W1, Timeframe.MO1
                )

                commonTfs.forEach { tf ->
                    val isSelected = tf == timeframe
                    Surface(
                        onClick = { onSelectTimeframe(tf) },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) TVPrimaryBlue else Color.Transparent,
                        modifier = Modifier.height(26.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tf.displayName,
                                color = if (isSelected) Color.White else TVTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                // Custom Timeframe Button
                Surface(
                    onClick = onOpenCustomTimeframe,
                    shape = RoundedCornerShape(6.dp),
                    color = TVCard,
                    modifier = Modifier.height(26.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+ Custom",
                            color = TVTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Single-Click Price Type Switcher (LAST, MARK, INDEX)
            Row(
                modifier = Modifier
                    .background(TVBackground, RoundedCornerShape(6.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                listOf(PriceType.LAST, PriceType.MARK, PriceType.INDEX).forEach { type ->
                    val isActive = priceType == type
                    Surface(
                        onClick = { onSelectPriceType(type) },
                        shape = RoundedCornerShape(4.dp),
                        color = if (isActive) TVPrimaryBlue else Color.Transparent,
                        modifier = Modifier.height(22.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = type.name,
                                color = if (isActive) Color.White else TVTextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
