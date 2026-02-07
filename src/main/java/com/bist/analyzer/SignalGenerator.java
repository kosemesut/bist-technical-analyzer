package com.bist.analyzer;

import java.util.List;

public class SignalGenerator {
    
    /**
     * Signal result with score-based classification
     */
    public static class SignalResult {
        public String symbol;
        public long timestamp;
        public double price;
        public String signal; // STRONG_BUY, BUY, HOLD, SELL, STRONG_SELL
        public double confidence; // 0-100
        public String details;
        public int score; // Internal score for debugging

        public SignalResult(String symbol, long timestamp, double price, String signal, 
                          double confidence, String details, int score) {
            this.symbol = symbol;
            this.timestamp = timestamp;
            this.price = price;
            this.signal = signal;
            this.confidence = confidence;
            this.details = details;
            this.score = score;
        }
    }

    /**
     * Generate trading signals using score-based system
     * Based on: EMA trend, RSI, MACD, Volume, OBV, Breakout/Breakdown
     */
    public static SignalResult generateSignal(String symbol, List<StockData> data,
                                             double[] sma20, double[] sma50, double[] ema12,
                                             double[] rsi, TechnicalIndicators.MACDResult macd,
                                             TechnicalIndicators.BollingerBands bb) {
        
        if (data.isEmpty() || data.size() < 200) {
            return new SignalResult(symbol, 0, 0, "HOLD", 0, "Yetersiz veri", 0);
        }

        StockData latest = data.get(data.size() - 1);
        int lastIdx = data.size() - 1;
        
        // Calculate additional indicators
        double[] ema20 = TechnicalIndicators.calculateEMA(data, 20);
        double[] ema50 = TechnicalIndicators.calculateEMA(data, 50);
        double[] ema200 = TechnicalIndicators.calculateEMA(data, 200);
        double[] atr = TechnicalIndicators.calculateATR(data, 14);
        double[] obv = TechnicalIndicators.calculateOBV(data);
        
        // 1. PRE-FILTERS (Data Quality)
        double avgMoneyVolume = 0;
        for (int i = Math.max(0, lastIdx - 19); i <= lastIdx; i++) {
            avgMoneyVolume += data.get(i).getVolume() * data.get(i).getClose();
        }
        avgMoneyVolume /= Math.min(20, data.size());
        
        // Low liquidity filter
        if (avgMoneyVolume < 100000) { // 100K TRY günlük hacim minimum
            return new SignalResult(symbol, latest.getTimestamp(), latest.getClose(),
                "HOLD", 25, "<strong>Düşük Likidite:</strong> Yetersiz işlem hacmi<br>", 0);
        }
        
        // Volatility penalty
        double volatilityRatio = Double.isNaN(atr[lastIdx]) ? 0 : atr[lastIdx] / latest.getClose();
        boolean highVolatility = volatilityRatio > 0.08;
        
        // 2. TREND DETECTION
        int trendScore = 0;
        StringBuilder details = new StringBuilder();
        
        boolean bullishTrend = !Double.isNaN(ema20[lastIdx]) && !Double.isNaN(ema50[lastIdx]) && 
                              !Double.isNaN(ema200[lastIdx]) &&
                              ema20[lastIdx] > ema50[lastIdx] && ema50[lastIdx] > ema200[lastIdx] &&
                              latest.getClose() > ema20[lastIdx];
                              
        boolean bearishTrend = !Double.isNaN(ema20[lastIdx]) && !Double.isNaN(ema50[lastIdx]) && 
                              !Double.isNaN(ema200[lastIdx]) &&
                              ema20[lastIdx] < ema50[lastIdx] && ema50[lastIdx] < ema200[lastIdx] &&
                              latest.getClose() < ema20[lastIdx];
        
        // EMA50 slope confirmation
        double ema50Slope = TechnicalIndicators.calculateSlope(ema50, lastIdx, 10);
        
        if (bullishTrend) {
            trendScore += 2;
            details.append("<strong>Trend (EMA Uyumu):</strong> Güçlü Yükseliş Trendi (EMA20 > EMA50 > EMA200)<br>");
            if (ema50Slope > 0) {
                trendScore += 1;
                details.append("<strong>EMA50 Eğimi:</strong> Pozitif (Yükseliş devam ediyor)<br>");
            }
        } else if (bearishTrend) {
            trendScore -= 2;
            details.append("<strong>Trend (EMA Uyumu):</strong> Güçlü Düşüş Trendi (EMA20 < EMA50 < EMA200)<br>");
            if (ema50Slope < 0) {
                trendScore -= 1;
                details.append("<strong>EMA50 Eğimi:</strong> Negatif (Düşüş devam ediyor)<br>");
            }
        }
        
        // Price above/below EMA20
        if (!Double.isNaN(ema20[lastIdx])) {
            if (latest.getClose() > ema20[lastIdx]) {
                trendScore += 1;
                details.append("<strong>Fiyat Pozisyonu:</strong> EMA20 Üstünde<br>");
            } else {
                trendScore -= 1;
                details.append("<strong>Fiyat Pozisyonu:</strong> EMA20 Altında<br>");
            }
        }
        
        // 3. MOMENTUM CONFIRMATION
        int momentumScore = 0;
        
        if (!Double.isNaN(rsi[lastIdx])) {
            if (rsi[lastIdx] > 55) {
                momentumScore += 1;
                details.append("<strong>RSI (Göreceli Güç):</strong> Boğa Momentumu (")
                      .append(String.format("%.1f", rsi[lastIdx])).append(")<br>");
            } else if (rsi[lastIdx] < 45) {
                momentumScore -= 1;
                details.append("<strong>RSI (Göreceli Güç):</strong> A yı Momentumu (")
                      .append(String.format("%.1f", rsi[lastIdx])).append(")<br>");
            } else if (rsi[lastIdx] < 30) {
                momentumScore += 1; // Oversold bounce potential
                details.append("<strong>RSI:</strong> Aşırı Satış Bölgesi - Geri Dönüş Potansiyeli (")
                      .append(String.format("%.1f", rsi[lastIdx])).append(")<br>");
            } else if (rsi[lastIdx] > 70) {
                momentumScore -= 1; // Overbought
                details.append("<strong>RSI:</strong> Aşırı Alım Bölgesi (")
                      .append(String.format("%.1f", rsi[lastIdx])).append(")<br>");
            }
        }
        
        // MACD histogram
        if (lastIdx > 0 && !Double.isNaN(macd.histogram[lastIdx]) && !Double.isNaN(macd.histogram[lastIdx - 1])) {
            if (macd.histogram[lastIdx] > 0 && macd.histogram[lastIdx] > macd.histogram[lastIdx - 1]) {
                momentumScore += 1;
                details.append("<strong>MACD:</strong> Yükseliş Histogramı (Momentum Artıyor)<br>");
            } else if (macd.histogram[lastIdx] < 0 && macd.histogram[lastIdx] < macd.histogram[lastIdx - 1]) {
                momentumScore -= 1;
                details.append("<strong>MACD:</strong> Düşüş Histogramı (Momentum Azalıyor)<br>");
            }
        }
        
        // 4. VOLUME CONFIRMATION
        int volumeScore = 0;
        
        double avgVolume = TechnicalIndicators.getAverageVolume(data, lastIdx, 20);
        boolean volumeSpike = latest.getVolume() > avgVolume * 1.5;
        
        // Breakout detection
        double highest20 = TechnicalIndicators.getHighestHigh(data, lastIdx - 1, 20);
        double lowest20 = TechnicalIndicators.getLowestLow(data, lastIdx - 1, 20);
        
        boolean breakout = !Double.isNaN(highest20) && latest.getClose() > highest20;
        boolean breakdown = !Double.isNaN(lowest20) && latest.getClose() < lowest20;
        
        if (breakout && volumeSpike) {
            volumeScore += 2;
            details.append("<strong>Kırılım (Breakout):</strong> 20 Günlük Direnç Kırıldı + Yüksek Hacim<br>");
        } else if (breakdown && volumeSpike) {
            volumeScore -= 2;
            details.append("<strong>Çöküş (Breakdown):</strong> 20 Günlük Destek Kırıldı + Yüksek Hacim<br>");
        }
        
        if (volumeSpike) {
            details.append("<strong>Hacim:</strong> Ortalama Üzeri (")
                  .append(String.format("%.0f%%", (latest.getVolume() / avgVolume - 1) * 100))
                  .append(" artış)<br>");
            if (latest.getClose() > data.get(lastIdx - 1).getClose()) {
                volumeScore += 1;
            } else {
                volumeScore -= 1;
            }
        }
        
        // OBV confirmation
        double obv20High = TechnicalIndicators.getHighestValue(obv, lastIdx, 20);
        double obv20Low = TechnicalIndicators.getLowestValue(obv, lastIdx, 20);
        
        if (obv[lastIdx] >= obv20High) {
            volumeScore += 1;
            details.append("<strong>OBV (Hacim Dengesi):</strong> 20 Günlük Zirve - Biriktirim<br>");
        } else if (obv[lastIdx] <= obv20Low) {
            volumeScore -= 1;
            details.append("<strong>OBV (Hacim Dengesi):</strong> 20 Günlük Dip - Dağıtım<br>");
        }
        
        // 5. TOTAL SCORE
        int totalScore = trendScore + momentumScore + volumeScore;
        
        // Apply volatility penalty
        if (highVolatility && totalScore > 0) {
            totalScore = (int)(totalScore * 0.8);
            details.append("<strong>⚠️ Yüksek Volatilite:</strong> Skor düşürüldü (ATR/Fiyat: ")
                  .append(String.format("%.2f%%", volatilityRatio * 100)).append(")<br>");
        }
        
        // 6. CLASSIFICATION
        String signal;
        double confidence;
        
        if (totalScore >= 6) {
            signal = "STRONG_BUY";
            confidence = Math.min(95, 70 + (totalScore - 6) * 5);
        } else if (totalScore >= 4) {
            signal = "BUY";
            confidence = 55 + (totalScore - 4) * 7.5;
        } else if (totalScore >= -3 && totalScore <= 3) {
            signal = "HOLD";
            confidence = 50;
        } else if (totalScore >= -5) {
            signal = "SELL";
            confidence = 55 + Math.abs(totalScore + 4) * 7.5;
        } else {
            signal = "STRONG_SELL";
            confidence = Math.min(95, 70 + Math.abs(totalScore + 6) * 5);
        }
        
        // Add score to details
        details.append("<br><strong>📊 Toplam Skor:</strong> ").append(totalScore)
              .append(" (Trend: ").append(trendScore)
              .append(", Momentum: ").append(momentumScore)
              .append(", Hacim: ").append(volumeScore).append(")");
        
        return new SignalResult(symbol, latest.getTimestamp(), latest.getClose(), 
                              signal, confidence, details.toString(), totalScore);
    }
}
