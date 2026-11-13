package com.tradingview.binancefutures.data.repository

import com.tradingview.binancefutures.data.remote.BinanceRemoteDataSource
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
    fun getLiveCandleStream(symbol: String, timeframe: Timeframe, priceType: com.tradingview.binancefutures.domain.model.PriceType = com.tradingview.binancefutures.domain.model.PriceType.LAST): Flow<Candle>
    suspend fun getServerTime(): Long
}

/**
 * Public Binance USD-M Futures market data only.
 * No API key is required for public exchange metadata, klines, or market WebSocket streams.
 */
class TradingRepositoryImpl(
    private val binance: BinanceRemoteDataSource = BinanceRemoteDataSource()
) : TradingRepository {
    override suspend fun getFuturesTradingPairs(): List<TradingPair> =
        binance.getFuturesTickers()

    override suspend fun getHistoricalCandles(
        symbol: String,
        timeframe: Timeframe,
        priceType: com.tradingview.binancefutures.domain.model.PriceType
    ): List<Candle> = binance.getHistoricalKlines(symbol, timeframe, priceType)

    override fun getLiveCandleStream(symbol: String, timeframe: Timeframe, priceType: com.tradingview.binancefutures.domain.model.PriceType): Flow<Candle> =
        binance.observeLiveKline(symbol, timeframe, priceType)

    override suspend fun getServerTime(): Long = binance.getServerTime()
}
