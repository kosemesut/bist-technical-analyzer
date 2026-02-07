package com.bist.analyzer;

import java.util.ArrayList;
import java.util.List;

/**
 * SignalValidator - Yanlış sinyalleri (fake breakout, stop hunt, tuzak) 
 * filtreleyip güvenilir sinyaller üreyen validator sınıfı
 * 
 * Hedef: SOKM grafiğinde gösterilen hatalı AL/SAT sinyallerini elimine etmek
 */
public class SignalValidator {

    /**
     * Signal kalitesi puanı (0-100)
     * 60+ = Güvenilir
     * 40-60 = Şüpheli (confidence reduce)
     * <40 = Ignore
     */
    public static class SignalQuality {
        public int falseScore;           // 0-100, yüksek = yanlış sinyal olasılığı
        public double confidenceMultiplier; // 0.0-1.0, orijinal confidence multiplier
        public String reason;            // Neden rejected/reduced
        public List<String> redFlags;    // Kırmızı bayraklar
        
        public SignalQuality() {
            this.falseScore = 0;
            this.confidenceMultiplier = 1.0;
            this.reason = "";
            this.redFlags = new ArrayList<>();
        }
    }

    /**
     * Sinyal kalitesini kapsamlı şekilde kontrol et
     */
    public static SignalQuality validateSignalQuality(
            String signal,                           // "BUY", "SELL"
            List<StockData> data,                   // 200+ bar geçmişi
            double adx,                              // ADX değeri
            double currentPrice,                    // Güncel fiyat
            double sma20, double sma50, double ema200, // MA'lar
            List<TechnicalIndicators.SupportResistanceLevel> srLevels,
            TechnicalIndicators.ADXResult adxResult) {
        
        SignalQuality quality = new SignalQuality();
        
        if (data.size() < 5) {
            quality.falseScore = 100;
            quality.reason = "Yetersiz veri";
            return quality;
        }
        
        // Last 5 candle'ı al (1 current + 4 history)
        int lastIdx = data.size() - 1;
        StockData current = data.get(lastIdx);
        
        // ============================================
        // 1. WEAK TREND PENALTY (ADX < 20)
        // ============================================
        if (adx < 20) {
            quality.falseScore += 35;
            quality.redFlags.add("⚠️ ADX=" + String.format("%.1f", adx) + " < 20 (Zayıf Trend)");
        }
        
        // ============================================
        // 2. VOLUME UNSUPPORTED BREAKOUT
        // ============================================
        double avgVolume = calculateAverageVolume(data, 20);
        double volumeRatio = (double) current.getVolume() / avgVolume;
        
        if ("BUY".equals(signal) && currentPrice > sma20) {
            // AL sinyalinde breakout bekliyoruz
            if (volumeRatio < 0.8) {
                quality.falseScore += 28;
                quality.redFlags.add(
                    String.format("📊 Hacim Destek Yok (%.1fx avg)", volumeRatio)
                );
            }
        } else if ("SELL".equals(signal) && currentPrice < sma20) {
            // SAT sinyalinde breakdown bekliyoruz
            if (volumeRatio < 0.8) {
                quality.falseScore += 28;
                quality.redFlags.add(
                    String.format("📊 Hacim Destek Yok (%.1fx avg)", volumeRatio)
                );
            }
        }
        
        // ============================================
        // 3. SUPPORT/RESISTANCE PROXIMITY TRAP
        // ============================================
        if (!srLevels.isEmpty()) {
            double minDistancePercent = 100; // % cinsinden
            boolean nearResistance = false;
            boolean nearSupport = false;
            
            for (TechnicalIndicators.SupportResistanceLevel level : srLevels) {
                double distancePercent = Math.abs(currentPrice - level.level) / level.level * 100;
                
                if (distancePercent < 3) { // 3% içinde
                    if ("BUY".equals(signal) && level.isResistance && currentPrice < level.level) {
                        nearResistance = true;
                        minDistancePercent = Math.min(minDistancePercent, distancePercent);
                    }
                    if ("SELL".equals(signal) && level.isSupport && currentPrice > level.level) {
                        nearSupport = true;
                        minDistancePercent = Math.min(minDistancePercent, distancePercent);
                    }
                }
            }
            
            if (nearResistance || nearSupport) {
                quality.falseScore += 25;
                quality.redFlags.add(
                    String.format("🎯 S/R Tuzak %.2f%% içinde", minDistancePercent)
                );
            }
        }
        
        // ============================================
        // 4. PRICE TOO FAR FROM SMA50 (Mean Reversion Risk)
        // ============================================
        double distFromSMA50 = Math.abs(currentPrice - sma50) / sma50 * 100;
        if (distFromSMA50 > 5.0) {
            quality.falseScore += 15;
            quality.redFlags.add(
                String.format("📏 Fiyat SMA50'den %.2f%% uzak (Mean Rev)", distFromSMA50)
            );
        }
        
        // ============================================
        // 5. BOLLINGER BAND WICK REJECTION (Stop Hunting)
        // ============================================
        double bodySize = Math.abs(current.getClose() - current.getOpen());
        double upperWick = current.getHigh() - Math.max(current.getClose(), current.getOpen());
        double lowerWick = Math.min(current.getClose(), current.getOpen()) - current.getLow();
        
        double maxWick = Math.max(upperWick, lowerWick);
        
        if (bodySize > 0 && maxWick > bodySize * 2) {
            quality.falseScore += 18;
            quality.redFlags.add(
                String.format("🔄 Stop Hunt Pattern (Wick/Body=%.2f)", maxWick / bodySize)
            );
        }
        
        // ============================================
        // 6. FAKE BREAKOUT DETECTION (3-Bar Test)
        // ============================================
        if (data.size() >= 3) {
            StockData bar3 = data.get(lastIdx - 2);
            StockData bar2 = data.get(lastIdx - 1);
            StockData bar1 = data.get(lastIdx);
            
            // Pattern: Breakout then immediate reversal
            if ("BUY".equals(signal)) {
                // AL verilmişte, sonraki barlar geri dönüyor mu?
                if (bar1.getClose() > sma50 && bar2.getClose() < bar1.getClose()) {
                    // 2. bar current barrdan düşük
                    if (bar3.getClose() < bar2.getClose()) {
                        // 3 bar düşüş = Fake breakout
                        quality.falseScore += 30;
                        quality.redFlags.add("📉 Fake Breakout Detected (3-bar reversal)");
                    }
                }
            } else if ("SELL".equals(signal)) {
                // SAT verilmişte, sonraki barlar yükselişe mi?
                if (bar1.getClose() < sma50 && bar2.getClose() > bar1.getClose()) {
                    if (bar3.getClose() > bar2.getClose()) {
                        quality.falseScore += 30;
                        quality.redFlags.add("📈 Fake Breakdown Detected (3-bar up)");
                    }
                }
            }
        }
        
        // ============================================
        // 7. NO MOMENTUM SUPPORT (Weak MACD/RSI)
        // ============================================
        double rsi = TechnicalIndicators.calculateRSI(data, 14)[lastIdx];
        
        if ("BUY".equals(signal) && rsi < 40) {
            // AL ama RSI hala zayıf
            quality.falseScore += 20;
            quality.redFlags.add(String.format("📉 Geri Çeken Momentum: RSI=%.1f (BUY için düşük)", rsi));
        } else if ("SELL".equals(signal) && rsi > 60) {
            // SAT ama RSI hala kuvvetli
            quality.falseScore += 20;
            quality.redFlags.add(String.format("📈 Ters Momentum: RSI=%.1f (SELL için yüksek)", rsi));
        }
        
        // ============================================
        // 8. MA ALIGNMENT CHECK (Trend doğrulama)
        // ============================================
        if ("BUY".equals(signal)) {
            // Trendde AL için: SMA20 > SMA50 > EMA200
            if (!(sma20 > sma50 && sma50 > ema200)) {
                quality.falseScore += 22;
                quality.redFlags.add(
                    String.format("⚡ Trend Uyumsuzluk: SMA20(%.2f) > SMA50(%.2f) > EMA200(%.2f)", 
                    sma20, sma50, ema200)
                );
            }
        } else if ("SELL".equals(signal)) {
            // Trendde SAT için: SMA20 < SMA50 < EMA200
            if (!(sma20 < sma50 && sma50 < ema200)) {
                quality.falseScore += 22;
                quality.redFlags.add(
                    String.format("⚡ Trend Uyumsuzluk: SMA20(%.2f) < SMA50(%.2f) < EMA200(%.2f)", 
                    sma20, sma50, ema200)
                );
            }
        }
        
        // ============================================
        // 9. MARKET MAKER TRAP (Hacim spike sonra boşal)
        // ============================================
        double prevVolume = data.get(lastIdx - 1).getVolume();
        double prevPrevVolume = data.get(lastIdx - 2).getVolume();
        
        if (current.getVolume() > avgVolume * 1.5 && 
            prevVolume < avgVolume * 0.8) {
            // Spike volume ama sonra boşalmış = tuzak
            quality.falseScore += 15;
            quality.redFlags.add("🎣 Market Maker Trap: Volume spike sonra düşüş");
        }
        
        // ============================================
        // 10. PRICE AGAINST TREND GAP
        // ============================================
        double openGap = Math.abs(current.getOpen() - data.get(lastIdx-1).getClose()) / data.get(lastIdx-1).getClose() * 100;
        
        if ("BUY".equals(signal) && current.getOpen() > current.getClose() && openGap > 1.5) {
            quality.falseScore += 15;
            quality.redFlags.add(String.format("⬆️ Al-Satı Fark: Gap=%.2f%% ama kapanış aşağı", openGap));
        } else if ("SELL".equals(signal) && current.getOpen() < current.getClose() && openGap > 1.5) {
            quality.falseScore += 15;
            quality.redFlags.add(String.format("⬇️ Sat-Alı Fark: Gap=%.2f%% ama kapanış yukarı", openGap));
        }
        
        // ============================================
        // FINAL CONFIDENCE ADJUSTMENT
        // ============================================
        quality.falseScore = Math.min(100, quality.falseScore);
        
        // ÇOK GEVŞEK THRESHOLD - Backtest asıl kararı verecek
        if (quality.falseScore >= 90) {
            quality.confidenceMultiplier = 0.0; // Ignore - reject (çok açık hata)
            quality.reason = "REJECT - Kesin Hatalı Sinyal (" + quality.falseScore + "/100)";
        } else if (quality.falseScore >= 75) {
            quality.confidenceMultiplier = 0.7; // Azalt
            quality.reason = "CAUTION - Confidence Reduce (" + quality.falseScore + "/100)";
        } else if (quality.falseScore >= 60) {
            quality.confidenceMultiplier = 0.9; // Hafif azalt
            quality.reason = "MINOR CAUTION - Slight Reduce (" + quality.falseScore + "/100)";
        } else {
            quality.confidenceMultiplier = 1.0; // Accept
            quality.reason = "ACCEPT - Green Light (" + quality.falseScore + "/100)";
        }
        
        return quality;
    }

    /**
     * Yardımcı: Ortalama hacim hesapla
     */
    private static double calculateAverageVolume(List<StockData> data, int period) {
        if (data.size() < period) {
            return data.get(data.size() - 1).getVolume();
        }
        
        long total = 0;
        int start = data.size() - period;
        for (int i = start; i < data.size(); i++) {
            total += data.get(i).getVolume();
        }
        return (double) total / period;
    }

    /**
     * Hızlı Ccheck: False breakout var mı?
     */
    public static boolean isFalseBreakout(
            List<StockData> data,
            double supportLevel,
            double resistanceLevel,
            int lookbackBars) {
        
        if (data.size() < lookbackBars) return false;
        
        int lastIdx = data.size() - 1;
        StockData current = data.get(lastIdx);
        
        // UP breakout: Close > resistance, low < resistance
        boolean breakoutAboveRes = current.getClose() > resistanceLevel && 
                                   current.getLow() < resistanceLevel;
        
        // DOWN breakdown: Close < support, high > support
        boolean breakdownBelowSup = current.getClose() < supportLevel && 
                                    current.getHigh() > supportLevel;
        
        if (breakoutAboveRes || breakdownBelowSup) {
            // 3 bar içinde kapalı mı geri dönmüş?
            int checkBars = Math.min(lookbackBars, 3);
            for (int i = 1; i < checkBars && (lastIdx - i) >= 0; i++) {
                StockData bar = data.get(lastIdx - i);
                if (breakoutAboveRes && bar.getClose() < resistanceLevel) {
                    return true; // Geri dönmüş
                }
                if (breakdownBelowSup && bar.getClose() > supportLevel) {
                    return true; // Geri dönmüş
                }
            }
        }
        
        return false;
    }

    /**
     * Stop hunting pattern: Destek altında wick, kapalı destek üstünde
     */
    public static boolean isStopHuntPattern(
            StockData current,
            double supportLevel,
            double atr) {
        
        double expectedWickDepth = atr * 0.5;
        
        // Kapalı destek üstünde
        if (current.getClose() > supportLevel) {
            // Wick destek altında
            if (current.getLow() < supportLevel - expectedWickDepth) {
                return true; // Stop hunting pattern
            }
        }
        
        return false;
    }
}
