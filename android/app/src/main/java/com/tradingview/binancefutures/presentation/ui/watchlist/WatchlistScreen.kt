package com.tradingview.binancefutures.presentation.ui.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.tradingview.binancefutures.domain.model.TradingPair
import com.tradingview.binancefutures.domain.model.formatMarketPrice
import com.tradingview.binancefutures.presentation.ui.theme.*
import java.util.Locale

private enum class MarketListFilter { ALL, FAVORITES, TOP_100, GAINERS, LOSERS }

@Composable
fun WatchlistScreen(
    pairs: List<TradingPair>,
    currentSymbol: String,
    favorites: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onSelectSymbol: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var filter by remember { mutableStateOf(MarketListFilter.FAVORITES) }
    var query by remember { mutableStateOf("") }
    val visiblePairs = remember(pairs, filter, query, favorites) {
        val source = when (filter) {
            MarketListFilter.ALL -> pairs
            MarketListFilter.FAVORITES -> pairs.filter { it.symbol in favorites }
            MarketListFilter.TOP_100 -> pairs.sortedByDescending { it.quoteVolume }.take(100)
            MarketListFilter.GAINERS -> pairs.sortedByDescending { it.priceChangePercent }.take(100)
            MarketListFilter.LOSERS -> pairs.sortedBy { it.priceChangePercent }.take(100)
        }
        if (query.isBlank()) source else source.filter {
            it.symbol.contains(query.trim(), true) || it.baseAsset.contains(query.trim(), true)
        }
    }
    Column(modifier.fillMaxSize().background(TVBackground)) {
        Text("Markets · USD-M Perpetual", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 10.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Search all Binance Futures pairs", color = TVTextSecondary) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = TVTextSecondary) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = TVSurface, unfocusedContainerColor = TVSurface,
                focusedBorderColor = TVPrimaryBlue, unfocusedBorderColor = TVBorder,
                focusedTextColor = Color.White, unfocusedTextColor = Color.White
            )
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(
                MarketListFilter.FAVORITES to "★",
                MarketListFilter.ALL to "All",
                MarketListFilter.TOP_100 to "Top 100",
                MarketListFilter.GAINERS to "Gainers",
                MarketListFilter.LOSERS to "Losers"
            ).forEach { (item, label) ->
                val selected = filter == item
                Surface(onClick = { filter = item }, color = if (selected) TVPrimaryBlue else TVSurface,
                    shape = RoundedCornerShape(7.dp), modifier = Modifier.weight(1f).height(34.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(label, color = if (selected) Color.White else TVTextSecondary,
                            fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("CONTRACT", color = TVTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("LAST PRICE     24H CHANGE", color = TVTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = TVBorder)
        LazyColumn(Modifier.weight(1f)) {
            items(visiblePairs, key = { it.symbol }) { pair ->
                val positive = pair.priceChangePercent >= 0f
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (pair.symbol == currentSymbol) TVPrimaryBlue.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { onSelectSymbol(pair.symbol) }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onToggleFavorite(pair.symbol) }, modifier = Modifier.size(32.dp)) {
                        Icon(if (pair.symbol in favorites) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Toggle favorite",
                            tint = if (pair.symbol in favorites) Color(0xFFFFC107) else TVTextSecondary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(pair.symbol, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Vol ${String.format(Locale.US, "%.1f", pair.quoteVolume / 1_000_000f)}M",
                            color = TVTextSecondary, fontSize = 10.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatMarketPrice(pair.price), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(String.format(Locale.US, "%+.2f%%", pair.priceChangePercent),
                            color = if (positive) TVBullishGreen else TVBearishRed, fontSize = 11.sp)
                    }
                }
                HorizontalDivider(color = TVBorder.copy(alpha = 0.45f))
            }
        }
        Text("${visiblePairs.size} contracts · Binance USD-M Futures", color = TVTextSecondary,
            fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp))
    }
}
