package com.bist.analyzer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class BistAnalyzerApp {
    
    private static final String STOCK_LIST_FILE = "stock_list.txt";
    private static final String OUTPUT_DIR = "output";
    private static final String CHARTS_DIR = "output/charts";

    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════════════════════╗");
        System.out.println("║    BIST Technical Analysis System Started         ║");
        System.out.println("╚═══════════════════════════════════════════════════╝\n");

        try {
            // Create output directories
            Files.createDirectories(Paths.get(OUTPUT_DIR));
            Files.createDirectories(Paths.get(CHARTS_DIR));

            // Read stock list
            List<String> stocks = readStockList();
            if (stocks.isEmpty()) {
                System.err.println("No stocks found in " + STOCK_LIST_FILE);
                return;
            }

            System.out.println("Stocks to analyze: " + stocks);
            System.out.println(repeat("═", 50) + "\n");

            // Data containers
            List<SignalGenerator.SignalResult> allSignals = new ArrayList<>();
            Map<String, List<StockData>> allData = new HashMap<>();

            // Process each stock
            for (String stock : stocks) {
                stock = stock.trim().toUpperCase();
                if (stock.isEmpty()) continue;

                System.out.println("\n📊 Processing: " + stock);
                System.out.println(repeat("─", 50));

                try {
                    // Fetch hourly data (1h, 3mo) for short-term analysis
                    System.out.println("  Fetching hourly data (1h)...");
                    List<StockData> hourlyData = StockDataFetcher.fetchData(stock, "1h", "3mo");
                    
                    // Fetch daily data (1d, 5y) for long-term analysis
                    System.out.println("  Fetching daily data (1d)...");
                    List<StockData> dailyData = StockDataFetcher.fetchData(stock, "1d", "5y");

                    if (hourlyData.isEmpty() && dailyData.isEmpty()) {
                        System.err.println("  ✗ No data retrieved for " + stock);
                        continue;
                    }

                    // Use hourly data if available, otherwise daily
                    List<StockData> analysisData = !hourlyData.isEmpty() ? hourlyData : dailyData;
                    allData.put(stock, analysisData);

                    // Calculate technical indicators
                    System.out.println("  Calculating technical indicators...");
                    double[] sma20 = TechnicalIndicators.calculateSMA(analysisData, 20);
                    double[] sma50 = TechnicalIndicators.calculateSMA(analysisData, 50);
                    double[] ema12 = TechnicalIndicators.calculateEMA(analysisData, 12);
                    double[] rsi = TechnicalIndicators.calculateRSI(analysisData, 14);
                    
                    TechnicalIndicators.MACDResult macd = TechnicalIndicators.calculateMACD(
                        analysisData, 12, 26, 9);
                    
                    TechnicalIndicators.BollingerBands bb = TechnicalIndicators.calculateBollingerBands(
                        analysisData, 20, 2.0);

                    // Generate trading signal
                    System.out.println("  Generating trading signals...");
                    SignalGenerator.SignalResult signal = SignalGenerator.generateSignal(
                        stock, analysisData, sma20, sma50, ema12, rsi, macd, bb);
                    allSignals.add(signal);
                    
                    System.out.println("  ✓ " + signal.signal + " (Confidence: " + 
                                     String.format("%.1f%%", signal.confidence) + ")");

                    // Generate charts
                    System.out.println("  Generating charts...");
                    ChartGenerator.generateChart(stock, analysisData, sma20, sma50, CHARTS_DIR, "price");
                    ChartGenerator.generateChart(stock, analysisData, rsi, new double[]{30, 70}, CHARTS_DIR, "technical");

                } catch (Exception e) {
                    System.err.println("  ✗ Error processing " + stock + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }

            // Generate HTML report
            if (!allSignals.isEmpty()) {
                System.out.println("\n" + repeat("═", 50));
                System.out.println("Generating HTML report...");
                HtmlReportGenerator.generateReport(allSignals, allData, OUTPUT_DIR + "/report.html");
                System.out.println("Report saved to: " + OUTPUT_DIR + "/report.html");
            } else {
                System.out.println("\nNo signals generated. Creating fallback report...");
                // Create fallback HTML if no data
                String fallbackHtml = generateFallbackReport();
                try (FileWriter writer = new FileWriter(OUTPUT_DIR + "/report.html")) {
                    writer.write(fallbackHtml);
                }
                System.out.println("Fallback report saved to: " + OUTPUT_DIR + "/report.html");
            }

            // Print signal summary
            if (!allSignals.isEmpty()) {
                System.out.println("\n" + repeat("═", 50));
                printSignalSummary(allSignals);
            }

            System.out.println("\n╔═══════════════════════════════════════════════════╗");
            System.out.println("║    Analysis Complete - Check output/ directory    ║");
            System.out.println("╚═══════════════════════════════════════════════════╝");

        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printSignalSummary(List<SignalGenerator.SignalResult> signals) {
        int strongBuy = 0, buy = 0, hold = 0, sell = 0, strongSell = 0;

        for (SignalGenerator.SignalResult signal : signals) {
            switch (signal.signal) {
                case "STRONG_BUY":
                    strongBuy++;
                    break;
                case "BUY":
                    buy++;
                    break;
                case "HOLD":
                    hold++;
                    break;
                case "SELL":
                    sell++;
                    break;
                case "STRONG_SELL":
                    strongSell++;
                    break;
            }
        }

        System.out.println("  🟢 STRONG BUY:   " + strongBuy);
        System.out.println("  🟢 BUY:          " + buy);
        System.out.println("  🟡 HOLD:         " + hold);
        System.out.println("  🔴 SELL:         " + sell);
        System.out.println("  🔴 STRONG SELL:  " + strongSell);
    }

    private static List<String> readStockList() throws IOException {
        List<String> stocks = new ArrayList<>();
        
        Path path = Paths.get(STOCK_LIST_FILE);
        if (Files.exists(path)) {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    stocks.add(line);
                }
            }
        } else {
            System.err.println("Warning: " + STOCK_LIST_FILE + " not found. Using default stocks.");
            stocks.addAll(Arrays.asList("THYAO", "SOKM", "FROTO", "SISE"));
        }
        
        return stocks;
    }

    private static String generateFallbackReport() {
        return "<!DOCTYPE html>" +
               "<html>" +
               "<head>" +
               "  <title>BIST Technical Analysis - Fallback Report</title>" +
               "  <style>" +
               "    body { font-family: Arial, sans-serif; background: #f5f5f5; margin: 20px; }" +
               "    .container { background: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }" +
               "    h1 { color: #333; }" +
               "    .info { background: #fff3cd; padding: 15px; border-radius: 4px; color: #856404; }" +
               "  </style>" +
               "</head>" +
               "<body>" +
               "  <div class=\"container\">" +
               "    <h1>BIST Technical Analysis Report</h1>" +
               "    <div class=\"info\">" +
               "      <p><strong>Note:</strong> The analysis could not retrieve sufficient data at this time.</p>" +
               "      <p>Please check your internet connection and try again later.</p>" +
               "    </div>" +
               "  </div>" +
               "</body>" +
               "</html>";
    }

    private static String repeat(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
}
