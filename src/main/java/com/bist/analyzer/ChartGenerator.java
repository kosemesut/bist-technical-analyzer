package com.bist.analyzer;

import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;

/**
 * Interactive chart generator using Plotly.js
 * Generates HTML charts with hover tooltips showing date and price information
 */
public class ChartGenerator {
    
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    
    /**
     * Daily aggregated OHLC data
     */
    public static class DailyData {
        public String date;           // yyyy-MM-dd
        public double open;           // İlk fiyat
        public double high;           // Gün içindeki en yüksek
        public double low;            // Gün içindeki en düşük
        public double close;          // Son fiyat (kapanış)
        public String closingTime;    // Son işlem saati (HH:mm)
        public long timestamp;        // Son işlemin timestamp'i
        public SignalGenerator.TradePoint dailySignal; // Gün için sinyal (varsa)
        
        public DailyData(String date, double open, double close, long timestamp) {
            this.date = date;
            this.open = open;
            this.close = close;
            this.high = close;
            this.low = close;
            this.timestamp = timestamp;
        }
    }
    
    /**
     * Aggregate hourly data to daily OHLC (Open/High/Low/Close)
     * Returns LinkedHashMap to preserve insertion order
     */
    private static Map<String, DailyData> aggregateToDailyOHLC(List<StockData> hourlyData) {
        Map<String, DailyData> dailyMap = new LinkedHashMap<>();
        SimpleDateFormat dateOnlyFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
        
        for (StockData hourly : hourlyData) {
            String dateStr = dateOnlyFormat.format(new Date(hourly.getTimestamp()));
            String timeStr = timeFormat.format(new Date(hourly.getTimestamp()));
            
            DailyData daily = dailyMap.getOrDefault(dateStr, new DailyData(dateStr, hourly.getOpen(), hourly.getClose(), hourly.getTimestamp()));
            
            // Update OHLC
            daily.open = Math.min(daily.open, hourly.getOpen());  // İlk saatin açılışı
            daily.high = Math.max(daily.high, hourly.getHigh());
            daily.low = Math.min(daily.low, hourly.getLow());
            daily.close = hourly.getClose();  // Son işlemin kapanışı
            daily.closingTime = timeStr;
            daily.timestamp = hourly.getTimestamp();
            
            dailyMap.put(dateStr, daily);
        }
        
        return dailyMap;
    }
    
    /**
     * Generate interactive HTML chart (replaces old PNG chart)
     */
    public static void generateCandleChart(String symbol, List<StockData> data, String outputPath) throws IOException {
        if (data.isEmpty()) {
            System.out.println("No data available for chart generation");
            return;
        }
        
        // Redirect to technical chart
        generateTechnicalChart(symbol, data, new double[data.size()], new double[data.size()], 
                             new double[data.size()], new double[data.size()], outputPath);
    }

    /**
     * Generate interactive multi-panel HTML chart with Plotly.js:
     * Panel 1 (75%): Price + SMA20 + SMA50 + EMA12 + BUY/SELL signals
     * Panel 2 (25%): RSI with oversold/overbought levels
     * 
     * Features:
     * - Mouse hover shows date + price
     * - Zoom and pan
     * - Interactive legend (click to show/hide)
     * - Responsive design
     */
    public static void generateTechnicalChart(String symbol, List<StockData> data, 
                                            double[] sma20, double[] sma50, double[] ema12,
                                            double[] rsi, String outputPath) throws IOException {
        generateTechnicalChart(symbol, data, sma20, sma50, ema12, rsi, outputPath, null);
    }
    
    /**
     * Generate technical chart with optional current signal (last day's signal)
     */
    public static void generateTechnicalChart(String symbol, List<StockData> data, 
                                            double[] sma20, double[] sma50, double[] ema12,
                                            double[] rsi, String outputPath, SignalGenerator.SignalResult currentSignal) throws IOException {
        generateTechnicalChartInternal(symbol, data, sma20, sma50, ema12, rsi, outputPath, currentSignal, false);
    }
    
    /**
     * Generate 1-month visual chart for mobile (calculations use full data, display shows last 30 days)
     */
    public static void generateTechnicalChart1Month(String symbol, List<StockData> data, 
                                            double[] sma20, double[] sma50, double[] ema12,
                                            double[] rsi, String outputPath, SignalGenerator.SignalResult currentSignal) throws IOException {
        generateTechnicalChartInternal(symbol, data, sma20, sma50, ema12, rsi, outputPath, currentSignal, true);
    }
    
    /**
     * Internal method - if oneMonthOnly=true, displays only last 30 days visually (calculations remain full)
     */
    private static void generateTechnicalChartInternal(String symbol, List<StockData> data, 
                                            double[] sma20, double[] sma50, double[] ema12,
                                            double[] rsi, String outputPath, SignalGenerator.SignalResult currentSignal,
                                            boolean oneMonthOnly) throws IOException {
        if (data.isEmpty()) {
            System.out.println("No data available for technical chart generation");
            return;
        }

        // Calculate indicators if not provided
        if (sma20[0] == 0.0) sma20 = TechnicalIndicators.calculateSMA(data, 20);
        if (sma50[0] == 0.0) sma50 = TechnicalIndicators.calculateSMA(data, 50);
        if (ema12[0] == 0.0) ema12 = TechnicalIndicators.calculateEMA(data, 12);
        if (rsi[0] == 0.0) rsi = TechnicalIndicators.calculateRSI(data, 14);
        
        // Find historical BUY/SELL signals (use full hourly data)
        List<SignalGenerator.TradePoint> tradeSignals = SignalGenerator.findHistoricalSignals(data, sma20, sma50, ema12, rsi);
        
        // Add current signal if provided (last day's signal)
        if (currentSignal != null && !data.isEmpty()) {
            // Check if the last historical signal is from the same day as current signal
            // If not, add the current signal to the chart
            StockData currentData = data.get(data.size() - 1);
            
            if (!tradeSignals.isEmpty()) {
                SignalGenerator.TradePoint lastSignal = tradeSignals.get(tradeSignals.size() - 1);
                StockData lastData = data.get(lastSignal.index);
                
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                String lastSignalDate = sdf.format(new Date(lastData.getTimestamp()));
                String currentDate = sdf.format(new Date(currentData.getTimestamp()));
                
                // Add current signal if it's from a different day than the last historical signal
                if (!lastSignalDate.equals(currentDate)) {
                    String signalType = null;
                    if (currentSignal.signal.equals("STRONG_BUY") || currentSignal.signal.equals("BUY")) {
                        signalType = "BUY";
                    } else if (currentSignal.signal.equals("STRONG_SELL") || currentSignal.signal.equals("SELL")) {
                        signalType = "SELL";
                    }
                    
                    if (signalType != null) {
                        double price = currentData.getClose();
                        String reason = "Son Gün: " + currentSignal.signal.replace("_", " ");
                        tradeSignals.add(new SignalGenerator.TradePoint(data.size() - 1, signalType, price, reason));
                    }
                }
            } else if (currentSignal != null) {
                // Add current signal if no historical signals exist
                String signalType = null;
                if (currentSignal.signal.equals("STRONG_BUY") || currentSignal.signal.equals("BUY")) {
                    signalType = "BUY";
                } else if (currentSignal.signal.equals("STRONG_SELL") || currentSignal.signal.equals("SELL")) {
                    signalType = "SELL";
                }
                
                if (signalType != null) {
                    double price = currentData.getClose();
                    String reason = "Son Gün: " + currentSignal.signal.replace("_", " ");
                    tradeSignals.add(new SignalGenerator.TradePoint(data.size() - 1, signalType, price, reason));
                }
            }
        }

        // For mobile 1-month view: filter display data to last 30 days
        int startIndex = 0;
        if (oneMonthOnly && data.size() > 0) {
            long thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
            for (int i = data.size() - 1; i >= 0; i--) {
                if (data.get(i).getTimestamp() < thirtyDaysAgo) {
                    startIndex = i + 1;
                    break;
                }
            }
        }
        
        // Aggregate hourly data to daily OHLC for chart display
        Map<String, DailyData> dailyMap = aggregateToDailyOHLC(data.subList(startIndex, data.size()));
        
        // Map signals to daily data
        Map<String, SignalGenerator.TradePoint> signalsByDate = new LinkedHashMap<>();
        SimpleDateFormat dateOnlyFormat = new SimpleDateFormat("yyyy-MM-dd");
        for (SignalGenerator.TradePoint signal : tradeSignals) {
            if (signal.index >= startIndex && signal.index < data.size()) {
                StockData signalData = data.get(signal.index);
                String dateStr = dateOnlyFormat.format(new Date(signalData.getTimestamp()));
                signalsByDate.put(dateStr, signal);
            }
        }
        
        // Build HTML with Plotly.js
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>").append(symbol).append(" - Teknik Analiz</title>\n");
        html.append("    <script src=\"https://cdn.plot.ly/plotly-2.27.0.min.js\"></script>\n");
        html.append("    <style>\n");
        html.append("        body { margin: 0; padding: 20px; background: #f5f5f5; font-family: 'Segoe UI', Arial, sans-serif; }\n");
        html.append("        .chart-container { background: white; border-radius: 8px; padding: 20px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }\n");
        html.append("        h2 { margin: 0 0 20px 0; color: #1a1a1a; font-size: 24px; }\n");
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"chart-container\">\n");
        html.append("        <h2>📊 ").append(symbol).append(" - Teknik Analiz Grafiği</h2>\n");
        html.append("        <div id=\"chart\" style=\"width:100%; height:800px;\"></div>\n");
        html.append("    </div>\n");
        html.append("    <script>\n");
        
        // Prepare data arrays for Plotly (daily aggregated data)
        html.append("        // Data preparation (daily aggregated)\n");
        SimpleDateFormat timeOnlyFormat = new SimpleDateFormat("HH:mm");
        
        // Build daily data arrays
        List<String> dailyDates = new ArrayList<>();
        List<Double> dailyOpens = new ArrayList<>();
        List<Double> dailyCloses = new ArrayList<>();
        List<Double> dailyHighs = new ArrayList<>();
        List<Double> dailyLows = new ArrayList<>();
        List<String> closingTimes = new ArrayList<>();
        List<String> dailyChangeTexts = new ArrayList<>();
        List<String> signalTexts = new ArrayList<>();
        List<Double> dailyChangeValues = new ArrayList<>();
        
        double prevDayClose = 0;
        int dayCount = 0;
        for (DailyData daily : dailyMap.values()) {
            dailyDates.add(daily.date);
            dailyOpens.add(daily.open);
            dailyCloses.add(daily.close);
            dailyHighs.add(daily.high);
            dailyLows.add(daily.low);
            closingTimes.add(daily.closingTime);
            
            // Calculate daily change %
            double dailyChange = 0;
            if (dayCount > 0 && prevDayClose > 0) {
                dailyChange = ((daily.close - prevDayClose) / prevDayClose) * 100;
            }
            if (Double.isNaN(dailyChange) || Double.isInfinite(dailyChange)) {
                dailyChange = 0.0;
            }
            dailyChangeValues.add(dailyChange);
            
            // Check if signal occurred on this day
            String signalInfo = "";
            if (signalsByDate.containsKey(daily.date)) {
                SignalGenerator.TradePoint signal = signalsByDate.get(daily.date);
                String signalTime = timeOnlyFormat.format(new Date(daily.timestamp));
                signalInfo = signal.type + " | " + signalTime;
            }
            signalTexts.add(signalInfo);
            
            prevDayClose = daily.close;
            dayCount++;
        }
        
        // Write dates array
        html.append("        var dates = [");
        for (int i = 0; i < dailyDates.size(); i++) {
            if (i > 0) html.append(", ");
            html.append("'").append(dailyDates.get(i)).append("'");
        }
        html.append("]\n\n");
        
        // Write closing prices
        html.append("        var prices = [");
        for (int i = 0; i < dailyCloses.size(); i++) {
            if (i > 0) html.append(", ");
            html.append(String.format(Locale.US, "%.2f", dailyCloses.get(i)));
        }
        html.append("];\n\n");
        
        // Write daily changes
        html.append("        var dailyChanges = [");
        for (int i = 0; i < dailyChangeValues.size(); i++) {
            if (i > 0) html.append(", ");
            html.append(String.format(Locale.US, "%.2f", dailyChangeValues.get(i)));
        }
        html.append("];\n\n");
        
        // Write hourly SMA/EMA/RSI aligned to daily data (sample at each day's closing index)
        html.append("        var sma20 = [");
        for (int i = 0; i < dailyDates.size(); i++) {
            if (i > 0) html.append(", ");
            String date = dailyDates.get(i);
            // Find the last hourly index for this date in original data
            int hourlyIndex = startIndex;
            for (int j = startIndex; j < data.size(); j++) {
                if (dateOnlyFormat.format(new Date(data.get(j).getTimestamp())).equals(date)) {
                    hourlyIndex = j;
                } else if (dateOnlyFormat.format(new Date(data.get(j).getTimestamp())).compareTo(date) > 0) {
                    break;
                }
            }
            if (hourlyIndex < sma20.length && !Double.isNaN(sma20[hourlyIndex])) {
                html.append(String.format(Locale.US, "%.2f", sma20[hourlyIndex]));
            } else {
                html.append("null");
            }
        }
        html.append("];\n\n");
        
        // Write SMA50
        html.append("        var sma50 = [");
        for (int i = 0; i < dailyDates.size(); i++) {
            if (i > 0) html.append(", ");
            String date = dailyDates.get(i);
            int hourlyIndex = startIndex;
            for (int j = startIndex; j < data.size(); j++) {
                if (dateOnlyFormat.format(new Date(data.get(j).getTimestamp())).equals(date)) {
                    hourlyIndex = j;
                } else if (dateOnlyFormat.format(new Date(data.get(j).getTimestamp())).compareTo(date) > 0) {
                    break;
                }
            }
            if (hourlyIndex < sma50.length && !Double.isNaN(sma50[hourlyIndex])) {
                html.append(String.format(Locale.US, "%.2f", sma50[hourlyIndex]));
            } else {
                html.append("null");
            }
        }
        html.append("];\n\n");
        
        // Write EMA12
        html.append("        var ema12 = [");
        for (int i = 0; i < dailyDates.size(); i++) {
            if (i > 0) html.append(", ");
            String date = dailyDates.get(i);
            int hourlyIndex = startIndex;
            for (int j = startIndex; j < data.size(); j++) {
                if (dateOnlyFormat.format(new Date(data.get(j).getTimestamp())).equals(date)) {
                    hourlyIndex = j;
                } else if (dateOnlyFormat.format(new Date(data.get(j).getTimestamp())).compareTo(date) > 0) {
                    break;
                }
            }
            if (hourlyIndex < ema12.length && !Double.isNaN(ema12[hourlyIndex])) {
                html.append(String.format(Locale.US, "%.2f", ema12[hourlyIndex]));
            } else {
                html.append("null");
            }
        }
        html.append("];\n\n");
        
        // Write RSI
        html.append("        var rsi = [");
        for (int i = 0; i < dailyDates.size(); i++) {
            if (i > 0) html.append(", ");
            String date = dailyDates.get(i);
            int hourlyIndex = startIndex;
            for (int j = startIndex; j < data.size(); j++) {
                if (dateOnlyFormat.format(new Date(data.get(j).getTimestamp())).equals(date)) {
                    hourlyIndex = j;
                } else if (dateOnlyFormat.format(new Date(data.get(j).getTimestamp())).compareTo(date) > 0) {
                    break;
                }
            }
            if (hourlyIndex < rsi.length && !Double.isNaN(rsi[hourlyIndex])) {
                html.append(String.format(Locale.US, "%.2f", rsi[hourlyIndex]));
            } else {
                html.append("null");
            }
        }
        html.append("];\n\n");
        
        // BUY/SELL signals (mapped to daily data)
        html.append("        // BUY/SELL signals on daily data\n");
        html.append("        var buyDates = [], buyPrices = [], buyTexts = [];\n");
        html.append("        var sellDates = [], sellPrices = [], sellTexts = [];\n");
        
        // Iterate through daily data and add signals
        for (int i = 0; i < dailyDates.size(); i++) {
            String dailyDate = dailyDates.get(i);
            if (signalsByDate.containsKey(dailyDate)) {
                SignalGenerator.TradePoint signal = signalsByDate.get(dailyDate);
                double price = dailyCloses.get(i); // Use daily closing price for signal marker
                String timeStr = closingTimes.get(i);
                String displayText = "<b>" + dailyDate + "</b>\\nSaat: " + timeStr + "\\n" + signal.type;                
                if (signal.type.equals("BUY")) {
                    html.append("        buyDates.push('").append(dailyDate).append("');\n");
                    html.append("        buyPrices.push(").append(String.format(Locale.US, "%.2f", price)).append(");\n");
                    html.append("        buyTexts.push('").append(displayText).append("');\n");
                } else if (signal.type.equals("SELL")) {
                    html.append("        sellDates.push('").append(dailyDate).append("');\n");
                    html.append("        sellPrices.push(").append(String.format(Locale.US, "%.2f", price)).append(");\n");
                    html.append("        sellTexts.push('").append(displayText).append("');\n");
                }
            }
        }
        html.append("\n");
        
        // Create Plotly traces
        html.append("        // Price chart traces\n");
        html.append("        var tracePrice = {\n");
        html.append("            x: dates,\n");
        html.append("            y: prices,\n");
        html.append("            customdata: [dailyChangeValues, closingTimes],\n");
        html.append("            type: 'scatter',\n");
        html.append("            mode: 'lines',\n");
        html.append("            name: 'Kapanış Fiyatı',\n");
        html.append("            line: { color: '#1a1a1a', width: 2 },\n");
        html.append("            hovertemplate: '<b>Tarih: %{x}</b><br>Kapanış: %{y:.2f} TL<br>Günlük Değişim: %{customdata[0]:.2f}%<br>Kapanış Saati: %{customdata[1]}<extra></extra>',\n");
        html.append("            yaxis: 'y'\n");
        html.append("        };\n\n");
        
        html.append("        var traceSMA20 = {\n");
        html.append("            x: dates,\n");
        html.append("            y: sma20,\n");
        html.append("            type: 'scatter',\n");
        html.append("            mode: 'lines',\n");
        html.append("            name: 'SMA20',\n");
        html.append("            line: { color: '#4285F4', width: 1.5, dash: 'dot' },\n");
        html.append("            hovertemplate: '<b>%{x|%Y-%m-%d %H:%M}</b><br>SMA20: %{y:.2f} TL<extra></extra>',\n");
        html.append("            yaxis: 'y'\n");
        html.append("        };\n\n");
        
        html.append("        var traceSMA50 = {\n");
        html.append("            x: dates,\n");
        html.append("            y: sma50,\n");
        html.append("            type: 'scatter',\n");
        html.append("            mode: 'lines',\n");
        html.append("            name: 'SMA50',\n");
        html.append("            line: { color: '#FBBC05', width: 1.5, dash: 'dot' },\n");
        html.append("            hovertemplate: '<b>%{x|%Y-%m-%d %H:%M}</b><br>SMA50: %{y:.2f} TL<extra></extra>',\n");
        html.append("            yaxis: 'y'\n");
        html.append("        };\n\n");
        
        html.append("        var traceEMA12 = {\n");
        html.append("            x: dates,\n");
        html.append("            y: ema12,\n");
        html.append("            type: 'scatter',\n");
        html.append("            mode: 'lines',\n");
        html.append("            name: 'EMA12',\n");
        html.append("            line: { color: '#34D399', width: 1.5, dash: 'dash' },\n");
        html.append("            hovertemplate: '<b>%{x|%Y-%m-%d %H:%M}</b><br>EMA12: %{y:.2f} TL<extra></extra>',\n");
        html.append("            yaxis: 'y'\n");
        html.append("        };\n\n");
        
        // Merge BUY and SELL into single signal line (chronological)
        html.append("        var signalLine = [];\n");
        html.append("        for (let i = 0; i < buyDates.length; i++) {\n");
        html.append("            signalLine.push({date: buyDates[i], price: buyPrices[i], type: 'BUY'});\n");
        html.append("        }\n");
        html.append("        for (let i = 0; i < sellDates.length; i++) {\n");
        html.append("            signalLine.push({date: sellDates[i], price: sellPrices[i], type: 'SELL'});\n");
        html.append("        }\n");
        html.append("        signalLine.sort((a, b) => new Date(a.date) - new Date(b.date));\n");
        html.append("        var signalLineX = signalLine.map(p => p.date);\n");
        html.append("        var signalLineY = signalLine.map(p => p.price);\n");
        html.append("        var signalLineText = signalLine.map(p => {\n");
        html.append("            return '<b>' + p.date + '</b>';\n");
        html.append("        });\n\n");
        
        // Signal line trace (connects all signal points)
        html.append("        var traceSignalLine = {\n");
        html.append("            x: signalLineX,\n");
        html.append("            y: signalLineY,\n");
        html.append("            text: signalLineText,\n");
        html.append("            type: 'scatter',\n");
        html.append("            mode: 'lines+markers',\n");
        html.append("            name: 'Sinyal Fiyatı (Turuncu)',\n");
        html.append("            line: { color: '#F97316', width: 2, dash: 'solid' },\n");
        html.append("            marker: { color: '#F97316', size: 6 },\n");
        html.append("            hovertemplate: '%{text}<br>Sinyal Fiyatı: %{y:.2f} TL<extra></extra>',\n");
        html.append("            yaxis: 'y'\n");
        html.append("        };\n\n");
        
        // BUY signals
        html.append("        var traceBuySignals = {\n");
        html.append("            x: buyDates,\n");
        html.append("            y: buyPrices,\n");
        html.append("            text: buyTexts,\n");
        html.append("            type: 'scatter',\n");
        html.append("            mode: 'markers',\n");
        html.append("            name: 'AL Sinyali',\n");
        html.append("            marker: { color: '#22C55E', size: 12, symbol: 'triangle-up', line: { color: '#fff', width: 2 } },\n");
        html.append("            hovertemplate: '%{text}<br>Fiyat: %{y:.2f} TL<extra></extra>',\n");
        html.append("            yaxis: 'y'\n");
        html.append("        };\n\n");
        
        // SELL signals
        html.append("        var traceSellSignals = {\n");
        html.append("            x: sellDates,\n");
        html.append("            y: sellPrices,\n");
        html.append("            text: sellTexts,\n");
        html.append("            type: 'scatter',\n");
        html.append("            mode: 'markers',\n");
        html.append("            name: 'SAT Sinyali',\n");
        html.append("            marker: { color: '#EF4444', size: 12, symbol: 'triangle-down', line: { color: '#fff', width: 2 } },\n");
        html.append("            hovertemplate: '%{text}<br>Fiyat: %{y:.2f} TL<extra></extra>',\n");
        html.append("            yaxis: 'y'\n");
        html.append("        };\n\n");
        
        // RSI trace
        html.append("        // RSI chart trace\n");
        html.append("        var traceRSI = {\n");
        html.append("            x: dates,\n");
        html.append("            y: rsi,\n");
        html.append("            type: 'scatter',\n");
        html.append("            mode: 'lines',\n");
        html.append("            name: 'RSI(14)',\n");
        html.append("            line: { color: '#A855F7', width: 2 },\n");
        html.append("            hovertemplate: '<b>%{x}</b><br>RSI: %{y:.1f}<extra></extra>',\n");
        html.append("            yaxis: 'y2',\n");
        html.append("            xaxis: 'x'\n");
        html.append("        };\n\n");
        
        // RSI reference lines (70=overbought, 30=oversold)
        html.append("        var traceRSI70 = {\n");
        html.append("            x: dates,\n");
        html.append("            y: Array(dates.length).fill(70),\n");
        html.append("            type: 'scatter',\n");
        html.append("            mode: 'lines',\n");
        html.append("            name: 'RSI 70 (Aşırı Alım)',\n");
        html.append("            line: { color: '#EF4444', width: 1, dash: 'dash' },\n");
        html.append("            showlegend: false,\n");
        html.append("            hoverinfo: 'skip',\n");
        html.append("            yaxis: 'y2',\n");
        html.append("            xaxis: 'x'\n");
        html.append("        };\n\n");
        
        html.append("        var traceRSI30 = {\n");
        html.append("            x: dates,\n");
        html.append("            y: Array(dates.length).fill(30),\n");
        html.append("            type: 'scatter',\n");
        html.append("            mode: 'lines',\n");
        html.append("            name: 'RSI 30 (Aşırı Satış)',\n");
        html.append("            line: { color: '#22C55E', width: 1, dash: 'dash' },\n");
        html.append("            showlegend: false,\n");
        html.append("            hoverinfo: 'skip',\n");
        html.append("            yaxis: 'y2',\n");
        html.append("            xaxis: 'x'\n");
        html.append("        };\n\n");
        
        // Layout with subplots
        html.append("        // Layout configuration\n");
        html.append("        var layout = {\n");
        html.append("            title: {\n");
        html.append("                text: '").append(symbol).append(" - Son ").append(data.size()).append(" Günlük Teknik Analiz',\n");
        html.append("                font: { size: 18, color: '#1a1a1a' }\n");
        html.append("            },\n");
        html.append("            xaxis: {\n");
        html.append("                title: 'Tarih',\n");
        html.append("                type: 'date',\n");
        html.append("                tickformat: '%Y-%m',\n");
        html.append("                nticks: 12,\n");
        html.append("                showgrid: true,\n");
        html.append("                gridcolor: '#e5e5e5'\n");
        html.append("            },\n");
        html.append("            yaxis: {\n");
        html.append("                title: 'Fiyat (₺)',\n");
        html.append("                domain: [0.3, 1],\n"); // Top 70% of chart
        html.append("                showgrid: true,\n");
        html.append("                gridcolor: '#e5e5e5'\n");
        html.append("            },\n");
        html.append("            yaxis2: {\n");
        html.append("                title: 'RSI',\n");
        html.append("                domain: [0, 0.25],\n"); // Bottom 25% of chart
        html.append("                range: [0, 100],\n");
        html.append("                showgrid: true,\n");
        html.append("                gridcolor: '#e5e5e5'\n");
        html.append("            },\n");
        html.append("            hovermode: 'closest',\n");
        html.append("            plot_bgcolor: '#fafafa',\n");
        html.append("            paper_bgcolor: '#ffffff',\n");
        html.append("            font: { family: 'Segoe UI, Arial', size: 12 },\n");
        html.append("            legend: {\n");
        html.append("                x: 0,\n");
        html.append("                y: 1.1,\n");
        html.append("                orientation: 'h',\n");
        html.append("                bgcolor: 'rgba(255,255,255,0.9)',\n");
        html.append("                bordercolor: '#e5e5e5',\n");
        html.append("                borderwidth: 1\n");
        html.append("            },\n");
        html.append("            margin: { t: 100, b: 80, l: 80, r: 50 }\n");
        html.append("        };\n\n");
        
        // Config
        html.append("        var config = {\n");
        html.append("            responsive: true,\n");
        html.append("            displayModeBar: true,\n");
        html.append("            displaylogo: false,\n");
        html.append("            modeBarButtonsToRemove: ['toImage', 'sendDataToCloud'],\n");
        html.append("            locale: 'tr'\n");
        html.append("        };\n\n");
        
        // Plot
        html.append("        // Render chart\n");
        html.append("        var data = [tracePrice, traceSignalLine, traceSMA20, traceSMA50, traceEMA12, traceBuySignals, traceSellSignals, traceRSI, traceRSI70, traceRSI30];\n");
        html.append("        Plotly.newPlot('chart', data, layout, config);\n");
        
        html.append("    </script>\n");
        html.append("</body>\n");
        html.append("</html>");
        
        // Write to file with UTF-8 encoding (fixes Turkish characters and ₺ symbol)
        String htmlPath = outputPath.replace(".png", oneMonthOnly ? "_1m.html" : ".html");
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(htmlPath), StandardCharsets.UTF_8)) {
            writer.write(html.toString());
        }
        
        if (!oneMonthOnly) {
            System.out.println("✅ İnteraktif grafik oluşturuldu: " + htmlPath);
        }
    }
    
    /**
     * Aggregate hourly data to daily EOD (End of Day) - last hour of each trading day
     * Limits to max 360 days
     */
    private static List<StockData> aggregateToDailyEOD(List<StockData> hourlyData) {
        if (hourlyData.isEmpty()) return hourlyData;
        List<StockData> dailyData = new java.util.ArrayList<>();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
        String lastDate = "";
        for (int i = hourlyData.size() - 1; i >= 0; i--) {
            StockData data = hourlyData.get(i);
            String currentDate = sdf.format(new java.util.Date(data.getTimestamp()));
            if (!currentDate.equals(lastDate)) {
                // New day found, this is the EOD for this day (since we're going backwards)
                dailyData.add(0, data);
                lastDate = currentDate;
                // Limit to 360 days
                if (dailyData.size() >= 360) {
                    break;
                }
            }
        }
        return dailyData;
    }
    
    /**
     * Deprecated: generateCombinedPriceVolumeChart() - Not needed for HTML charts
     */
    public static void generateCombinedPriceVolumeChart(String symbol, List<StockData> data, String outputPath) throws IOException {
        // Simply redirect to main chart
        generateTechnicalChart(symbol, data, new double[data.size()], new double[data.size()], 
                             new double[data.size()], new double[data.size()], outputPath);
    }
}
