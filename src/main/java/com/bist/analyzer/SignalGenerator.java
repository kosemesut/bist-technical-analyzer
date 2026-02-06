package com.bist.analyzer;

import java.util.List;

public class SignalGenerator {
    
    /**
     * Signal result containing buy/sell signals
     */
    public static class SignalResult {
        public String symbol;
        public long timestamp;
        public double price;
        public String signal; // BUY, SELL, STRONG_BUY, STRONG_SELL, HOLD
        public double confidence; // 0-100
        public String details;

        public SignalResult(String symbol, long timestamp, double price, String signal, double confidence, String details) {
            this.symbol = symbol;
            this.timestamp = timestamp;
            this.price = price;
            this.signal = signal;
            this.confidence = confidence;
            this.details = details;
        }
    }

    /**
     * Generate trading signals based on technical indicators
     */
    public static SignalResult generateSignal(String symbol, List<StockData> data,
                                             double[] sma20, double[] sma50, double[] ema12,
                                             double[] rsi, TechnicalIndicators.MACDResult macd,
                                             TechnicalIndicators.BollingerBands bb) {
        
        if (data.isEmpty()) {
            return new SignalResult(symbol, 0, 0, "HOLD", 0, "No data available");
        }

        StockData latest = data.get(data.size() - 1);
        int lastIdx = data.size() - 1;
        
        int buySignals = 0;
        int sellSignals = 0;
        StringBuilder details = new StringBuilder();

        // RSI signals
        if (!Double.isNaN(rsi[lastIdx])) {
            if (rsi[lastIdx] < 30) {
                buySignals += 2;
                details.append("RSI Oversold(").append(String.format("%.1f", rsi[lastIdx])).append(") ");
            } else if (rsi[lastIdx] > 70) {
                sellSignals += 2;
                details.append("RSI Overbought(").append(String.format("%.1f", rsi[lastIdx])).append(") ");
            } else if (rsi[lastIdx] < 50 && lastIdx > 0 && rsi[lastIdx - 1] < rsi[lastIdx]) {
                buySignals += 1;
                details.append("RSI Bullish ");
            } else if (rsi[lastIdx] > 50 && lastIdx > 0 && rsi[lastIdx - 1] > rsi[lastIdx]) {
                sellSignals += 1;
                details.append("RSI Bearish ");
            }
        }

        // MACD signals
        if (lastIdx > 0 && !Double.isNaN(macd.histogram[lastIdx]) && !Double.isNaN(macd.histogram[lastIdx - 1])) {
            if (macd.histogram[lastIdx - 1] < 0 && macd.histogram[lastIdx] > 0) {
                buySignals += 2;
                details.append("MACD Crossover ");
            } else if (macd.histogram[lastIdx - 1] > 0 && macd.histogram[lastIdx] < 0) {
                sellSignals += 2;
                details.append("MACD Crossunder ");
            } else if (macd.histogram[lastIdx] > 0) {
                buySignals += 1;
                details.append("MACD Bullish ");
            } else if (macd.histogram[lastIdx] < 0) {
                sellSignals += 1;
                details.append("MACD Bearish ");
            }
        }

        // Moving Average signals
        if (!Double.isNaN(sma20[lastIdx]) && !Double.isNaN(sma50[lastIdx])) {
            if (sma20[lastIdx] > sma50[lastIdx]) {
                buySignals += 1;
                details.append("MA Uptrend ");
            } else {
                sellSignals += 1;
                details.append("MA Downtrend ");
            }
        }

        if (!Double.isNaN(ema12[lastIdx]) && latest.getClose() > ema12[lastIdx]) {
            buySignals += 1;
            details.append("Price>EMA12 ");
        } else if (!Double.isNaN(ema12[lastIdx])) {
            sellSignals += 1;
            details.append("Price<EMA12 ");
        }

        // Bollinger Bands signals
        if (!Double.isNaN(bb.upper[lastIdx]) && !Double.isNaN(bb.lower[lastIdx])) {
            if (latest.getClose() < bb.lower[lastIdx]) {
                buySignals += 2;
                details.append("BB Lower ");
            } else if (latest.getClose() > bb.upper[lastIdx]) {
                sellSignals += 2;
                details.append("BB Upper ");
            } else if (latest.getClose() < bb.middle[lastIdx]) {
                buySignals += 1;
                details.append("BB Lower Half ");
            } else {
                sellSignals += 1;
                details.append("BB Upper Half ");
            }
        }

        // Volume signal
        if (lastIdx > 0) {
            double avgVolume = 0;
            int volumeCount = Math.min(20, lastIdx);
            for (int i = lastIdx - volumeCount; i < lastIdx; i++) {
                avgVolume += data.get(i).getVolume();
            }
            avgVolume /= volumeCount;
            
            if (latest.getVolume() > avgVolume * 1.5) {
                if (buySignals > sellSignals) {
                    buySignals += 1;
                    details.append("High Volume ");
                } else {
                    sellSignals += 1;
                    details.append("High Volume ");
                }
            }
        }

        // Determine final signal
        String signal = "HOLD";
        double confidence = 0;
        
        if (buySignals > sellSignals * 1.5) {
            signal = "STRONG_BUY";
            confidence = Math.min(100, (buySignals / (double)(buySignals + sellSignals)) * 100);
        } else if (buySignals > sellSignals) {
            signal = "BUY";
            confidence = Math.min(100, (buySignals / (double)(buySignals + sellSignals)) * 100);
        } else if (sellSignals > buySignals * 1.5) {
            signal = "STRONG_SELL";
            confidence = Math.min(100, (sellSignals / (double)(buySignals + sellSignals)) * 100);
        } else if (sellSignals > buySignals) {
            signal = "SELL";
            confidence = Math.min(100, (sellSignals / (double)(buySignals + sellSignals)) * 100);
        } else {
            confidence = 50;
        }

        return new SignalResult(symbol, latest.getTimestamp(), latest.getClose(), signal, confidence, details.toString());
    }
}
