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
    fun getLiveCandleStream(symbol: String, timeframe: Timeframe): Flow<Candle>
    suspend fun getServerTime(): Long
}

class TradingRepositoryImpl(
    private val remoteDataSource: BinanceRemoteDataSource = BinanceRemoteDataSource()
) : TradingRepository {
    override suspend fun getFuturesTradingPairs(): List<TradingPair> {
        return remoteDataSource.getFuturesTickers()
    }

    override suspend fun getHistoricalCandles(
        symbol: String,
        timeframe: Timeframe,
        priceType: com.tradingview.binancefutures.domain.model.PriceType
    ): List<Candle> {
        return remoteDataSource.getHistoricalKlines(symbol, timeframe, priceType)
    }

    override fun getLiveCandleStream(symbol: String, timeframe: Timeframe): Flow<Candle> {
        return remoteDataSource.observeLiveKline(symbol, timeframe)
    }

    override suspend fun getServerTime(): Long {
        return remoteDataSource.getServerTime()
    }
}
