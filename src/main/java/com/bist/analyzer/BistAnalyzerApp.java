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
            System.out.println("═".repeat(50) + "\n");

            // Data containers
            List<SignalGenerator.SignalResult> allSignals = new ArrayList<>();
            Map<String, List<StockData>> allData = new HashMap<>();

            // Process each stock
            for (String stock : stocks) {
                stock = stock.trim().toUpperCase();
                if (stock.isEmpty()) continue;

                System.out.println("\n📊 Processing: " + stock);
                System.out.println("─".repeat(50));

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
                    double[] ema26 = TechnicalIndicators.calculateEMA(analysisData, 26);
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
                    System.out.println("  ✓ Signal: " + signal.signal + " (Confidence: " + 
                        String.format("%.1f%%", signal.confidence) + ")");

                    // Generate charts
                    System.out.println("  Generating charts...");
                    try {
                        ChartGenerator.generateCandleChart(stock, analysisData, 
                            CHARTS_DIR + "/" + stock + "_price.png");
                        
                        ChartGenerator.generateTechnicalChart(stock, analysisData, 
                            sma20, sma50, ema12, rsi, 
                            CHARTS_DIR + "/" + stock + "_technical.png");
                    } catch (Exception e) {
                        System.err.println("  ⚠ Chart generation failed: " + e.getMessage());
                    }

                    System.out.println("  ✓ " + stock + " analysis completed");

                } catch (Exception e) {
                    System.err.println("  ✗ Error processing " + stock + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }

            // Generate HTML report
            System.out.println("\n" + "═".repeat(50));
            System.out.println("\n📄 Generating HTML report...");
            try {
                HtmlReportGenerator.generateReport(allSignals, allData, OUTPUT_DIR + "/index.html");
                System.out.println("✓ Report generated: " + OUTPUT_DIR + "/index.html");
            } catch (IOException e) {
                System.err.println("✗ Report generation failed: " + e.getMessage());
            }

            // Summary
            System.out.println("\n" + "═".repeat(50));
            System.out.println("\n📈 Analysis Summary:");
            printSignalSummary(allSignals);

            System.out.println("\n╔═══════════════════════════════════════════════════╗");
            System.out.println("║    Analysis Complete - Ready for GitHub Pages    ║");
            System.out.println("╚═══════════════════════════════════════════════════╝");

        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static List<String> readStockList() throws IOException {
        List<String> stocks = new ArrayList<>();
        Path path = Paths.get(STOCK_LIST_FILE);
        
        if (!Files.exists(path)) {
            System.err.println("Warning: " + STOCK_LIST_FILE + " not found. Creating default...");
            Files.write(path, "THYAO\nSOKM\nFROTO\nSISE\n".getBytes(StandardCharsets.UTF_8));
        }
        
        stocks.addAll(Files.readAllLines(path, StandardCharsets.UTF_8));
        return stocks;
    }

    private static void printSignalSummary(List<SignalGenerator.SignalResult> signals) {
        int strongBuy = 0, buy = 0, hold = 0, sell = 0, strongSell = 0;

        for (SignalGenerator.SignalResult signal : signals) {
            switch (signal.signal) {
                case "STRONG_BUY" -> strongBuy++;
                case "BUY" -> buy++;
                case "HOLD" -> hold++;
                case "SELL" -> sell++;
                case "STRONG_SELL" -> strongSell++;
            }
        }

        System.out.println("  🟢 STRONG BUY:   " + strongBuy);
        System.out.println("  🟢 BUY:          " + buy);
        System.out.println("  🟡 HOLD:         " + hold);
        System.out.println("  🔴 SELL:         " + sell);
        System.out.println("  🔴 STRONG SELL:  " + strongSell);
        System.out.println("  Total Analyzed:  " + signals.size());
    }
}
