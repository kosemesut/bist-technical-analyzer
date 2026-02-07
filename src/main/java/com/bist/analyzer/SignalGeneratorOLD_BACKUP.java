package com.bist.analyzer;

import java.util.List;

public class SignalGeneratorOLD_BACKUP {
    
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
                details.append("<strong>RSI (Göreceli Güç Endeksi):</strong> Aşırı Satış (").append(String.format("%.1f", rsi[lastIdx])).append(")<br>");
            } else if (rsi[lastIdx] > 70) {
                sellSignals += 2;
                details.append("<strong>RSI (Göreceli Güç Endeksi):</strong> Aşırı Alım (").append(String.format("%.1f", rsi[lastIdx])).append(")<br>");
            } else if (rsi[lastIdx] < 50 && lastIdx > 0 && rsi[lastIdx - 1] < rsi[lastIdx]) {
                buySignals += 1;
                details.append("<strong>RSI (Göreceli Güç Endeksi):</strong> Boğa Trendinde (").append(String.format("%.1f", rsi[lastIdx])).append(")<br>");
            } else if (rsi[lastIdx] > 50 && lastIdx > 0 && rsi[lastIdx - 1] > rsi[lastIdx]) {
                sellSignals += 1;
                details.append("<strong>RSI (Göreceli Güç Endeksi):</strong> Ayı Trendinde (").append(String.format("%.1f", rsi[lastIdx])).append(")<br>");
            }
        }

        // MACD signals
        if (lastIdx > 0 && !Double.isNaN(macd.histogram[lastIdx]) && !Double.isNaN(macd.histogram[lastIdx - 1])) {
            if (macd.histogram[lastIdx - 1] < 0 && macd.histogram[lastIdx] > 0) {
                buySignals += 2;
                details.append("<strong>MACD (Hareketli Ortalama Yakınsama Iraksama):</strong> Altın Kesişim<br>");
            } else if (macd.histogram[lastIdx - 1] > 0 && macd.histogram[lastIdx] < 0) {
                sellSignals += 2;
                details.append("<strong>MACD (Hareketli Ortalama Yakınsama Iraksama):</strong> Ölüm Kesişimi<br>");
            } else if (macd.histogram[lastIdx] > 0) {
                buySignals += 1;
                details.append("<strong>MACD (Hareketli Ortalama Yakınsama Iraksama):</strong> Boğa Sinyali<br>");
            } else if (macd.histogram[lastIdx] < 0) {
                sellSignals += 1;
                details.append("<strong>MACD (Hareketli Ortalama Yakınsama Iraksama):</strong> Ayı Sinyali<br>");
            }
        }

        // Moving Average signals
        if (!Double.isNaN(sma20[lastIdx]) && !Double.isNaN(sma50[lastIdx])) {
            if (sma20[lastIdx] > sma50[lastIdx]) {
                buySignals += 1;
                details.append("<strong>MA (Hareketli Ortalama):</strong> SMA20 > SMA50 - Yukarı Trend<br>");
            } else {
                sellSignals += 1;
                details.append("<strong>MA (Hareketli Ortalama):</strong> SMA20 < SMA50 - Aşağı Trend<br>");
            }
        }

        if (!Double.isNaN(ema12[lastIdx]) && latest.getClose() > ema12[lastIdx]) {
            buySignals += 1;
            details.append("<strong>EMA12 (Üstel Hareketli Ortalama):</strong> Fiyat Üstünde<br>");
        } else if (!Double.isNaN(ema12[lastIdx])) {
            sellSignals += 1;
            details.append("<strong>EMA12 (Üstel Hareketli Ortalama):</strong> Fiyat Altında<br>");
        }

        // Bollinger Bands signals
        if (!Double.isNaN(bb.upper[lastIdx]) && !Double.isNaN(bb.lower[lastIdx])) {
            if (latest.getClose() < bb.lower[lastIdx]) {
                buySignals += 2;
                details.append("<strong>Bollinger Bantları:</strong> Alt Bant Altında - Aşırı Satış<br>");
            } else if (latest.getClose() > bb.upper[lastIdx]) {
                sellSignals += 2;
                details.append("<strong>Bollinger Bantları:</strong> Üst Bant Üstünde - Aşırı Alım<br>");
            } else if (latest.getClose() < bb.middle[lastIdx]) {
                buySignals += 1;
                details.append("<strong>Bollinger Bantları:</strong> Alt Yarısında<br>");
            } else {
                sellSignals += 1;
                details.append("<strong>Bollinger Bantları:</strong> Üst Yarısında<br>");
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
                    details.append("<strong>Hacim:</strong> Yüksek Alış Hacmi<br>");
                } else {
                    sellSignals += 1;
                    details.append("<strong>Hacim:</strong> Yüksek Satış Hacmi<br>");
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
