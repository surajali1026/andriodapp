package com.tradingview.binancefutures.presentation.ui.watchlist

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tradingview.binancefutures.domain.model.formatMarketPrice
import com.tradingview.binancefutures.domain.model.TradingPair
import com.tradingview.binancefutures.presentation.ui.theme.*
import java.util.Locale

enum class WatchlistTab {
    ALL, WATCHLIST, TOP_100, GAINERS, LOSERS
}

@Composable
fun WatchlistDialogCompose(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    pairs: List<TradingPair>,
    currentSymbol: String,
    favorites: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onSelectSymbol: (String) -> Unit
) {
    if (!isOpen) return

    var selectedTab by remember { mutableStateOf(WatchlistTab.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredList = remember(pairs, selectedTab, searchQuery, favorites) {
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().uppercase()
            // When user is searching, search ALL Binance Futures coins across symbol and base asset!
            pairs.filter { it.symbol.contains(q) || it.baseAsset.contains(q) }
        } else {
            when (selectedTab) {
                WatchlistTab.ALL -> pairs
                WatchlistTab.WATCHLIST -> pairs.filter { favorites.contains(it.symbol) }.sortedByDescending { it.quoteVolume }
                WatchlistTab.TOP_100 -> pairs.sortedByDescending { it.quoteVolume }.take(100)
                WatchlistTab.GAINERS -> pairs.sortedByDescending { it.priceChangePercent }
                WatchlistTab.LOSERS -> pairs.sortedBy { it.priceChangePercent }
            }
        }
    }

    // Modal Drawer Overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss)
    ) {
        // Slide-out Drawer Panel from Right
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(min = 280.dp, max = 360.dp)
                .fillMaxWidth(0.85f)
                .align(Alignment.CenterEnd)
                .clickable(enabled = false) {}, // prevent click-through to dismiss
            color = TVSurface,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // Drawer Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Watchlist",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${filteredList.size} USD-M Pairs",
                            color = TVTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search coin (BTC, ETH, SOL)...", color = TVTextSecondary, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TVTextSecondary, modifier = Modifier.size(18.dp)) },
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(48.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 4 Category Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf(
                        WatchlistTab.ALL to "All (${pairs.size})",
                        WatchlistTab.WATCHLIST to "Favs",
                        WatchlistTab.TOP_100 to "Top 100",
                        WatchlistTab.GAINERS to "Gainers",
                        WatchlistTab.LOSERS to "Losers"
                    )

                    tabs.forEach { (tab, label) ->
                        val isSelected = selectedTab == tab
                        Surface(
                            onClick = { selectedTab = tab },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) TVPrimaryBlue else TVBackground,
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else TVTextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("PAIR", color = TVTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("LAST / 24H", color = TVTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(color = TVBorder, thickness = 1.dp)

                // Pairs List
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredList, key = { it.symbol }) { pair ->
                        val isFav = favorites.contains(pair.symbol)
                        val isBull = pair.priceChangePercent >= 0f
                        val isSelected = pair.symbol == currentSymbol

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectSymbol(pair.symbol)
                                    onDismiss()
                                }
                                .background(if (isSelected) TVPrimaryBlue.copy(alpha = 0.18f) else Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = { onToggleFavorite(pair.symbol) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Favorite",
                                        tint = if (isFav) Color(0xFFFFB300) else TVTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = pair.baseAsset,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = " USDT",
                                            color = TVTextSecondary,
                                            fontSize = 10.sp
                                        )
                                    }
                                    Text(
                                        text = "Vol $${String.format(Locale.US, "%.1f", pair.quoteVolume / 1_000_000f)}M",
                                        color = TVTextSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formatMarketPrice(pair.price),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = String.format(Locale.US, "%s%.2f%%", if (isBull) "+" else "", pair.priceChangePercent),
                                    color = if (isBull) TVBullishGreen else TVBearishRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
