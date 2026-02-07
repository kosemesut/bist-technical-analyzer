package com.bist.analyzer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class BistAnalyzerApp {
    
    private static final String STOCK_LIST_FILE = "stock_list.txt";
    private static final String OUTPUT_DIR = "output";
    private static final String CHARTS_DIR = "output/charts";
    
    private static Map<String, String> failedStocks = new LinkedHashMap<>();

    // BIST 100 Hisseleri
    private static final String[] BIST100_STOCKS = {
        "THYAO", "ASELS", "GUBRF", "SAIS", "SODA", "MGMT", "KRDMD", "VAKBN", "EREGL", "SAHOL",
        "TOASO", "KOZAL", "BIMAS", "ULKER", "ENEOS", "TATGD", "CCOLA", "KCHIA", "CIMSA", "TKFEN",
        "SESA", "AEFGH", "ORCAY", "ADEL", "DEVA", "PSTVRK", "ISLTK", "KBNDY", "OZROU", "CNTA",
        "GLYHO", "HALKS", "SIGER", "ARCLK", "CEMTS", "PETKM", "TTKOM", "AFYON", "CRKP", "LOGO",
        "ALARK", "NTHOL", "CMAT", "EGISB", "ENJSA", "FENER", "GARO", "HALKB", "KCHOL", "KAVAK",
        "KLMNT", "KORDS", "KUMPB", "LAPCO", "LISI", "LYKOH", "MERKO", "METUR", "MPARK", "MAVI",
        "NMSIL", "NUHCM", "OKCGY", "OYAKC", "PARSB", "PETBT", "PLTUR", "QUAGR", "ROLO", "SASES",
        "SATIM", "SCOPB", "SECO", "SEDEF", "SEMES", "SENCE", "SENTI", "SEREP", "SINEF", "SOKM",
        "SOSIN", "SOYAB", "SUNEC", "TACAK", "TAKAS", "TALDO", "TDGFT", "TEKTU", "TENTT", "TGSRT",
        "TKNSA", "TRLHF", "TURSG", "UNLU", "VESTS", "VESTL", "YKBNF", "YKSRT", "YUM", "ZARVY"
    };

    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════════════════════╗");
        System.out.println("║    BİST Teknik Analiz Sistemi Başlatıldı          ║");
        System.out.println("╚═══════════════════════════════════════════════════╝\n");

        try {
            // Create output directories
            Files.createDirectories(Paths.get(OUTPUT_DIR));
            Files.createDirectories(Paths.get(CHARTS_DIR));

            // Read stock list
            List<String> stocks = readStockList();
            if (stocks.isEmpty()) {
                System.err.println("Hisse bulunamadı: " + STOCK_LIST_FILE);
                return;
            }

            // Get BIST 100 stocks and filter out already analyzed ones
            Set<String> userStocks = new HashSet<>(stocks);
            Set<String> bist100Stocks = new HashSet<>(Arrays.asList(BIST100_STOCKS));
            bist100Stocks.removeAll(userStocks); // Remove user-provided stocks
            
            System.out.println("📋 Toplam Analiz Edilecek: " + stocks.size() + " hisse");
            System.out.println("BIST 100'den ek hisseler: " + (bist100Stocks.size() > 0 ? bist100Stocks.size() + " hisse" : "Yok"));
            System.out.println(repeat("═", 50) + "\n");

            // Data containers
            List<SignalGenerator.SignalResult> userSignals = new ArrayList<>();
            List<SignalGenerator.SignalResult> bist100Signals = new ArrayList<>();
            Map<String, List<StockData>> allData = new HashMap<>();

            // Process user-provided stocks
            processStocks(stocks, userSignals, allData, false);
            System.out.println("\n✅ Kullanıcı Hisseleri: " + userSignals.size() + " başarıyla analiz edildi");
            
            // Process BIST 100 stocks (only if not too many and if user enabled it)
            if (bist100Stocks.size() > 0 && bist100Stocks.size() <= 30) {
                System.out.println("\n" + repeat("═", 50));
                System.out.println("BIST 100'den ek hisselerin analizi başlanıyor...");
                System.out.println(repeat("═", 50) + "\n");
                processStocks(new ArrayList<>(bist100Stocks), bist100Signals, allData, true);
                System.out.println("\n✅ BIST 100 Hisseleri: " + bist100Signals.size() + " başarıyla analiz edildi");
            }

            // Summary
            int totalAnalyzed = userSignals.size() + bist100Signals.size();
            System.out.println("\n" + repeat("═", 50));
            System.out.println("📊 TOPLAM SONUÇ: " + totalAnalyzed + " hisse analiz edildi");

            // Generate HTML report
            if (!userSignals.isEmpty() || !bist100Signals.isEmpty()) {
                System.out.println("\n" + repeat("═", 50));
                System.out.println("HTML rapor oluşturuluyor...");
                HtmlReportGenerator.generateReport(userSignals, bist100Signals, allData, failedStocks, OUTPUT_DIR + "/report.html");
                System.out.println("Rapor kaydedildi: " + OUTPUT_DIR + "/report.html");
                if (!failedStocks.isEmpty()) {
                    System.out.println("\n⚠️  Veri alınamayan hisseler: " + failedStocks.size() + " adet (raporda detaylar var)");
                }
            } else {
                System.out.println("\nSinyal oluşturulamadı. Geri dönüş raporu oluşturuluyor...");
                // Create fallback HTML if no data
                String fallbackHtml = generateFallbackReport();
                try (FileWriter writer = new FileWriter(OUTPUT_DIR + "/report.html")) {
                    writer.write(fallbackHtml);
                }
                System.out.println("Geri dönüş raporu kaydedildi: " + OUTPUT_DIR + "/report.html");
            }

            // Print signal summary
            if (!userSignals.isEmpty() || !bist100Signals.isEmpty()) {
                System.out.println("\n" + repeat("═", 50));
                System.out.println("İSTENEN HİSSELER:");
                printSignalSummary(userSignals);
                if (!bist100Signals.isEmpty()) {
                    System.out.println("\nBİST 100'DEN GÜÇLÜ SİNYALLER:");
                    printSignalSummary(bist100Signals);
                }
            }

            System.out.println("\n╔═══════════════════════════════════════════════════╗");
            System.out.println("║    Analiz Tamamlandı - output/ klasörünü kontrol   ║");
            System.out.println("╚═══════════════════════════════════════════════════╝");

        } catch (Exception e) {
            System.err.println("Kritik hata: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void processStocks(List<String> stocks, 
                                     List<SignalGenerator.SignalResult> signals,
                                     Map<String, List<StockData>> allData,
                                     boolean isBist100Analysis) {
        for (String stock : stocks) {
            stock = stock.trim().toUpperCase();
            if (stock.isEmpty()) continue;

            System.out.println("\n📊 İşleniyor: " + stock);
            System.out.println(repeat("─", 50));

            try {
                // For BIST100 analysis, use daily data only to save time
                if (isBist100Analysis) {
                    System.out.println("  Günlük veriler çekiliyor (1d)...");
                    List<StockData> dailyData = StockDataFetcher.fetchData(stock, "1d", "5y");

                    if (dailyData.isEmpty()) {
                        String reason = "API'den günlük veri alınamadı (HTTP 404 veya veri yok)";
                        failedStocks.put(stock, reason);
                        System.err.println("  ✗ " + stock + " için veri alınamadı");
                        continue;
                    }
                    
                    allData.put(stock, dailyData);
                    analyzeAndSignal(stock, dailyData, signals);
                } else {
                    // For user stocks, use both hourly and daily data
                    System.out.println("  Saatlik veriler çekiliyor (1h)...");
                    List<StockData> hourlyData = StockDataFetcher.fetchData(stock, "1h", "3mo");
                    
                    System.out.println("  Günlük veriler çekiliyor (1d)...");
                    List<StockData> dailyData = StockDataFetcher.fetchData(stock, "1d", "5y");

                    if (hourlyData.isEmpty() && dailyData.isEmpty()) {
                        String reason = "API'den veri alınamadı (HTTP 404 veya veri yok)";
                        failedStocks.put(stock, reason);
                        System.err.println("  ✗ " + stock + " için veri alınamadı");
                        continue;
                    }

                    List<StockData> analysisData = !hourlyData.isEmpty() ? hourlyData : dailyData;
                    allData.put(stock, analysisData);
                    analyzeAndSignal(stock, analysisData, signals);
                    
                    // Generate charts only for user stocks
                    if (!signals.isEmpty()) {
                        System.out.println("  Grafikler oluşturuluyor...");
                        SignalGenerator.SignalResult lastSignal = signals.get(signals.size() - 1);
                        if (lastSignal.symbol.equals(stock)) {
                            List<StockData> data = allData.get(stock);
                            double[] sma20 = TechnicalIndicators.calculateSMA(data, 20);
                            double[] sma50 = TechnicalIndicators.calculateSMA(data, 50);
                            double[] ema12 = TechnicalIndicators.calculateEMA(data, 12);
                            double[] rsi = TechnicalIndicators.calculateRSI(data, 14);
                            
                            // Generate full data chart
                            ChartGenerator.generateTechnicalChart(stock, data, sma20, sma50, ema12, rsi, CHARTS_DIR + "/" + stock + "_chart.png", lastSignal);
                            
                            // Generate 1-month visual version for mobile (same calculations, last 30 days display)
                            ChartGenerator.generateTechnicalChart1Month(stock, data, sma20, sma50, ema12, rsi, CHARTS_DIR + "/" + stock + "_chart.png", lastSignal);
                        }
                    }
                }

            } catch (Exception e) {
                String reason = "İşleme hatası: " + e.getMessage();
                failedStocks.put(stock, reason);
                System.err.println("  ✗ " + stock + " işlenirken hata: " + e.getMessage());
            }
        }
    }

    private static void analyzeAndSignal(String stock, List<StockData> data,
                                        List<SignalGenerator.SignalResult> signals) {
        if (data.isEmpty()) return;
        
        System.out.println("  Teknik göstergeler hesaplanıyor...");
        double[] sma20 = TechnicalIndicators.calculateSMA(data, 20);
        double[] sma50 = TechnicalIndicators.calculateSMA(data, 50);
        double[] ema12 = TechnicalIndicators.calculateEMA(data, 12);
        double[] rsi = TechnicalIndicators.calculateRSI(data, 14);
        
        TechnicalIndicators.MACDResult macd = TechnicalIndicators.calculateMACD(data, 12, 26, 9);
        TechnicalIndicators.BollingerBands bb = TechnicalIndicators.calculateBollingerBands(data, 20, 2.0);

        System.out.println("  İşlem sinyalleri üretiliyor...");
        SignalGenerator.SignalResult signal = SignalGenerator.generateSignal(
            stock, data, sma20, sma50, ema12, rsi, macd, bb);
        signals.add(signal);
        
        System.out.println("  ✓ " + getSignalTextTR(signal.signal) + " (Güven: " + 
                         String.format("%.1f%%", signal.confidence) + ")");
    }

    private static String getSignalTextTR(String signal) {
        switch (signal) {
            case "STRONG_BUY":
                return "GÜÇLÜ AL";
            case "BUY":
                return "AL";
            case "HOLD":
                return "TUT";
            case "SELL":
                return "SAT";
            case "STRONG_SELL":
                return "GÜÇLÜ SAT";
            default:
                return signal;
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

        System.out.println("  🟢 GÜÇLÜ AL:    " + strongBuy);
        System.out.println("  🟢 AL:          " + buy);
        System.out.println("  🟡 TUTUTTUR:    " + hold);
        System.out.println("  🔴 SAT:         " + sell);
        System.out.println("  🔴 GÜÇLÜ SAT:   " + strongSell);
    }

    private static List<String> readStockList() throws IOException {
        List<String> stocks = new ArrayList<>();
        
        Path path = Paths.get(STOCK_LIST_FILE);
        if (Files.exists(path)) {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String line : lines) {
                line = line.trim();
                // Skip empty lines, comments, and the --- separator
                if (!line.isEmpty() && !line.startsWith("#") && !line.equals("---") && !line.equals("--")) {
                    // Extract symbol from format: "SYMBOL - Full Name" or just "SYMBOL"
                    String symbol = line;
                    if (line.contains(" - ")) {
                        symbol = line.split(" - ")[0].trim();
                    }
                    if (!symbol.isEmpty()) {
                        stocks.add(symbol.toUpperCase());
                    }
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
