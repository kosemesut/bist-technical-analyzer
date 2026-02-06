package com.bist.analyzer;

import java.util.List;

public class TechnicalIndicators {

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
        
        result.signalLine = calculateSMA(convertToList(data, result.macdLine), signal);
        
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

    private static double[] convertToList(List<StockData> data, double[] values) {
        return values;
    }
}
