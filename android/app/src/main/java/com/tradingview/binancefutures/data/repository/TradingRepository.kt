package com.tradingview.binancefutures.data.repository

import com.tradingview.binancefutures.data.remote.BinanceRemoteDataSource
import com.tradingview.binancefutures.data.remote.BybitRemoteDataSource
import com.tradingview.binancefutures.domain.model.Candle
import com.tradingview.binancefutures.domain.model.Timeframe
import com.tradingview.binancefutures.domain.model.TradingPair
import kotlinx.coroutines.flow.Flow

interface TradingRepository {
    suspend fun getFuturesTradingPairs(): List<TradingPair>
    suspend fun getHistoricalCandles(
        symbol: String,
        timeframe: Timeframe,
        priceType: com.tradingview.binancefutures.domain.model.PriceType = com.tradingview.binancefutures.domain.model.PriceType.LAST
    ): List<Candle>
    fun getLiveCandleStream(symbol: String, timeframe: Timeframe): Flow<Candle>
    suspend fun getServerTime(): Long
}

/**
 * Binance is used only for the futures watchlist/universe.
 * All chart history and live candle updates come from Bybit.
 */
class TradingRepositoryImpl(
    private val binanceWatchlist: BinanceRemoteDataSource = BinanceRemoteDataSource(),
    private val bybitChart: BybitRemoteDataSource = BybitRemoteDataSource()
) : TradingRepository {
    override suspend fun getFuturesTradingPairs(): List<TradingPair> =
        binanceWatchlist.getFuturesTickers()

    override suspend fun getHistoricalCandles(
        symbol: String,
        timeframe: Timeframe,
        priceType: com.tradingview.binancefutures.domain.model.PriceType
    ): List<Candle> = bybitChart.getHistoricalKlines(symbol, timeframe)

    override fun getLiveCandleStream(symbol: String, timeframe: Timeframe): Flow<Candle> =
        bybitChart.observeLiveKline(symbol, timeframe)

    override suspend fun getServerTime(): Long = System.currentTimeMillis()
}
