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
    
    // Inner class to hold daily OHLC data
    public static class DailyData {
        public String date;              // yyyy-MM-dd
        public double open;              // First hour's open price
        public double high;              // Highest price of day
        public double low;               // Lowest price of day
        public double close;             // Last hour's close price
        public String closingTime;       // HH:mm of last transaction
        public long timestamp;           // Timestamp of last transaction
        public SignalGenerator.TradePoint signal;  // Signal if occurred on this day

        public DailyData(String date, double open, double high, double low, double close) {
            this.date = date;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.closingTime = "";
            this.timestamp = 0;
            this.signal = null;
        }
    }
    
    // Helper class for signal information
    private static class SignalData {
        public String type;      // AL, GÜÇLÜ_AL, SAT, GÜÇLÜ_SAT
        public String time;      // HH:mm
        public String reason;    // Full reason text
        public SignalGenerator.TradePoint source;  // Original signal
        
        public SignalData(String type, String time, SignalGenerator.TradePoint source) {
            this.source = source;
            this.time = time;
            this.reason = source.reason;
            
            // Parse signal type from reason to distinguish GÜÇLÜ vs normal
            if (source.reason.contains("Güçlü AL")) {
                this.type = "GÜÇLÜ_AL";
            } else if (source.reason.contains("AL")) {
                this.type = "AL";
            } else if (source.reason.contains("Güçlü SAT")) {
                this.type = "GÜÇLÜ_SAT";
            } else if (source.reason.contains("SAT")) {
                this.type = "SAT";
            } else {
                this.type = type;  // fallback
            }
        }
    }
    
    /**
     * Aggregate hourly data to daily OHLC - ensures ONE point per day
     * Properly maintains: open=first hour, high=max, low=min, close=last hour
     */
    private static Map<String, DailyData> aggregateToDailyOHLC(List<StockData> hourlyData) {
        Map<String, DailyData> dailyMap = new LinkedHashMap<>();
        SimpleDateFormat dateOnlyFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
        
        for (StockData hourly : hourlyData) {
            String dateStr = dateOnlyFormat.format(new Date(hourly.getTimestamp()));
            String timeStr = timeFormat.format(new Date(hourly.getTimestamp()));
            
            if (!dailyMap.containsKey(dateStr)) {
                // First data point for this day - set OHLC with this hour's values
                DailyData daily = new DailyData(dateStr, 
                    hourly.getOpen(),                    // open = first hour's open
                    hourly.getHigh(),                    // high = first hour's high
                    hourly.getLow(),                     // low = first hour's low  
                    hourly.getClose());                  // close = first hour's close (will be updated)
                daily.closingTime = timeStr;
                daily.timestamp = hourly.getTimestamp();
                dailyMap.put(dateStr, daily);
            } else {
                // Subsequent data points for same day - update OHLC
                DailyData daily = dailyMap.get(dateStr);
                daily.high = Math.max(daily.high, hourly.getHigh());
                daily.low = Math.min(daily.low, hourly.getLow());
                daily.close = hourly.getClose();         // close = last hour's close
                daily.closingTime = timeStr;             // closingTime = last hour's time
                daily.timestamp = hourly.getTimestamp(); // timestamp = last hour's timestamp
            }
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
        
        // Aggregate hourly data to daily OHLC for display
        Map<String, DailyData> dailyMap = aggregateToDailyOHLC(data.subList(startIndex, data.size()));
        
        // Map signals to daily data and preserve signal timestamp
        Map<String, SignalData> signalsByDay = new LinkedHashMap<>();
        SimpleDateFormat dateOnlyFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat timeOnlyFormat = new SimpleDateFormat("HH:mm");
        
        for (SignalGenerator.TradePoint signal : tradeSignals) {
            if (signal.index >= startIndex && signal.index < data.size()) {
                StockData signalData = data.get(signal.index);
                String dateStr = dateOnlyFormat.format(new Date(signalData.getTimestamp()));
                String timeStr = timeOnlyFormat.format(new Date(signalData.getTimestamp()));
                signalsByDay.put(dateStr, new SignalData(signal.type, timeStr, signal));
            }
        }
        
        // Build daily data arrays with proper customdata
        List<String> dailyDates = new ArrayList<>();
        List<Double> dailyOpens = new ArrayList<>();
        List<Double> dailyCloses = new ArrayList<>();
        List<Double> dailyHighs = new ArrayList<>();
        List<Double> dailyLows = new ArrayList<>();
        List<String> closingTimes = new ArrayList<>();
        List<Double> dailyChanges = new ArrayList<>();
        List<String> signals = new ArrayList<>();
        List<String> dayNames = new ArrayList<>();
        SimpleDateFormat dayNameFormat = new SimpleDateFormat("EEEE", new Locale("tr", "TR"));
        
        double prevClose = 0;
        for (DailyData daily : dailyMap.values()) {
            dailyDates.add(daily.date);
            dailyOpens.add(daily.open);
            dailyCloses.add(daily.close);
            dailyHighs.add(daily.high);
            dailyLows.add(daily.low);
            closingTimes.add(daily.closingTime);
            
            // Get day name (Pazartesi, Salı, vb)
            String dayName = dayNameFormat.format(new Date(daily.timestamp));
            dayNames.add(dayName);
            
            // Calculate daily change % (vs previous day's close)
            double change = 0;
            if (prevClose > 0) {
                change = ((daily.close - prevClose) / prevClose) * 100;
            }
            if (Double.isNaN(change) || Double.isInfinite(change)) {
                change = 0.0;
            }
            dailyChanges.add(change);
            
            // Get signal info if available for this day
            String signalText = "";
            if (signalsByDay.containsKey(daily.date)) {
                SignalData sig = signalsByDay.get(daily.date);
                signalText = sig.source.reason;
            }
            signals.add(signalText);
            
            prevClose = daily.close;
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
        
        // Prepare data arrays for Plotly (daily aggregated)
        html.append("        // Data preparation (daily aggregated)\n");
        
        // Daily dates
        html.append("        var dates = [");
        for (int i = 0; i < dailyDates.size(); i++) {
            if (i > 0) html.append(", ");
            html.append("'").append(dailyDates.get(i)).append("'");
        }
        html.append("]\n\n");
        
        // Daily closing prices (for main chart)
        html.append("        var prices = [");
        for (int i = 0; i < dailyCloses.size(); i++) {
            if (i > 0) html.append(", ");
            html.append(String.format(Locale.US, "%.2f", dailyCloses.get(i)));
        }
        html.append("];\n\n");
        
        // Opening prices
        html.append("        var opens = [");
        for (int i = 0; i < dailyOpens.size(); i++) {
            if (i > 0) html.append(", ");
            html.append(String.format(Locale.US, "%.2f", dailyOpens.get(i)));
        }
        html.append("];\n\n");
        
        // High prices
        html.append("        var highs = [");
        for (int i = 0; i < dailyHighs.size(); i++) {
            if (i > 0) html.append(", ");
            html.append(String.format(Locale.US, "%.2f", dailyHighs.get(i)));
        }
        html.append("];\n\n");
        
        // Low prices
        html.append("        var lows = [");
        for (int i = 0; i < dailyLows.size(); i++) {
            if (i > 0) html.append(", ");
            html.append(String.format(Locale.US, "%.2f", dailyLows.get(i)));
        }
        html.append("];\n\n");
        
        // Daily changes
        html.append("        var dailyChanges = [");
        for (int i = 0; i < dailyChanges.size(); i++) {
            if (i > 0) html.append(", ");
            html.append(String.format(Locale.US, "%.2f", dailyChanges.get(i)));
        }
        html.append("];\n\n");
        
        // Closing times
        html.append("        var closingTimes = [");
        for (int i = 0; i < closingTimes.size(); i++) {
            if (i > 0) html.append(", ");
            html.append("'").append(closingTimes.get(i)).append("'");
        }
        html.append("];\n\n");
        
        // Signal types
        html.append("        var signalTypes = [");
        for (int i = 0; i < signals.size(); i++) {
            if (i > 0) html.append(", ");
            html.append("'").append(signals.get(i)).append("'");
        }
        html.append("];\n\n");
        
        // Day names (Pazartesi, Salı, etc)
        html.append("        var dayNames = [");
        for (int i = 0; i < dayNames.size(); i++) {
            if (i > 0) html.append(", ");
            html.append("'").append(dayNames.get(i)).append("'");
        }
        html.append("];\n\n");
        
        // SMA20 (daily downsampled from hourly)
        html.append("        var sma20 = [");
        for (int i = 0; i < dailyDates.size(); i++) {
            if (i > 0) html.append(", ");
            String dateStr = dailyDates.get(i);
            // Find last hourly index for this date
            int hourlyIndex = startIndex;
            for (int j = startIndex; j < data.size(); j++) {
                if (dateOnlyFormat.format(new Date(data.get(j).getTimestamp())).equals(dateStr)) {
                    hourlyIndex = j;
                } else if (dateOnlyFormat.format(new Date(data.get(j).getTimestamp())).compareTo(dateStr) > 0) {
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
        
        // SMA50 (daily downsampled)
        html.append("        var sma50 = [");
        for (int i = 0; i < dailyDates.size(); i++) {
            if (i > 0) html.append(", ");
            String dateStr = dailyDates.get(i);
            int hourlyIndex = startIndex;
            for (int j = startIndex; j < data.size(); j++) {
                if (dateOnlyFormat.format(new Date(data.get(j).getTimestamp())).equals(dateStr)) {
                    hourlyIndex = j;
                } else if (dateOnlyFormat.format(new Date(data.get(j).getTimestamp())).compareTo(dateStr) > 0) {
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
        
        // EMA12 (daily downsampled)
        html.append("        var ema12 = [");
        for (int i = 0; i < dailyDates.size(); i++) {
            if (i > 0) html.append(", ");
            String dateStr = dailyDates.get(i);
            int hourlyIndex = startIndex;
            for (int j = startIndex; j < data.size(); j++) {
                if (dateOnlyFormat.format(new Date(data.get(j).getTimestamp())).equals(dateStr)) {
                    hourlyIndex = j;
                } else if (dateOnlyFormat.format(new Date(data.get(j).getTimestamp())).compareTo(dateStr) > 0) {
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
        
        // RSI (daily downsampled)
        html.append("        var rsi = [");
        for (int i = 0; i < dailyDates.size(); i++) {
            if (i > 0) html.append(", ");
            String dateStr = dailyDates.get(i);
            int hourlyIndex = startIndex;
            for (int j = startIndex; j < data.size(); j++) {
                if (dateOnlyFormat.format(new Date(data.get(j).getTimestamp())).equals(dateStr)) {
                    hourlyIndex = j;
                } else if (dateOnlyFormat.format(new Date(data.get(j).getTimestamp())).compareTo(dateStr) > 0) {
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
        
        // Build BUY/SELL signals from daily data
        html.append("        // BUY/SELL signals (4 types)\n");
        html.append("        var alDates = [], alPrices = [], alTexts = [];\n");
        html.append("        var strongAlDates = [], strongAlPrices = [], strongAlTexts = [];\n");
        html.append("        var satDates = [], satPrices = [], satTexts = [];\n");
        html.append("        var strongSatDates = [], strongSatPrices = [], strongSatTexts = [];\n");
        
        for (int i = 0; i < dailyDates.size(); i++) {
            String dailyDate = dailyDates.get(i);
            if (signalsByDay.containsKey(dailyDate)) {
                SignalData sig = signalsByDay.get(dailyDate);
                double price = dailyCloses.get(i);
                double open = dailyOpens.get(i);
                double change = dailyChanges.get(i);
                String closingTime = closingTimes.get(i);
                String dayName = dayNames.get(i);
                // Build complete hover text with all fields (removed redundant Sinyal Saati)
                String displayText = "<b>Tarih: "+dailyDate+" ("+dayName+")</b><br>Açılış: "+String.format(Locale.US, "%.2f", open)+" TL<br>Kapanış: "+String.format(Locale.US, "%.2f", price)+" TL<br>Günlük Değişim: "+String.format(Locale.US, "%.2f", change)+"%<br>Kapanış Saati: "+closingTime+"<br>Sinyal: "+sig.reason;
                
                if (sig.type.equals("AL")) {
                    html.append("        alDates.push('").append(dailyDate).append("');\n");
                    html.append("        alPrices.push(").append(String.format(Locale.US, "%.2f", price)).append(");\n");
                    html.append("        alTexts.push('").append(displayText).append("');\n");
                } else if (sig.type.equals("GÜÇLÜ_AL")) {
                    html.append("        strongAlDates.push('").append(dailyDate).append("');\n");
                    html.append("        strongAlPrices.push(").append(String.format(Locale.US, "%.2f", price)).append(");\n");
                    html.append("        strongAlTexts.push('").append(displayText).append("');\n");
                } else if (sig.type.equals("SAT")) {
                    html.append("        satDates.push('").append(dailyDate).append("');\n");
                    html.append("        satPrices.push(").append(String.format(Locale.US, "%.2f", price)).append(");\n");
                    html.append("        satTexts.push('").append(displayText).append("');\n");
                } else if (sig.type.equals("GÜÇLÜ_SAT")) {
                    html.append("        strongSatDates.push('").append(dailyDate).append("');\n");
                    html.append("        strongSatPrices.push(").append(String.format(Locale.US, "%.2f", price)).append(");\n");
                    html.append("        strongSatTexts.push('").append(displayText).append("');\n");
                }
            }
        }
        html.append("\n");
        
        // Create Plotly traces
        html.append("        // Build custom data for hover template\n");
        html.append("        var customdata = [];\n");
        html.append("        for (let i = 0; i < dates.length; i++) {\n");
        html.append("            customdata.push([opens[i], dailyChanges[i], closingTimes[i], signalTypes[i], dayNames[i]]);\n");
        html.append("        }\n\n");
        
        html.append("        // Price chart traces\n");
        html.append("        var tracePrice = {\n");
        html.append("            x: dates,\n");
        html.append("            y: prices,\n");
        html.append("            customdata: customdata,\n");
        html.append("            type: 'scatter',\n");
        html.append("            mode: 'lines',\n");
        html.append("            name: 'Kapanış Fiyatı',\n");
        html.append("            line: { color: '#1a1a1a', width: 2 },\n");
        html.append("            hovertemplate: '<b>Tarih: %{x} (%{customdata[4]})</b><br>Açılış: %{customdata[0]:.2f} TL<br>Kapanış: %{y:.2f} TL<br>Günlük Değişim: %{customdata[1]:.2f}%<br>Kapanış Saati: %{customdata[2]}<br>Sinyal: %{customdata[3]}<extra></extra>',\n");
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
        html.append("            visible: false,\n");
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
        html.append("            visible: false,\n");
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
        html.append("            visible: false,\n");
        html.append("            yaxis: 'y'\n");
        html.append("        };\n\n");
        
        // AL signals (open green)
        html.append("        var traceAlSignals = {\n");
        html.append("            x: alDates,\n");
        html.append("            y: alPrices,\n");
        html.append("            text: alTexts,\n");
        html.append("            type: 'scatter',\n");
        html.append("            mode: 'markers',\n");
        html.append("            name: 'AL Sinyali',\n");
        html.append("            marker: { color: '#4ADE80', size: 12, symbol: 'triangle-up', line: { color: '#fff', width: 2 } },\n");
        html.append("            hovertemplate: '%{text}<extra></extra>',\n");
        html.append("            yaxis: 'y'\n");
        html.append("        };\n\n");
        
        // GÜÇLÜ AL signals (dark green)
        html.append("        var traceStrongAlSignals = {\n");
        html.append("            x: strongAlDates,\n");
        html.append("            y: strongAlPrices,\n");
        html.append("            text: strongAlTexts,\n");
        html.append("            type: 'scatter',\n");
        html.append("            mode: 'markers',\n");
        html.append("            name: 'GÜÇLÜ AL Sinyali',\n");
        html.append("            marker: { color: '#15803D', size: 13, symbol: 'triangle-up', line: { color: '#fff', width: 2 } },\n");
        html.append("            hovertemplate: '%{text}<extra></extra>',\n");
        html.append("            yaxis: 'y'\n");
        html.append("        };\n\n");
        
        // SAT signals (open red)
        html.append("        var traceSatSignals = {\n");
        html.append("            x: satDates,\n");
        html.append("            y: satPrices,\n");
        html.append("            text: satTexts,\n");
        html.append("            type: 'scatter',\n");
        html.append("            mode: 'markers',\n");
        html.append("            name: 'SAT Sinyali',\n");
        html.append("            marker: { color: '#FB7185', size: 12, symbol: 'triangle-down', line: { color: '#fff', width: 2 } },\n");
        html.append("            hovertemplate: '%{text}<extra></extra>',\n");
        html.append("            yaxis: 'y'\n");
        html.append("        };\n\n");
        
        // GÜÇLÜ SAT signals (dark red)
        html.append("        var traceStrongSatSignals = {\n");
        html.append("            x: strongSatDates,\n");
        html.append("            y: strongSatPrices,\n");
        html.append("            text: strongSatTexts,\n");
        html.append("            type: 'scatter',\n");
        html.append("            mode: 'markers',\n");
        html.append("            name: 'GÜÇLÜ SAT Sinyali',\n");
        html.append("            marker: { color: '#991B1B', size: 13, symbol: 'triangle-down', line: { color: '#fff', width: 2 } },\n");
        html.append("            hovertemplate: '%{text}<extra></extra>',\n");
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
        html.append("            visible: true,\n");
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
        html.append("            visible: false,\n");
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
        html.append("            visible: false,\n");
        html.append("            showlegend: false,\n");
        html.append("            hoverinfo: 'skip',\n");
        html.append("            yaxis: 'y2',\n");
        html.append("            xaxis: 'x'\n");
        html.append("        };\n\n");
        
        // Layout with subplots
        html.append("        // Layout configuration\n");
        html.append("        var layout = {\n");
        html.append("            title: {\n");
        html.append("                text: '").append(symbol).append(" - Teknik Analiz',\n");
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
        html.append("        var data = [tracePrice, traceSMA20, traceSMA50, traceEMA12, traceAlSignals, traceStrongAlSignals, traceSatSignals, traceStrongSatSignals, traceRSI, traceRSI70, traceRSI30];\n");
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
