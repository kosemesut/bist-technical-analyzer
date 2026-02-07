package com.bist.analyzer;

import java.util.ArrayList;
import java.util.List;

/**
 * BacktestValidator - Sinyalleri geriye dönük test eder
 * AL sinyalinden sonra gerçekten %5+ yükseliş oldu mu?
 * SAT sinyalinden sonra gerçekten %5+ düşüş oldu mu?
 * 
 * Eğer geçmişte benzer sinyaller başarısız olduysa, şimdiki sinyali REDDET
 */
public class BacktestValidator {

    private static final double SUCCESS_THRESHOLD = 0.05; // %5 hareket
    private static final int[] TEST_PERIODS = {1, 3, 5, 10}; // 1, 3, 5, 10 gün sonra kontrol

    /**
     * Sinyal başarı oranını test et
     */
    public static class BacktestResult {
        public int totalSignals;           // Geçmişte kaç benzer sinyal vardı
        public int successfulSignals;      // Kaç tanesi başarılı oldu
        public double successRate;         // Başarı oranı (0-1)
        public String reason;              // Detaylı açıklama
        public List<String> examples;      // Örnek başarılı/başarısız durumlar
        
        public BacktestResult() {
            this.examples = new ArrayList<>();
        }
    }

    /**
     * Sinyali geriye dönük test et - geçmişte benzer durumlarda başarılı olmuş mu?
     */
    public static BacktestResult validateSignalWithBacktest(
            String signal,                 // "BUY" or "SELL"
            List<StockData> data,         // Tüm historik data
            int currentIdx,               // Şu anki index
            double[] sma20,
            double[] sma50,
            double[] rsi,
            TechnicalIndicators.ADXResult adx) {
        
        BacktestResult result = new BacktestResult();
        
        // En az 60 günlük geçmiş olmalı (test için)
        if (currentIdx < 60) {
            result.reason = "Yetersiz geçmiş veri";
            return result;
        }
        
        // Şu anki piyasa durumu
        StockData current = data.get(currentIdx);
        double currentPrice = current.getClose();
        double currentRSI = rsi[currentIdx];
        double currentADX = adx.adx[currentIdx];
        
        // Son 100 gün içinde benzer durumları bul
        int lookback = Math.min(100, currentIdx - 20);
        
        for (int i = currentIdx - lookback; i < currentIdx - 10; i++) {
            if (i < 20) continue; // İndikatörler için yeterli veri yok
            
            // Benzer piyasa durumu mu?
            boolean similarMarket = isSimilarMarketCondition(
                data, i, currentIdx,
                sma20, sma50, rsi, adx,
                signal
            );
            
            if (similarMarket) {
                result.totalSignals++;
                
                // Bu noktadan sonraki 1-10 gün içinde hedef gerçekleşti mi?
                boolean successful = checkIfTargetReached(data, i, signal);
                
                if (successful) {
                    result.successfulSignals++;
                    
                    // Örnek ekle
                    if (result.examples.size() < 3) {
                        double maxGain = calculateMaxMove(data, i, signal);
                        result.examples.add(String.format(
                            "✅ %s @ %.2f₺ → %.1f%% hareket (Gün %d)",
                            signal, data.get(i).getClose(),
                            maxGain * 100, i
                        ));
                    }
                } else {
                    // Başarısız örnek ekle
                    if (result.examples.size() < 3) {
                        double maxGain = calculateMaxMove(data, i, signal);
                        result.examples.add(String.format(
                            "❌ %s @ %.2f₺ → sadece %.1f%% (Gün %d)",
                            signal, data.get(i).getClose(),
                            maxGain * 100, i
                        ));
                    }
                }
            }
        }
        
        // Başarı oranını hesapla
        if (result.totalSignals > 0) {
            result.successRate = (double) result.successfulSignals / result.totalSignals;
            result.reason = String.format(
                "Son 100 günde %d benzer %s sinyali, %d başarılı (%.0f%%)",
                result.totalSignals, signal,
                result.successfulSignals, result.successRate * 100
            );
        } else {
            result.successRate = 0.5; // Veri yoksa nötr
            result.reason = "Benzer geçmiş sinyal bulunamadı";
        }
        
        return result;
    }

    /**
     * Benzer piyasa durumu mu? (RSI, ADX, MA alignment benzer mi)
     * GEVŞEK KRİTERLER - Daha fazla benzer sinyal bulabilmek için
     */
    private static boolean isSimilarMarketCondition(
            List<StockData> data,
            int pastIdx,
            int currentIdx,
            double[] sma20,
            double[] sma50,
            double[] rsi,
            TechnicalIndicators.ADXResult adx,
            String signal) {
        
        // RSI benzerliği (±15 puan içinde) - GEVŞEK
        double rsiDiff = Math.abs(rsi[pastIdx] - rsi[currentIdx]);
        if (rsiDiff > 15) return false;
        
        // ADX benzerliği (±15 puan içinde) - GEVŞEK
        double adxDiff = Math.abs(adx.adx[pastIdx] - adx.adx[currentIdx]);
        if (adxDiff > 15) return false;
        
        // Trend yönü aynı mı?
        boolean pastUptrend = sma20[pastIdx] > sma50[pastIdx];
        boolean currentUptrend = sma20[currentIdx] > sma50[currentIdx];
        
        if (pastUptrend != currentUptrend) return false;
        
        // Fiyat pozisyonu benzer mi? (SMA20'ye göre) - GEVŞEK %5
        double pastPricePos = (data.get(pastIdx).getClose() - sma20[pastIdx]) / sma20[pastIdx];
        double currentPricePos = (data.get(currentIdx).getClose() - sma20[currentIdx]) / sma20[currentIdx];
        
        if (Math.abs(pastPricePos - currentPricePos) > 0.05) return false; // %5 fark
        
        return true;
    }

    /**
     * Hedef fiyat hareketine ulaşıldı mı? (1-10 gün içinde %5+)
     */
    private static boolean checkIfTargetReached(
            List<StockData> data,
            int signalIdx,
            String signal) {
        
        double entryPrice = data.get(signalIdx).getClose();
        
        // 1, 3, 5, 10 gün sonrasını kontrol et
        for (int period : TEST_PERIODS) {
            int checkIdx = signalIdx + period;
            
            // Veri yoksa atla
            if (checkIdx >= data.size()) continue;
            
            // Bu dönem içindeki max/min fiyatı bul
            double maxPrice = entryPrice;
            double minPrice = entryPrice;
            
            for (int i = signalIdx + 1; i <= checkIdx && i < data.size(); i++) {
                maxPrice = Math.max(maxPrice, data.get(i).getHigh());
                minPrice = Math.min(minPrice, data.get(i).getLow());
            }
            
            // AL sinyali: %5+ yükseliş oldu mu?
            if ("BUY".equals(signal)) {
                double gain = (maxPrice - entryPrice) / entryPrice;
                if (gain >= SUCCESS_THRESHOLD) {
                    return true; // Başarılı
                }
            }
            
            // SAT sinyali: %5+ düşüş oldu mu?
            if ("SELL".equals(signal)) {
                double loss = (entryPrice - minPrice) / entryPrice;
                if (loss >= SUCCESS_THRESHOLD) {
                    return true; // Başarılı
                }
            }
        }
        
        return false; // Hiçbir dönemde hedef tutmadı
    }

    /**
     * Maksimum hareket ne kadardı? (raporlama için)
     */
    private static double calculateMaxMove(
            List<StockData> data,
            int signalIdx,
            String signal) {
        
        double entryPrice = data.get(signalIdx).getClose();
        double maxMove = 0;
        
        // 10 gün sonrasına kadar kontrol et
        for (int i = signalIdx + 1; i < Math.min(signalIdx + 11, data.size()); i++) {
            if ("BUY".equals(signal)) {
                double gain = (data.get(i).getHigh() - entryPrice) / entryPrice;
                maxMove = Math.max(maxMove, gain);
            } else {
                double loss = (entryPrice - data.get(i).getLow()) / entryPrice;
                maxMove = Math.max(maxMove, loss);
            }
        }
        
        return maxMove;
    }

    /**
     * Backtest sonucuna göre güven çarpanı hesapla
     */
    public static double calculateConfidenceMultiplier(BacktestResult backtest) {
        if (backtest.totalSignals < 3) {
            return 0.8; // Çok az veri, biraz düşür
        }
        
        // Başarı oranına göre çarpan
        if (backtest.successRate >= 0.7) {
            return 1.0; // %70+ başarı, güvenilir
        } else if (backtest.successRate >= 0.5) {
            return 0.7; // %50-70 başarı, dikkatli
        } else if (backtest.successRate >= 0.3) {
            return 0.4; // %30-50 başarı, çok düşük confidence
        } else {
            return 0.0; // %30'un altı, tamamen reddet
        }
    }

    /**
     * Geriye dönük test raporu oluştur
     */
    public static String generateBacktestReport(BacktestResult backtest) {
        StringBuilder report = new StringBuilder();
        
        report.append(String.format(
            "<strong>📊 Backtest Sonucu:</strong> %s<br>",
            backtest.reason
        ));
        
        if (backtest.totalSignals >= 3) {
            report.append(String.format(
                "<strong>Başarı Oranı:</strong> %.0f%% (%d/%d)<br>",
                backtest.successRate * 100,
                backtest.successfulSignals,
                backtest.totalSignals
            ));
            
            // Örnekler varsa göster
            if (!backtest.examples.isEmpty()) {
                report.append("<strong>Örnekler:</strong><br>");
                for (String example : backtest.examples) {
                    report.append("  • ").append(example).append("<br>");
                }
            }
        }
        
        return report.toString();
    }
}
