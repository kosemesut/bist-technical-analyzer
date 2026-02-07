package com.bist.analyzer;

import java.util.List;
import java.util.ArrayList;

public class TechnicalIndicators {

    /**
     * Calculate Simple Moving Average from double array
     */
    public static double[] calculateSMAFromArray(double[] values, int period) {
        double[] sma = new double[values.length];
        
        for (int i = 0; i < values.length; i++) {
            if (i < period - 1 || Double.isNaN(values[i])) {
                sma[i] = Double.NaN;
            } else {
                double sum = 0;
                for (int j = i - period + 1; j <= i; j++) {
                    sum += values[j];
                }
                sma[i] = sum / period;
            }
        }
        return sma;
    }

    /**
     * Calculate Simple Moving Average
     */
    public static double[] calculateSMA(List<StockData> data, int period) {
        double[] sma = new double[data.size()];
        
        for (int i = 0; i < data.size(); i++) {
            if (i < period - 1) {
                sma[i] = Double.NaN;
            } else {
                double sum = 0;
                for (int j = i - period + 1; j <= i; j++) {
                    sum += data.get(j).getClose();
                }
                sma[i] = sum / period;
            }
        }
        return sma;
    }

    /**
     * Calculate Exponential Moving Average
     */
    public static double[] calculateEMA(List<StockData> data, int period) {
        double[] ema = new double[data.size()];
        double multiplier = 2.0 / (period + 1);
        
        // SMA as starting point
        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += data.get(i).getClose();
        }
        ema[period - 1] = sum / period;
        
        // Calculate EMA
        for (int i = period; i < data.size(); i++) {
            ema[i] = (data.get(i).getClose() - ema[i - 1]) * multiplier + ema[i - 1];
        }
        
        // Fill NaN for initial period
        for (int i = 0; i < period - 1; i++) {
            ema[i] = Double.NaN;
        }
        
        return ema;
    }

    /**
     * Calculate Relative Strength Index (RSI)
     */
    public static double[] calculateRSI(List<StockData> data, int period) {
        double[] rsi = new double[data.size()];
        double[] gains = new double[data.size()];
        double[] losses = new double[data.size()];
        
        // Calculate gains and losses
        for (int i = 1; i < data.size(); i++) {
            double change = data.get(i).getClose() - data.get(i - 1).getClose();
            gains[i] = change > 0 ? change : 0;
            losses[i] = change < 0 ? -change : 0;
        }
        
        // Calculate average gain and loss
        double avgGain = 0, avgLoss = 0;
        for (int i = 1; i <= period; i++) {
            avgGain += gains[i];
            avgLoss += losses[i];
        }
        avgGain /= period;
        avgLoss /= period;
        
        // Calculate RSI
        for (int i = period + 1; i < data.size(); i++) {
            avgGain = (avgGain * (period - 1) + gains[i]) / period;
            avgLoss = (avgLoss * (period - 1) + losses[i]) / period;
            
            if (avgLoss == 0) {
                rsi[i] = avgGain == 0 ? 50 : 100;
            } else {
                double rs = avgGain / avgLoss;
                rsi[i] = 100 - (100 / (1 + rs));
            }
        }
        
        // Fill NaN for initial period
        for (int i = 0; i <= period; i++) {
            rsi[i] = Double.NaN;
        }
        
        return rsi;
    }

    /**
     * Calculate MACD (Moving Average Convergence Divergence)
     */
    public static class MACDResult {
        public double[] macdLine;
        public double[] signalLine;
        public double[] histogram;
    }
    
    public static MACDResult calculateMACD(List<StockData> data, int fast, int slow, int signal) {
        MACDResult result = new MACDResult();
        
        double[] ema12 = calculateEMA(data, fast);
        double[] ema26 = calculateEMA(data, slow);
        
        result.macdLine = new double[data.size()];
        for (int i = 0; i < data.size(); i++) {
            if (Double.isNaN(ema12[i]) || Double.isNaN(ema26[i])) {
                result.macdLine[i] = Double.NaN;
            } else {
                result.macdLine[i] = ema12[i] - ema26[i];
            }
        }
        
        result.signalLine = calculateSMAFromArray(result.macdLine, signal);
        
        result.histogram = new double[data.size()];
        for (int i = 0; i < data.size(); i++) {
            if (Double.isNaN(result.macdLine[i]) || Double.isNaN(result.signalLine[i])) {
                result.histogram[i] = Double.NaN;
            } else {
                result.histogram[i] = result.macdLine[i] - result.signalLine[i];
            }
        }
        
        return result;
    }

    /**
     * Calculate Bollinger Bands
     */
    public static class BollingerBands {
        public double[] middle;
        public double[] upper;
        public double[] lower;
    }
    
    public static BollingerBands calculateBollingerBands(List<StockData> data, int period, double stdDevMultiplier) {
        BollingerBands bb = new BollingerBands();
        bb.middle = calculateSMA(data, period);
        bb.upper = new double[data.size()];
        bb.lower = new double[data.size()];
        
        for (int i = period - 1; i < data.size(); i++) {
            double sum = 0;
            for (int j = i - period + 1; j <= i; j++) {
                sum += Math.pow(data.get(j).getClose() - bb.middle[i], 2);
            }
            double stdDev = Math.sqrt(sum / period);
            bb.upper[i] = bb.middle[i] + (stdDev * stdDevMultiplier);
            bb.lower[i] = bb.middle[i] - (stdDev * stdDevMultiplier);
        }
        
        for (int i = 0; i < period - 1; i++) {
            bb.middle[i] = Double.NaN;
            bb.upper[i] = Double.NaN;
            bb.lower[i] = Double.NaN;
        }
        
        return bb;
    }

    /**
     * Calculate ATR (Average True Range) - volatility indicator
     */
    public static double[] calculateATR(List<StockData> data, int period) {
        double[] tr = new double[data.size()];
        double[] atr = new double[data.size()];
        
        // Calculate True Range
        for (int i = 0; i < data.size(); i++) {
            if (i == 0) {
                tr[i] = data.get(i).getHigh() - data.get(i).getLow();
            } else {
                double highLow = data.get(i).getHigh() - data.get(i).getLow();
                double highClose = Math.abs(data.get(i).getHigh() - data.get(i - 1).getClose());
                double lowClose = Math.abs(data.get(i).getLow() - data.get(i - 1).getClose());
                tr[i] = Math.max(highLow, Math.max(highClose, lowClose));
            }
        }
        
        // Calculate ATR (EMA of TR)
        for (int i = 0; i < data.size(); i++) {
            if (i < period - 1) {
                atr[i] = Double.NaN;
            } else if (i == period - 1) {
                double sum = 0;
                for (int j = 0; j <= i; j++) {
                    sum += tr[j];
                }
                atr[i] = sum / period;
            } else {
                atr[i] = (atr[i - 1] * (period - 1) + tr[i]) / period;
            }
        }
        
        return atr;
    }

    /**
     * Calculate OBV (On-Balance Volume) - volume indicator
     */
    public static double[] calculateOBV(List<StockData> data) {
        double[] obv = new double[data.size()];
        
        obv[0] = data.get(0).getVolume();
        
        for (int i = 1; i < data.size(); i++) {
            if (data.get(i).getClose() > data.get(i - 1).getClose()) {
                obv[i] = obv[i - 1] + data.get(i).getVolume();
            } else if (data.get(i).getClose() < data.get(i - 1).getClose()) {
                obv[i] = obv[i - 1] - data.get(i).getVolume();
            } else {
                obv[i] = obv[i - 1];
            }
        }
        
        return obv;
    }

    /**
     * Get highest high over period
     */
    public static double getHighestHigh(List<StockData> data, int endIndex, int period) {
        if (endIndex < period - 1) return Double.NaN;
        
        double highest = data.get(endIndex - period + 1).getHigh();
        for (int i = endIndex - period + 2; i <= endIndex; i++) {
            highest = Math.max(highest, data.get(i).getHigh());
        }
        return highest;
    }

    /**
     * Get lowest low over period
     */
    public static double getLowestLow(List<StockData> data, int endIndex, int period) {
        if (endIndex < period - 1) return Double.NaN;
        
        double lowest = data.get(endIndex - period + 1).getLow();
        for (int i = endIndex - period + 2; i <= endIndex; i++) {
            lowest = Math.min(lowest, data.get(i).getLow());
        }
        return lowest;
    }

    /**
     * Calculate slope of values over period
     */
    public static double calculateSlope(double[] values, int endIndex, int period) {
        if (endIndex < period - 1 || Double.isNaN(values[endIndex])) return Double.NaN;
        
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        int n = 0;
        
        for (int i = endIndex - period + 1; i <= endIndex; i++) {
            if (!Double.isNaN(values[i])) {
                double x = i - (endIndex - period + 1);
                double y = values[i];
                sumX += x;
                sumY += y;
                sumXY += x * y;
                sumX2 += x * x;
                n++;
            }
        }
        
        if (n < 2) return Double.NaN;
        
        return (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
    }

    /**
     * Calculate average volume over period
     */
    public static double getAverageVolume(List<StockData> data, int endIndex, int period) {
        if (endIndex < period - 1) return Double.NaN;
        
        double sum = 0;
        for (int i = endIndex - period + 1; i <= endIndex; i++) {
            sum += data.get(i).getVolume();
        }
        return sum / period;
    }

    /**
     * Get highest value in array over period
     */
    public static double getHighestValue(double[] values, int endIndex, int period) {
        if (endIndex < period - 1) return Double.NaN;
        
        double highest = values[endIndex - period + 1];
        for (int i = endIndex - period + 2; i <= endIndex; i++) {
            if (!Double.isNaN(values[i])) {
                highest = Math.max(highest, values[i]);
            }
        }
        return highest;
    }

    /**
     * Get lowest value in array over period
     */
    public static double getLowestValue(double[] values, int endIndex, int period) {
        if (endIndex < period - 1) return Double.NaN;
        
        double lowest = values[endIndex - period + 1];
        for (int i = endIndex - period + 2; i <= endIndex; i++) {
            if (!Double.isNaN(values[i])) {
                lowest = Math.min(lowest, values[i]);
            }
        }
        return lowest;
    }

    private static double[] convertToList(List<StockData> data, double[] values) {
        return values;
    }
    
    /**
     * ADX Result class
     */
    public static class ADXResult {
        public double[] adx;
        public double[] plusDI;
        public double[] minusDI;
        
        public ADXResult(int size) {
            this.adx = new double[size];
            this.plusDI = new double[size];
            this.minusDI = new double[size];
        }
    }
    
    /**
     * Calculate ADX (Average Directional Index) - Trend Strength Indicator
     * ADX > 25: Strong trend, ADX < 20: Weak trend/ranging
     */
    public static ADXResult calculateADX(List<StockData> data, int period) {
        ADXResult result = new ADXResult(data.size());
        
        if (data.size() < period + 1) {
            for (int i = 0; i < data.size(); i++) {
                result.adx[i] = Double.NaN;
                result.plusDI[i] = Double.NaN;
                result.minusDI[i] = Double.NaN;
            }
            return result;
        }
        
        // Calculate True Range and Directional Movement
        double[] tr = new double[data.size()];
        double[] plusDM = new double[data.size()];
        double[] minusDM = new double[data.size()];
        
        for (int i = 1; i < data.size(); i++) {
            double high = data.get(i).getHigh();
            double low = data.get(i).getLow();
            double prevClose = data.get(i-1).getClose();
            double prevHigh = data.get(i-1).getHigh();
            double prevLow = data.get(i-1).getLow();
            
            // True Range
            tr[i] = Math.max(high - low, Math.max(
                Math.abs(high - prevClose), Math.abs(low - prevClose)));
            
            // Directional Movement
            double upMove = high - prevHigh;
            double downMove = prevLow - low;
            
            plusDM[i] = (upMove > downMove && upMove > 0) ? upMove : 0;
            minusDM[i] = (downMove > upMove && downMove > 0) ? downMove : 0;
        }
        
        // Smooth the values using Wilder's smoothing
        double[] smoothedTR = new double[data.size()];
        double[] smoothedPlusDM = new double[data.size()];
        double[] smoothedMinusDM = new double[data.size()];
        
        // First smoothed value is sum of first 'period' values
        for (int i = 1; i <= period; i++) {
            smoothedTR[period] += tr[i];
            smoothedPlusDM[period] += plusDM[i];
            smoothedMinusDM[period] += minusDM[i];
        }
        
        // Subsequent values use Wilder's smoothing
        for (int i = period + 1; i < data.size(); i++) {
            smoothedTR[i] = smoothedTR[i-1] - (smoothedTR[i-1] / period) + tr[i];
            smoothedPlusDM[i] = smoothedPlusDM[i-1] - (smoothedPlusDM[i-1] / period) + plusDM[i];
            smoothedMinusDM[i] = smoothedMinusDM[i-1] - (smoothedMinusDM[i-1] / period) + minusDM[i];
        }
        
        // Calculate +DI and -DI
        double[] dx = new double[data.size()];
        for (int i = period; i < data.size(); i++) {
            if (smoothedTR[i] == 0) {
                result.plusDI[i] = 0;
                result.minusDI[i] = 0;
                dx[i] = 0;
            } else {
                result.plusDI[i] = 100 * smoothedPlusDM[i] / smoothedTR[i];
                result.minusDI[i] = 100 * smoothedMinusDM[i] / smoothedTR[i];
                
                double diSum = result.plusDI[i] + result.minusDI[i];
                if (diSum == 0) {
                    dx[i] = 0;
                } else {
                    dx[i] = 100 * Math.abs(result.plusDI[i] - result.minusDI[i]) / diSum;
                }
            }
        }
        
        // Calculate ADX (smoothed DX)
        double adxSum = 0;
        for (int i = period; i < period * 2 && i < data.size(); i++) {
            adxSum += dx[i];
        }
        
        if (period * 2 <= data.size()) {
            result.adx[period * 2 - 1] = adxSum / period;
            
            for (int i = period * 2; i < data.size(); i++) {
                result.adx[i] = (result.adx[i-1] * (period - 1) + dx[i]) / period;
            }
        }
        
        // Fill initial values with NaN
        for (int i = 0; i < period * 2; i++) {
            if (i < period) {
                result.plusDI[i] = Double.NaN;
                result.minusDI[i] = Double.NaN;
            }
            result.adx[i] = Double.NaN;
        }
        
        return result;
    }
    
    /**
     * Candle Pattern class
     */
    public static class CandlePattern {
        public boolean isBullishEngulfing;
        public boolean isBearishEngulfing;
        public boolean isHammer;
        public boolean isShootingStar;
        public boolean isDoji;
        public boolean isBullishHarami;
        public boolean isBearishHarami;
        public double bodySize;      // Body size as % of total range
        public double upperWickSize;  // Upper wick as % of total range
        public double lowerWickSize;  // Lower wick as % of total range
        
        public CandlePattern() {
            this.isBullishEngulfing = false;
            this.isBearishEngulfing = false;
            this.isHammer = false;
            this.isShootingStar = false;
            this.isDoji = false;
            this.isBullishHarami = false;
            this.isBearishHarami = false;
            this.bodySize = 0;
            this.upperWickSize = 0;
            this.lowerWickSize = 0;
        }
    }
    
    /**
     * Analyze candle pattern at given index
     */
    public static CandlePattern analyzeCandlePattern(List<StockData> data, int index) {
        CandlePattern pattern = new CandlePattern();
        
        if (index < 1 || index >= data.size()) {
            return pattern;
        }
        
        StockData current = data.get(index);
        StockData prev = data.get(index - 1);
        
        double open = current.getOpen();
        double close = current.getClose();
        double high = current.getHigh();
        double low = current.getLow();
        
        double prevOpen = prev.getOpen();
        double prevClose = prev.getClose();
        double prevHigh = prev.getHigh();
        double prevLow = prev.getLow();
        
        // Calculate candle properties
        double range = high - low;
        if (range == 0) return pattern;
        
        double body = Math.abs(close - open);
        double upperWick = high - Math.max(open, close);
        double lowerWick = Math.min(open, close) - low;
        
        pattern.bodySize = body / range;
        pattern.upperWickSize = upperWick / range;
        pattern.lowerWickSize = lowerWick / range;
        
        boolean isBullish = close > open;
        boolean isBearish = close < open;
        boolean prevBullish = prevClose > prevOpen;
        boolean prevBearish = prevClose < prevOpen;
        
        // Doji: Small body (< 10% of range)
        pattern.isDoji = pattern.bodySize < 0.1;
        
        // Hammer: Small body at top, long lower wick (at least 2x body), short upper wick
        if (isBullish && pattern.lowerWickSize > pattern.bodySize * 2 && pattern.upperWickSize < pattern.bodySize * 0.5) {
            pattern.isHammer = true;
        }
        
        // Shooting Star: Small body at bottom, long upper wick (at least 2x body), short lower wick
        if (isBearish && pattern.upperWickSize > pattern.bodySize * 2 && pattern.lowerWickSize < pattern.bodySize * 0.5) {
            pattern.isShootingStar = true;
        }
        
        // Bullish Engulfing: Current bullish candle completely engulfs previous bearish candle
        if (prevBearish && isBullish && open < prevClose && close > prevOpen && body > Math.abs(prevClose - prevOpen)) {
            pattern.isBullishEngulfing = true;
        }
        
        // Bearish Engulfing: Current bearish candle completely engulfs previous bullish candle
        if (prevBullish && isBearish && open > prevClose && close < prevOpen && body > Math.abs(prevClose - prevOpen)) {
            pattern.isBearishEngulfing = true;
        }
        
        // Bullish Harami: Small bullish candle inside previous large bearish candle
        double prevBody = Math.abs(prevClose - prevOpen);
        if (prevBearish && isBullish && prevBody > body * 1.5 && close < prevOpen && open > prevClose) {
            pattern.isBullishHarami = true;
        }
        
        // Bearish Harami: Small bearish candle inside previous large bullish candle
        if (prevBullish && isBearish && prevBody > body * 1.5 && close > prevOpen && open < prevClose) {
            pattern.isBearishHarami = true;
        }
        
        return pattern;
    }
    
    /**
     * Support/Resistance Level class
     */
    public static class SupportResistanceLevel {
        public double level;
        public int touches;      // How many times price touched this level
        public boolean isSupport;
        public boolean isResistance;
        public double strength;  // 0-1, based on touches and age
        
        public SupportResistanceLevel(double level, int touches, boolean isSupport, boolean isResistance) {
            this.level = level;
            this.touches = touches;
            this.isSupport = isSupport;
            this.isResistance = isResistance;
            this.strength = Math.min(1.0, touches / 5.0); // Max strength at 5 touches
        }
    }
    
    /**
     * Find significant support and resistance levels
     * Uses pivot points and price clustering
     */
    public static List<SupportResistanceLevel> findSupportResistance(List<StockData> data, int lookback) {
        List<SupportResistanceLevel> levels = new ArrayList<>();
        
        if (data.size() < lookback + 10) {
            return levels;
        }
        
        int startIdx = data.size() - lookback;
        
        // Find pivot highs and lows
        for (int i = startIdx + 5; i < data.size() - 5; i++) {
            double high = data.get(i).getHigh();
            double low = data.get(i).getLow();
            
            // Check if it's a pivot high (resistance)
            boolean isPivotHigh = true;
            for (int j = i - 5; j <= i + 5; j++) {
                if (j != i && data.get(j).getHigh() > high) {
                    isPivotHigh = false;
                    break;
                }
            }
            
            // Check if it's a pivot low (support)
            boolean isPivotLow = true;
            for (int j = i - 5; j <= i + 5; j++) {
                if (j != i && data.get(j).getLow() < low) {
                    isPivotLow = false;
                    break;
                }
            }
            
            if (isPivotHigh) {
                // Check if level already exists (within 2%)
                boolean found = false;
                for (SupportResistanceLevel level : levels) {
                    if (Math.abs(level.level - high) / high < 0.02) {
                        level.touches++;
                        level.isResistance = true;
                        level.strength = Math.min(1.0, level.touches / 5.0);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    levels.add(new SupportResistanceLevel(high, 1, false, true));
                }
            }
            
            if (isPivotLow) {
                // Check if level already exists (within 2%)
                boolean found = false;
                for (SupportResistanceLevel level : levels) {
                    if (Math.abs(level.level - low) / low < 0.02) {
                        level.touches++;
                        level.isSupport = true;
                        level.strength = Math.min(1.0, level.touches / 5.0);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    levels.add(new SupportResistanceLevel(low, 1, true, false));
                }
            }
        }
        
        // Sort by strength (descending)
        levels.sort((a, b) -> Double.compare(b.strength, a.strength));
        
        return levels;
    }
    
    /**
     * Calculate buying/selling pressure from volume
     * Positive value = buying pressure, Negative = selling pressure
     */
    public static double[] calculateVolumePressure(List<StockData> data) {
        double[] pressure = new double[data.size()];
        
        for (int i = 1; i < data.size(); i++) {
            double close = data.get(i).getClose();
            double open = data.get(i).getOpen();
            double high = data.get(i).getHigh();
            double low = data.get(i).getLow();
            double volume = data.get(i).getVolume();
            
            double range = high - low;
            if (range == 0) {
                pressure[i] = 0;
                continue;
            }
            
            // Calculate close position within range
            double closePosition = (close - low) / range; // 0-1
            
            // Adjust for intraday movement
            double priceChange = close - open;
            double priceChangeRatio = range > 0 ? priceChange / range : 0;
            
            // Combine position and direction
            double pressureRatio = (closePosition * 0.7 + (priceChangeRatio + 1) / 2 * 0.3) * 2 - 1; // -1 to +1
            
            pressure[i] = pressureRatio * volume;
        }
        
        return pressure;
    }
    
    /**
     * Detect false breakout
     * Returns true if price broke a level but quickly reversed
     */
    public static boolean isFalseBreakout(List<StockData> data, int breakoutIndex, double level, boolean isUpBreakout) {
        if (breakoutIndex < 2 || breakoutIndex >= data.size() - 3) {
            return false;
        }
        
        // Check if price broke the level
        double breakoutClose = data.get(breakoutIndex).getClose();
        double breakoutHigh = data.get(breakoutIndex).getHigh();
        double breakoutLow = data.get(breakoutIndex).getLow();
        
        if (isUpBreakout) {
            // Upward breakout
            if (breakoutClose <= level) return false; // Not a breakout
            
            // Check next 1-3 candles for reversal
            int reversalCount = 0;
            for (int i = breakoutIndex + 1; i <= Math.min(breakoutIndex + 3, data.size() - 1); i++) {
                if (data.get(i).getClose() < level) {
                    reversalCount++;
                }
            }
            
            // If price closed below level within 3 candles, it's a false breakout
            return reversalCount >= 2;
            
        } else {
            // Downward breakout
            if (breakoutClose >= level) return false; // Not a breakout
            
            // Check next 1-3 candles for reversal
            int reversalCount = 0;
            for (int i = breakoutIndex + 1; i <= Math.min(breakoutIndex + 3, data.size() - 1); i++) {
                if (data.get(i).getClose() > level) {
                    reversalCount++;
                }
            }
            
            // If price closed above level within 3 candles, it's a false breakout
            return reversalCount >= 2;
        }
    }
}
