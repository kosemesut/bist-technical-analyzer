package com.bist.analyzer;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SignalGenerator {
    
    /**
     * Historical trade signal point
     */
    public static class TradePoint {
        public int index;           // Position in data array
        public String type;         // "BUY" or "SELL"
        public double price;        // Price at signal
        public String reason;       // Why signal generated
        
        public TradePoint(int index, String type, double price, String reason) {
            this.index = index;
            this.type = type;
            this.price = price;
            this.reason = reason;
        }
    }
    
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
     * Generate trading signals using ENHANCED multi-layer confirmation system
     * Based on: EMA trend, RSI, MACD, Bollinger, Volume, OBV, Price Action, Confluence
     * OPTIMIZED FOR INVESTMENT DECISIONS - Multiple confirmations required
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
        
        // NEW: Advanced indicators for false signal prevention
        TechnicalIndicators.ADXResult adx = TechnicalIndicators.calculateADX(data, 14);
        double[] volumePressure = TechnicalIndicators.calculateVolumePressure(data);
        TechnicalIndicators.CandlePattern candlePattern = TechnicalIndicators.analyzeCandlePattern(data, lastIdx);
        List<TechnicalIndicators.SupportResistanceLevel> srLevels = TechnicalIndicators.findSupportResistance(data, 100);
        
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
        
        // NEW: ADX Trend Strength Filter (Prevent signals in weak/ranging markets)
        double adxValue = !Double.isNaN(adx.adx[lastIdx]) ? adx.adx[lastIdx] : 0;
        boolean weakTrend = adxValue < 20;  // ADX < 20 means weak trend or ranging
        boolean strongTrend = adxValue > 25; // ADX > 25 means strong trend
        
        if (weakTrend) {
            return new SignalResult(symbol, latest.getTimestamp(), latest.getClose(),
                "HOLD", 25, "<strong>⚠️ Zayıf Trend (ADX: " + String.format("%.1f", adxValue) + 
                "):</strong> Yatay piyasa, sinyal güvenilirliği düşük<br>", 0);
        }
        
        // NEW: Check proximity to support/resistance levels
        boolean nearSupportResistance = false;
        String srDetails = "";
        for (TechnicalIndicators.SupportResistanceLevel level : srLevels) {
            double distancePercent = Math.abs(latest.getClose() - level.level) / latest.getClose();
            if (distancePercent <0.03 && level.strength > 0.6) { // Within 3% and strong level
                nearSupportResistance = true;
                srDetails = String.format("Yakında %s seviye: %.2f ₺ (%.1f%% uzaklık)<br>",
                    level.isSupport ? "destek" : "direnç", level.level, distancePercent * 100);
                break;
            }
        }
        
        // NEW: Volume Pressure Analysis (Buying vs Selling)
        double avgPressure = 0;
        for (int i = Math.max(0, lastIdx - 4); i <= lastIdx; i++) {
            avgPressure += volumePressure[i];
        }
        avgPressure /= Math.min(5, lastIdx + 1);
        boolean buyingPressure = avgPressure > 0;
        boolean sellingPressure = avgPressure < 0;
        double pressureStrength = Math.abs(avgPressure) / latest.getVolume();
        
        // 2. TREND DETECTION (Enhanced with multiple timeframes)
        int trendScore = 0;
        int confirmationCount = 0; // Track how many indicators agree
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
        
        // 3. MOMENTUM CONFIRMATION (Enhanced with RSI divergence)
        int momentumScore = 0;
        
        // RSI Analysis with stricter thresholds
        if (!Double.isNaN(rsi[lastIdx]) && lastIdx >= 5) {
            // Check RSI trend (5-period)
            double rsiTrend = rsi[lastIdx] - rsi[lastIdx - 5];
            
            if (rsi[lastIdx] > 60) {
                momentumScore += 2;
                confirmationCount++;
                details.append("<strong>✓ RSI (Göreceli Güç):</strong> Güçlü Boğa Momentumu (")
                      .append(String.format("%.1f", rsi[lastIdx])).append(")<br>");
            } else if (rsi[lastIdx] > 50 && rsiTrend > 5) {
                momentumScore += 1;
                details.append("<strong>RSI:</strong> Yükselen Momentum (")
                      .append(String.format("%.1f", rsi[lastIdx])).append(", +")
                      .append(String.format("%.1f", rsiTrend)).append(")<br>");
            } else if (rsi[lastIdx] < 40) {
                momentumScore -= 2;
                confirmationCount++;
                details.append("<strong>✓ RSI:</strong> Güçlü Ayı Momentumu (")
                      .append(String.format("%.1f", rsi[lastIdx])).append(")<br>");
            } else if (rsi[lastIdx] < 50 && rsiTrend < -5) {
                momentumScore -= 1;
                details.append("<strong>RSI:</strong> Düşen Momentum (")
                      .append(String.format("%.1f", rsi[lastIdx])).append(", ")
                      .append(String.format("%.1f", rsiTrend)).append(")<br>");
            }
            
            // Oversold/Overbought with reversal confirmation
            if (rsi[lastIdx] < 30 && rsiTrend > 0) {
                momentumScore += 2;
                confirmationCount++;
                details.append("<strong>✓ RSI Geri Dönüş:</strong> Aşırı Satıştan Çıkış (")
                      .append(String.format("%.1f", rsi[lastIdx])).append(")<br>");
            } else if (rsi[lastIdx] > 70 && rsiTrend < 0) {
                momentumScore -= 2;
                confirmationCount++;
                details.append("<strong>✓ RSI Geri Dönüş:</strong> Aşırı Alımdan Düşüş (")
                      .append(String.format("%.1f", rsi[lastIdx])).append(")<br>");
            }
        }
        
        // MACD with crossover detection (ENHANCED)
        if (lastIdx > 1 && !Double.isNaN(macd.macdLine[lastIdx]) && !Double.isNaN(macd.signalLine[lastIdx])) {
            double prevMacdLine = macd.macdLine[lastIdx - 1];
            double prevSignalLine = macd.signalLine[lastIdx - 1];
            
            // Golden Cross (MACD crosses above Signal)
            if (prevMacdLine <= prevSignalLine && macd.macdLine[lastIdx] > macd.signalLine[lastIdx]) {
                momentumScore += 3;
                confirmationCount++;
                details.append("<strong>✓ MACD GOLDEN CROSS:</strong> Güçlü Alış Sinyali<br>");
            }
            // Death Cross (MACD crosses below Signal)
            else if (prevMacdLine >= prevSignalLine && macd.macdLine[lastIdx] < macd.signalLine[lastIdx]) {
                momentumScore -= 3;
                confirmationCount++;
                details.append("<strong>✓ MACD DEATH CROSS:</strong> Güçlü Satış Sinyali<br>");
            }
            // Histogram direction
            else if (!Double.isNaN(macd.histogram[lastIdx]) && !Double.isNaN(macd.histogram[lastIdx - 1])) {
                if (macd.histogram[lastIdx] > 0 && macd.histogram[lastIdx] > macd.histogram[lastIdx - 1]) {
                    momentumScore += 1;
                    details.append("<strong>MACD:</strong> Yükseliş Histogramı (Momentum Artıyor)<br>");
                } else if (macd.histogram[lastIdx] < 0 && macd.histogram[lastIdx] < macd.histogram[lastIdx - 1]) {
                    momentumScore -= 1;
                    details.append("<strong>MACD:</strong> Düşüş Histogramı (Momentum Azalıyor)<br>");
                }
            }
        }
        
        // 4. BOLLINGER BANDS ANALYSIS (NEW - HIGH PRECISION)
        int bollingerScore = 0;
        if (!Double.isNaN(bb.upper[lastIdx]) && !Double.isNaN(bb.lower[lastIdx]) && !Double.isNaN(bb.middle[lastIdx])) {
            double bbPosition = (latest.getClose() - bb.lower[lastIdx]) / (bb.upper[lastIdx] - bb.lower[lastIdx]);
            double bbWidth = (bb.upper[lastIdx] - bb.lower[lastIdx]) / bb.middle[lastIdx];
            
            // Bollinger Breakout (price closes above upper band)
            if (latest.getClose() > bb.upper[lastIdx] && data.get(lastIdx - 1).getClose() <= bb.upper[lastIdx - 1]) {
                bollingerScore += 3;
                confirmationCount++;
                details.append("<strong>✓ BOLLINGER BREAKOUT:</strong> Fiyat Üst Bant Üzerinde<br>");
            }
            // Bollinger Breakdown (price closes below lower band)
            else if (latest.getClose() < bb.lower[lastIdx] && data.get(lastIdx - 1).getClose() >= bb.lower[lastIdx - 1]) {
                bollingerScore -= 3;
                confirmationCount++;
                details.append("<strong>✓ BOLLINGER BREAKDOWN:</strong> Fiyat Alt Bant Altında<br>");
            }
            // Bollinger Bounce from lower (oversold)
            else if (bbPosition < 0.2 && latest.getClose() > data.get(lastIdx - 1).getClose()) {
                bollingerScore += 2;
                details.append("<strong>Bollinger:</strong> Alt Banttan Geri Sekmek (Aşırı Satış)<br>");
            }
            // Bollinger rejection from upper (overbought)
            else if (bbPosition > 0.8 && latest.getClose() < data.get(lastIdx - 1).getClose()) {
                bollingerScore -= 2;
                details.append("<strong>Bollinger:</strong> Üst Banttan Red (Aşırı Alım)<br>");
            }
            
            // Bollinger Squeeze (low volatility - prepare for breakout)
            if (bbWidth < 0.05) {
                details.append("<strong>⚠️ Bollinger Squeeze:</strong> Düşük Volatilite - Kırılım Bekleniyor<br>");
            }
        }
        
        // 5. VOLUME CONFIRMATION (Enhanced with stricter thresholds)
        int volumeScore = 0;
        
        double avgVolume = TechnicalIndicators.getAverageVolume(data, lastIdx, 20);
        boolean volumeSpike = latest.getVolume() > avgVolume * 2.0; // INCREASED from 1.5x to 2.0x
        boolean strongVolumeSpike = latest.getVolume() > avgVolume * 3.0;
        
        // Breakout detection
        double highest20 = TechnicalIndicators.getHighestHigh(data, lastIdx - 1, 20);
        double lowest20 = TechnicalIndicators.getLowestLow(data, lastIdx - 1, 20);
        
        boolean breakout = !Double.isNaN(highest20) && latest.getClose() > highest20;
        boolean breakdown = !Double.isNaN(lowest20) && latest.getClose() < lowest20;
        
        if (breakout && strongVolumeSpike) {
            volumeScore += 4;
            confirmationCount++;
            details.append("<strong>✓ GÜÇLÜ KIRILIM:</strong> 20 Günlük Direnç + 3x Hacim<br>");
        } else if (breakout && volumeSpike) {
            volumeScore += 2;
            confirmationCount++;
            details.append("<strong>✓ Kırılım (Breakout):</strong> 20 Günlük Direnç Kırıldı + Yüksek Hacim<br>");
        } else if (breakdown && strongVolumeSpike) {
            volumeScore -= 4;
            confirmationCount++;
            details.append("<strong>✓ GÜÇLÜ ÇÖKÜŞ:</strong> 20 Günlük Destek + 3x Hacim<br>");
        } else if (breakdown && volumeSpike) {
            volumeScore -= 2;
            confirmationCount++;
            details.append("<strong>✓ Çöküş (Breakdown):</strong> 20 Günlük Destek Kırıldı + Yüksek Hacim<br>");
        }
        
        if (strongVolumeSpike) {
            details.append("<strong>✓ AŞIRI YÜ KSEK HACİM:</strong> ")
                  .append(String.format("%.0f%%", (latest.getVolume() / avgVolume - 1) * 100))
                  .append(" artış (3x üzeri)<br>");
            if (latest.getClose() > data.get(lastIdx - 1).getClose()) {
                volumeScore += 2;
                confirmationCount++;
            } else {
                volumeScore -= 2;
                confirmationCount++;
            }
        } else if (volumeSpike) {
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
        
        // OBV with trend confirmation
        if (lastIdx >= 10 && obv[lastIdx] >= obv20High) {
            double obvTrend = obv[lastIdx] - obv[lastIdx - 10];
            if (obvTrend > 0) {
                volumeScore += 2;
                confirmationCount++;
                details.append("<strong>✓ OBV:</strong> 20 Günlük Zirve + Yükseliş Trendi - Güçlü Biriktirim<br>");
            } else {
                volumeScore += 1;
                details.append("<strong>OBV (Hacim Dengesi):</strong> 20 Günlük Zirve - Biriktirim<br>");
            }
        } else if (lastIdx >= 10 && obv[lastIdx] <= obv20Low) {
            double obvTrend = obv[lastIdx] - obv[lastIdx - 10];
            if (obvTrend < 0) {
                volumeScore -= 2;
                confirmationCount++;
                details.append("<strong>✓ OBV:</strong> 20 Günlük Dip + Düşüş Trendi - Güçlü Dağıtım<br>");
            } else {
                volumeScore -= 1;
                details.append("<strong>OBV (Hacim Dengesi):</strong> 20 Günlük Dip - Dağıtım<br>");
            }
        }
        
        // 6. CANDLE PATTERN ANALYSIS (NEW - REVERSAL PATTERNS)
        int candleScore = 0;
        if (candlePattern.isBullishEngulfing) {
            candleScore += 4;
            confirmationCount++;
            details.append("<strong>✓ BULLISH ENGULFING:</strong> Güçlü Yükseliş Dönüş Pattern'i<br>");
        } else if (candlePattern.isHammer && rsi[lastIdx] < 50) {
            candleScore += 3;
            confirmationCount++;
            details.append("<strong>✓ HAMMER:</strong> Destek Bulma + Alıcı Baskısı<br>");
        } else if (candlePattern.isBullishHarami) {
            candleScore += 2;
            details.append("<strong>Bullish Harami:</strong> Olası Yükseliş Dönüşü<br>");
        }
        
        if (candlePattern.isBearishEngulfing) {
            candleScore -= 4;
            confirmationCount++;
            details.append("<strong>✓ BEARISH ENGULFING:</strong> Güçlü Düşüş Dönüş Pattern'i<br>");
        } else if (candlePattern.isShootingStar && rsi[lastIdx] > 50) {
            candleScore -= 3;
            confirmationCount++;
            details.append("<strong>✓ SHOOTING STAR:</strong> Direnç Görme + Satıcı Baskısı<br>");
        } else if (candlePattern.isBearishHarami) {
            candleScore -= 2;
            details.append("<strong>Bearish Harami:</strong> Olası Düşüş Dönüşü<br>");
        }
        
        // Doji at support/resistance is indecision
        if (candlePattern.isDoji && nearSupportResistance) {
            details.append("<strong>⚠️ DOJI @ S/R Seviye:</strong> Belirsizlik - Dikkatli Ol<br>");
        }
        
        // 7. PRICE ACTION PATTERNS (NEW - CRITICAL)
        int priceActionScore = 0;
        if (lastIdx >= 20) {
            // Higher Highs and Higher Lows (uptrend)
            double prevHigh = Double.MIN_VALUE;
            double prevLow = Double.MAX_VALUE;
            for (int i = lastIdx - 20; i < lastIdx - 10; i++) {
                prevHigh = Math.max(prevHigh, data.get(i).getHigh());
                prevLow = Math.min(prevLow, data.get(i).getLow());
            }
            double recentHigh = Double.MIN_VALUE;
            double recentLow = Double.MAX_VALUE;
            for (int i = lastIdx - 10; i <= lastIdx; i++) {
                recentHigh = Math.max(recentHigh, data.get(i).getHigh());
                recentLow = Math.min(recentLow, data.get(i).getLow());
            }
            
            if (recentHigh > prevHigh && recentLow > prevLow) {
                priceActionScore += 3;
                confirmationCount++;
                details.append("<strong>✓ PRICE ACTION:</strong> Higher Highs & Higher Lows (Güçlü Yükseliş)<br>");
            } else if (recentHigh < prevHigh && recentLow < prevLow) {
                priceActionScore -= 3;
                confirmationCount++;
                details.append("<strong>✓ PRICE ACTION:</strong> Lower Highs & Lower Lows (Güçlü Düşüş)<br>");
            }
        }
        
        // 8. VOLUME PRESSURE CONFIRMATION (NEW)
        int pressureScore = 0;
        if (strongTrend) { // Only trust pressure in trending markets
            if (buyingPressure && pressureStrength > 0.3) {
                pressureScore += 2;
                confirmationCount++;
                details.append("<strong>✓ ALIM BASKISI:</strong> Güçlü alıcı aktivitesi tespit edildi<br>");
            } else if (sellingPressure && pressureStrength > 0.3) {
                pressureScore -= 2;
                confirmationCount++;
                details.append("<strong>✓ SATIM BASKISI:</strong> Güçlü satıcı aktivitesi tespit edildi<br>");
            }
        }
        
        // 9. TOTAL SCORE WITH ALL NEW INDICATORS
        int totalScore = trendScore + momentumScore + bollingerScore + volumeScore + priceActionScore + candleScore + pressureScore;
        
        // Apply volatility penalty
        if (highVolatility && totalScore > 0) {
            totalScore = (int)(totalScore * 0.8);
            details.append("<strong>⚠️ Yüksek Volatilite:</strong> Skor düşürüldü (ATR/Fiyat: ")
                  .append(String.format("%.2f%%", volatilityRatio * 100)).append(")<br>");
        }        
        // NEW: ADX bonus for strong trends
        if (strongTrend) {
            int adxBonus = totalScore > 0 ? 2 : (totalScore < 0 ? -2 : 0);
            totalScore += adxBonus;
            details.append("<strong>✓ GÜÇLÜ TREND (ADX: " + String.format("%.1f", adxValue) + 
                          "):</strong> Trend gücü sinyali destekliyor<br>");
        }        
        // 10. FALSE BREAKOUT WARNING (NEW - CRITICAL)
        if (nearSupportResistance && !srDetails.isEmpty()) {
            details.append("<strong>⚠️ DESTEK/DİRENÇ YAKINI:</strong> " + srDetails);
            
            // Reduce confidence if near resistance on BUY signal or near support on SELL signal
            if (totalScore > 0 && srDetails.contains("direnç")) {
                totalScore = (int)(totalScore * 0.7); // 30% penalty
                details.append("<strong>⚠️ DİKKAT:</strong> Direnç yakınında AL sinyali - Skor düşürüldü<br>");
            } else if (totalScore < 0 && srDetails.contains("destek")) {
                totalScore = (int)(totalScore * 0.7); // 30% penalty
                details.append("<strong>⚠️ DİKKAT:</strong> Destek yakınında SAT sinyali - Skor düşürüldü<br>");
            }
        }
        
        // ============================================
        // 11. FALSE SIGNAL VALIDATION LAYER (DEVRE DIŞI - ÇOK KATI)
        // ============================================
        // Determine preliminary signal direction for validator
        String preliminarySignal = (totalScore > 0) ? "BUY" : ((totalScore < 0) ? "SELL" : "HOLD");
        
        SignalValidator.SignalQuality signalQuality = SignalValidator.validateSignalQuality(
            preliminarySignal, data, adxValue, latest.getClose(),
            sma20[lastIdx], sma50[lastIdx], ema200[lastIdx], srLevels, adx
        );
        
        // BYPASS - SignalValidator çok katı, önerilerini göster ama sinyal engelleme
        // totalScore = (int)(totalScore * signalQuality.confidenceMultiplier);
        
        // Add red flags to details
        if (!signalQuality.redFlags.isEmpty()) {
            details.append("<br><strong>🚨 UYARI FLAGLARı:</strong><br>");
            for (String flag : signalQuality.redFlags) {
                details.append("  • ").append(flag).append("<br>");
            }
        }
        
        if (!signalQuality.reason.isEmpty()) {
            details.append("<strong>Validator Karar:</strong> ").append(signalQuality.reason).append("<br>");
        }
        
        // ============================================
        // 12. BACKTEST VALIDATION LAYER (DEVRE DIŞI - SADECE RAPOR)
        // ============================================
        // Backtest sadece bilgi amaçlı - sinyal engellemesin
        BacktestValidator.BacktestResult backtestResult = BacktestValidator.validateSignalWithBacktest(
            preliminarySignal, data, lastIdx, sma20, sma50, rsi, adx
        );
        
        // BYPASS - Backtest multiplier uygulanmasın, algoritma kendi öğrensin
        // double backtestMultiplier = BacktestValidator.calculateConfidenceMultiplier(backtestResult);
        // totalScore = (int)(totalScore * backtestMultiplier);
        
        // Backtest asla sinyal engellemesin - sadece raporda göster
        
        // Backtest raporu ekle
        if (backtestResult.totalSignals > 0) {
            details.append("<br>");
            details.append(BacktestValidator.generateBacktestReport(backtestResult));
            
            if (backtestResult.successRate < 0.5) {
                details.append("<strong>⚠️ DİKKAT:</strong> Geçmiş başarı oranı düşük, güven azaltıldı<br>");
            } else if (backtestResult.successRate >= 0.7) {
                details.append("<strong>✅ İYİ:</strong> Geçmişte benzer sinyaller başarılı olmuş<br>");
            }
        }
        
        // 13. CONFLUENCE CHECK (GEVŞEK - Daha fazla aksiyon)
        boolean hasStrongConfluence = confirmationCount >= 3; // DÜŞÜRÜLDÜ: 5→3
        boolean hasMinimumConfluence = confirmationCount >= 2; // DÜŞÜRÜLDÜ: 3→2
        
        // 14. CLASSIFICATION (GEVŞEK THRESHOLD - Daha fazla sinyal)
        String signal;
        double confidence;
        
        if (totalScore >= 6 && hasStrongConfluence) { // GEVŞEK: 12→6, ADX/doji kontrolü kaldırıldı
            signal = "STRONG_BUY";
            confidence = Math.min(95, 70 + confirmationCount * 4);
            details.append("<br><strong>🎯 CONFLUENCE:</strong> ").append(confirmationCount)
                  .append(" gösterge aynı yönde - GÜÇLÜ SİNYAL<br>");
        } else if (totalScore >= 4 && hasMinimumConfluence) { // GEVŞEK: 7→4
            signal = "BUY";
            confidence = 55 + confirmationCount * 6;
            details.append("<br><strong>✓ CONFLUENCE:</strong> ").append(confirmationCount)
                  .append(" gösterge aynı yönde<br>");
        } else if (totalScore <= -6 && hasStrongConfluence) { // GEVŞEK: -12→-6
            signal = "STRONG_SELL";
            confidence = Math.min(95, 70 + confirmationCount * 4);
            details.append("<br><strong>🎯 CONFLUENCE:</strong> ").append(confirmationCount)
                  .append(" gösterge aynı yönde - GÜÇLÜ SİNYAL<br>");
        } else if (totalScore <= -4 && hasMinimumConfluence) { // GEVŞEK: -7→-4
            signal = "SELL";
            confidence = 55 + confirmationCount * 6;
            details.append("<br><strong>✓ CONFLUENCE:</strong> ").append(confirmationCount)
                  .append(" gösterge aynı yönde<br>");
        } else {
            signal = "HOLD";
            confidence = 50;
            if (totalScore != 0) {
                details.append("<br><strong>⚠️ YETERSİZ CONFLUENCE:</strong> Sinyaller karışık, bekleme önerilir<br>");
            }
        }
        
        // Add detailed score breakdown
        details.append("<br><strong>📊 Toplam Skor:</strong> ").append(totalScore)
              .append(" (Trend: ").append(trendScore)
              .append(", Momentum: ").append(momentumScore)
              .append(", Bollinger: ").append(bollingerScore)
              .append(", Hacim: ").append(volumeScore)
              .append(", Price Action: ").append(priceActionScore)
              .append(", Candle: ").append(candleScore)
              .append(", Pressure: ").append(pressureScore)
              .append(")")
              .append("<br><strong>🔍 ADX (Trend Gücü):</strong> ").append(String.format("%.1f", adxValue))
              .append(strongTrend ? " - Güçlü" : (weakTrend ? " - Zayıf" : " - Orta"));
        
        return new SignalResult(symbol, latest.getTimestamp(), latest.getClose(), 
                              signal, confidence, details.toString(), totalScore);
    }
    
    /**
     * Find historical BUY/SELL signal points for chart visualization
     * ENHANCED VERSION - Multiple confirmations required for HIGH PRECISION
     * Uses: EMA/SMA crossover + RSI + Volume + Price Action + Confluence
     */
    /**
     * Find historical BUY/SELL signals using THE SAME scoring system as analyzeStock()
     * This ensures consistency between chart markers and report recommendations
     */
    public static List<TradePoint> findHistoricalSignals(List<StockData> data,
                                                         double[] sma20, double[] sma50, 
                                                         double[] ema12, double[] rsi) {
        List<TradePoint> signals = new ArrayList<>();
        
        if (data.size() < 60) {
            return signals;
        }
        
        // Calculate all needed indicators (same as analyzeStock)
        double[] ema200 = TechnicalIndicators.calculateEMA(data, 200);
        TechnicalIndicators.ADXResult adxResult = TechnicalIndicators.calculateADX(data, 14);
        double[] adx = adxResult.adx;
        TechnicalIndicators.MACDResult macd = TechnicalIndicators.calculateMACD(data, 12, 26, 9);
        TechnicalIndicators.BollingerBands bb = TechnicalIndicators.calculateBollingerBands(data, 20, 2.0);
        double[] obv = TechnicalIndicators.calculateOBV(data);
        
        int lastSignalIndex = -10; // Track last signal to avoid too frequent signals
        
        // Scan each historical day (same as report would)
        for (int i = 60; i < data.size() - 1; i++) {
            // Skip if too close to last signal
            if (i - lastSignalIndex < 5) {
                continue;
            }
            
            // Skip if indicators are NaN
            if (Double.isNaN(sma20[i]) || Double.isNaN(sma50[i]) || 
                Double.isNaN(ema12[i]) || Double.isNaN(ema200[i]) || 
                Double.isNaN(rsi[i]) || Double.isNaN(adx[i])) {
                continue;
            }
            
            // === CALCULATE SCORE (EXACT SAME AS analyzeStock) ===
            int trendScore = 0;
            int momentumScore = 0;
            int volumeScore = 0;
            int priceActionScore = 0;
            int confirmationCount = 0;
            
            StockData current = data.get(i);
            double currentPrice = current.getClose();
            
            // 1. TREND SCORE (EMA/SMA crossovers)
            if (ema12[i] > sma20[i] && sma20[i] > sma50[i]) {
                trendScore += 3;
                confirmationCount++;
            } else if (ema12[i] < sma20[i] && sma20[i] < sma50[i]) {
                trendScore -= 3;
                confirmationCount++;
            }
            
            // Golden/Death Cross
            if (i > 0) {
                if (ema12[i-1] <= sma20[i-1] && ema12[i] > sma20[i]) {
                    trendScore += 3;
                    confirmationCount++;
                } else if (ema12[i-1] >= sma20[i-1] && ema12[i] < sma20[i]) {
                    trendScore -= 3;
                    confirmationCount++;
                }
            }
            
            // 2. MOMENTUM SCORE (RSI + MACD)
            if (i >= 5) {
                double rsiTrend = rsi[i] - rsi[i-5];
                if (rsi[i] > 55 && rsiTrend > 0) {
                    momentumScore += 2;
                    confirmationCount++;
                } else if (rsi[i] < 45 && rsiTrend < 0) {
                    momentumScore -= 2;
                    confirmationCount++;
                }
                
                // RSI oversold/overbought reversal
                if (rsi[i] < 30 && rsiTrend > 0) {
                    momentumScore += 2;
                    confirmationCount++;
                } else if (rsi[i] > 70 && rsiTrend < 0) {
                    momentumScore -= 2;
                    confirmationCount++;
                }
            }
            
            // MACD crossovers
            if (i > 1 && !Double.isNaN(macd.macdLine[i]) && !Double.isNaN(macd.signalLine[i])) {
                double prevMacd = macd.macdLine[i-1];
                double prevSignal = macd.signalLine[i-1];
                
                if (prevMacd <= prevSignal && macd.macdLine[i] > macd.signalLine[i]) {
                    momentumScore += 3;
                    confirmationCount++;
                } else if (prevMacd >= prevSignal && macd.macdLine[i] < macd.signalLine[i]) {
                    momentumScore -= 3;
                    confirmationCount++;
                }
            }
            
            // 3. VOLUME SCORE (breakout/breakdown + OBV)
            double avgVolume = TechnicalIndicators.getAverageVolume(data, i, 20);
            boolean volumeSpike = current.getVolume() > avgVolume * 2.0;
            boolean strongVolumeSpike = current.getVolume() > avgVolume * 3.0;
            
            double highest20 = TechnicalIndicators.getHighestHigh(data, i - 1, 20);
            double lowest20 = TechnicalIndicators.getLowestLow(data, i - 1, 20);
            
            boolean breakout = !Double.isNaN(highest20) && currentPrice > highest20;
            boolean breakdown = !Double.isNaN(lowest20) && currentPrice < lowest20;
            
            if (breakout && strongVolumeSpike) {
                volumeScore += 4;
                confirmationCount++;
            } else if (breakout && volumeSpike) {
                volumeScore += 2;
                confirmationCount++;
            } else if (breakdown && strongVolumeSpike) {
                volumeScore -= 4;
                confirmationCount++;
            } else if (breakdown && volumeSpike) {
                volumeScore -= 2;
                confirmationCount++;
            }
            
            // OBV confirmation
            double obv20High = TechnicalIndicators.getHighestValue(obv, i, 20);
            double obv20Low = TechnicalIndicators.getLowestValue(obv, i, 20);
            
            if (i >= 10 && obv[i] >= obv20High) {
                double obvTrend = obv[i] - obv[i-10];
                if (obvTrend > 0) {
                    volumeScore += 2;
                    confirmationCount++;
                }
            } else if (i >= 10 && obv[i] <= obv20Low) {
                double obvTrend = obv[i] - obv[i-10];
                if (obvTrend < 0) {
                    volumeScore -= 2;
                    confirmationCount++;
                }
            }
            
            // 4. PRICE ACTION SCORE (Higher Highs/Lower Lows)
            if (i >= 20) {
                double prevHigh = Double.MIN_VALUE;
                double prevLow = Double.MAX_VALUE;
                for (int j = i - 20; j < i - 10; j++) {
                    prevHigh = Math.max(prevHigh, data.get(j).getHigh());
                    prevLow = Math.min(prevLow, data.get(j).getLow());
                }
                double recentHigh = Double.MIN_VALUE;
                double recentLow = Double.MAX_VALUE;
                for (int j = i - 10; j <= i; j++) {
                    recentHigh = Math.max(recentHigh, data.get(j).getHigh());
                    recentLow = Math.min(recentLow, data.get(j).getLow());
                }
                
                if (recentHigh > prevHigh && recentLow > prevLow) {
                    priceActionScore += 3;
                    confirmationCount++;
                } else if (recentHigh < prevHigh && recentLow < prevLow) {
                    priceActionScore -= 3;
                    confirmationCount++;
                }
            }
            
            // TOTAL SCORE (same as analyzeStock)
            int totalScore = trendScore + momentumScore + volumeScore + priceActionScore;
            
            // ADX bonus for strong trends
            if (!Double.isNaN(adx[i]) && adx[i] > 25) {
                int adxBonus = totalScore > 0 ? 2 : (totalScore < 0 ? -2 : 0);
                totalScore += adxBonus;
            }
            
            // CONFLUENCE CHECK (same thresholds as report)
            boolean hasStrongConfluence = confirmationCount >= 3;
            boolean hasMinimumConfluence = confirmationCount >= 2;
            
            // GENERATE SIGNALS (same thresholds as report)
            // Strong BUY: score >= 6, confluence >= 3
            // BUY: score >= 4, confluence >= 2
            // Strong SELL: score <= -6, confluence >= 3
            // SELL: score <= -4, confluence >= 2
            
            String signalType = null;
            String reason = "";
            
            if (totalScore >= 6 && hasStrongConfluence) {
                signalType = "BUY"; // Mark as BUY (strong signals on chart)
                reason = "Güçlü AL (Skor: " + totalScore + ", " + confirmationCount + " gösterge)";
            } else if (totalScore >= 4 && hasMinimumConfluence) {
                signalType = "BUY"; // Mark as BUY (regular signals on chart)
                reason = "AL (Skor: " + totalScore + ", " + confirmationCount + " gösterge)";
            } else if (totalScore <= -6 && hasStrongConfluence) {
                signalType = "SELL"; // Mark as SELL (strong signals on chart)
                reason = "Güçlü SAT (Skor: " + totalScore + ", " + confirmationCount + " gösterge)";
            } else if (totalScore <= -4 && hasMinimumConfluence) {
                signalType = "SELL"; // Mark as SELL (regular signals on chart)
                reason = "SAT (Skor: " + totalScore + ", " + confirmationCount + " gösterge)";
            }
            
            if (signalType != null) {
                signals.add(new TradePoint(i, signalType, currentPrice, reason));
                lastSignalIndex = i;
            }
        }
        
        // Filter signals to keep only the strongest one per calendar day
        // This prevents contradictory BUY/SELL signals on the same day
        List<TradePoint> filteredSignals = new ArrayList<>();
        Map<String, TradePoint> signalsByDate = new java.util.LinkedHashMap<>();
        
        for (TradePoint signal : signals) {
            if (signal.index < 0 || signal.index >= data.size()) continue;
            
            long timestamp = data.get(signal.index).getTimestamp();
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            String dateKey = sdf.format(new java.util.Date(timestamp));
            
            if (!signalsByDate.containsKey(dateKey)) {
                signalsByDate.put(dateKey, signal);
            } else {
                // Keep signal with higher absolute score
                TradePoint existing = signalsByDate.get(dateKey);
                int existingScore = Integer.parseInt(existing.reason.replaceAll("[^-0-9]", "").split("[,]")[0]);
                int newScore = Integer.parseInt(signal.reason.replaceAll("[^-0-9]", "").split("[,]")[0]);
                
                if (Math.abs(newScore) > Math.abs(existingScore)) {
                    signalsByDate.put(dateKey, signal);
                }
            }
        }
        
        // Add filtered signals back
        for (TradePoint signal : signalsByDate.values()) {
            filteredSignals.add(signal);
        }
        
        return filteredSignals;
    }
}
