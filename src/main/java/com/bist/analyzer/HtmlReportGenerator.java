package com.bist.analyzer;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class HtmlReportGenerator {

    public static void generateReport(List<SignalGenerator.SignalResult> userSignals,
                                      List<SignalGenerator.SignalResult> bist100Signals,
                                      Map<String, List<StockData>> allData,
                                      String outputPath) throws IOException {
        
        StringBuilder html = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"tr\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>BİST Teknik Analiz Raporu</title>\n");
        html.append("    <style>\n");
        html.append(getCSS());
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        
        // Add modal for image zoom
        html.append("<div id=\"imageModal\" class=\"modal\">\n");
        html.append("    <span class=\"modal-close\">&times;</span>\n");
        html.append("    <img class=\"modal-content\" id=\"modalImage\">\n");
        html.append("</div>\n");
        html.append("<script>\n");
        html.append(getJavaScript());
        html.append("</script>\n");
        
        html.append("<div class=\"container\" id=\"top\">\n");
        html.append("    <h1>BİST Teknik Analiz Raporu</h1>\n");
        html.append("    <div class=\"timestamp\">Oluşturulma Tarihi: ").append(sdf.format(new Date())).append("</div>\n");
        
        // User Stocks Summary table
        if (!userSignals.isEmpty()) {
            html.append("    <section class=\"summary\">\n");
            html.append("        <h2>📋 İstenen Hisselerin Sinyalleri</h2>\n");
            generateSignalTable(html, userSignals, allData);
            html.append("    </section>\n");
        }
        
        // BIST 100 Strong Signals Summary table
        if (!bist100Signals.isEmpty()) {
            List<SignalGenerator.SignalResult> strongBist100Signals = new ArrayList<>();
            for (SignalGenerator.SignalResult signal : bist100Signals) {
                if (signal.signal.equals("STRONG_BUY") || signal.signal.equals("STRONG_SELL")) {
                    strongBist100Signals.add(signal);
                }
            }
            
            if (!strongBist100Signals.isEmpty()) {
                html.append("    <section class=\"summary\">\n");
                html.append("        <h2>🎯 BIST 100'deki Güçlü Sinyaller</h2>\n");
                generateSignalTable(html, strongBist100Signals, allData);
                html.append("    </section>\n");
            }
        }
        
        // Detailed analysis for user stocks
        if (!userSignals.isEmpty()) {
            html.append("    <section class=\"detailed\">\n");
            html.append("        <h2>📊 Detaylı Analiz</h2>\n");
            
            List<SignalGenerator.SignalResult> sortedSignals = new ArrayList<>(userSignals);
            sortedSignals.sort((a, b) -> {
                int aValue = getSignalValue(a.signal);
                int bValue = getSignalValue(b.signal);
                return Integer.compare(bValue, aValue);
            });
            
            for (SignalGenerator.SignalResult signal : sortedSignals) {
                generateStockDetail(html, signal, allData);
            }
            
            html.append("    </section>\n");
        }
        
        // Legend
        html.append("    <section class=\"legend\">\n");
        html.append("        <h2>Sinyal Açıklaması</h2>\n");
        html.append("        <div class=\"legend-items\">\n");
        html.append("            <div class=\"legend-item\"><span class=\"badge badge-strong-buy\">GÜÇLÜ AL</span> - Çok güçlü satın alma sinyalleri</div>\n");
        html.append("            <div class=\"legend-item\"><span class=\"badge badge-buy\">AL</span> - Satın alma sinyalleri tespit edildi</div>\n");
        html.append("            <div class=\"legend-item\"><span class=\"badge badge-hold\">TUT</span> - Karışık veya net sinyal yok</div>\n");
        html.append("            <div class=\"legend-item\"><span class=\"badge badge-sell\">SAT</span> - Satış sinyalleri tespit edildi</div>\n");
        html.append("            <div class=\"legend-item\"><span class=\"badge badge-strong-sell\">GÜÇLÜ SAT</span> - Çok güçlü satış sinyalleri</div>\n");
        html.append("        </div>\n");
        html.append("    </section>\n");
        
        html.append("    <footer>\n");
        html.append("        <p><strong>Uyarı:</strong> Bu analiz sadece bilgilendirme amaçlıdır. Yatırım kararlarından önce daima kendi araştırmanızı yapınız.</p>\n");
        html.append("        <p>BİST Teknik Analiz Sistemi | Veriler Yahoo Finance'tan alınmıştır</p>\n");
        html.append("    </footer>\n");
        html.append("</div>\n");
        html.append("<a href=\"#top\" class=\"back-to-top\" title=\"En Üste Dön\">⬆</a>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        
        // Write to file with UTF-8 encoding
        byte[] bytes = html.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.nio.file.Files.write(java.nio.file.Paths.get(outputPath), bytes);
        
        System.out.println("HTML rapor oluşturuldu: " + outputPath);
    }

    private static void generateSignalTable(StringBuilder html, List<SignalGenerator.SignalResult> signals,
                                            Map<String, List<StockData>> allData) {
        html.append("        <table class=\"signals-table\">\n");
        html.append("            <thead>\n");
        html.append("                <tr>\n");
        html.append("                    <th>Hisse</th>\n");
        html.append("                    <th>Güncel Fiyat</th>\n");
        html.append("                    <th>Sinyal</th>\n");
        html.append("                    <th>Güven</th>\n");
        html.append("                    <th>Fiyat Değişimleri</th>\n");
        html.append("                </tr>\n");
        html.append("            </thead>\n");
        html.append("            <tbody>\n");
        
        // Read priority stocks from stock_list.txt
        List<String> priorityStocks = readPriorityStocks();
        
        // Custom sorting
        List<SignalGenerator.SignalResult> sortedSignals = new ArrayList<>(signals);
        sortedSignals.sort((a, b) -> {
            // 0. Priority stocks always first (in order they appear in stock_list.txt)
            boolean aPriority = priorityStocks.contains(a.symbol);
            boolean bPriority = priorityStocks.contains(b.symbol);
            if (aPriority && !bPriority) return -1;
            if (!aPriority && bPriority) return 1;
            if (aPriority && bPriority) {
                return Integer.compare(priorityStocks.indexOf(a.symbol), priorityStocks.indexOf(b.symbol));
            }
            
            // 1. Signal priority (STRONG_BUY first)
            int aValue = getSignalValue(a.signal);
            int bValue = getSignalValue(b.signal);
            if (aValue != bValue) {
                return Integer.compare(bValue, aValue);
            }
            
            // 2. For STRONG_BUY with confidence > 75%, sort by 1-month change (lowest first)
            if (a.signal.equals("STRONG_BUY") && a.confidence > 75 && b.signal.equals("STRONG_BUY") && b.confidence > 75) {
                double aChange = get1MonthChange(a.symbol, a.price, allData);
                double bChange = get1MonthChange(b.symbol, b.price, allData);
                return Double.compare(aChange, bChange); // Ascending (lowest gain first)
            }
            
            // 3. Within same signal, sort by confidence (highest first)
            if (Math.abs(a.confidence - b.confidence) > 0.1) {
                return Double.compare(b.confidence, a.confidence);
            }
            
            return 0;
        });
        
        for (SignalGenerator.SignalResult signal : sortedSignals) {
            String signalClass = signal.signal.toLowerCase().replace("_", "-");
            String signalText = getSignalTextTR(signal.signal);
            html.append("                <tr class=\"signal-").append(signalClass).append("\">\n");
            html.append("                    <td><strong><a href=\"#detail-").append(signal.symbol)
                .append("\" class=\"stock-link\">").append(signal.symbol).append("</a></strong></td>\n");
            html.append("                    <td>").append(String.format("%.2f ₺", signal.price)).append("</td>\n");
            html.append("                    <td><span class=\"signal-badge signal-").append(signalClass).append("\">")
                .append(signalText).append("</span></td>\n");
            html.append("                    <td>").append(String.format("%.0f%%", signal.confidence)).append("</td>\n");
            
            // Price changes column
            html.append("                    <td><div class=\"price-changes\">\n");
            appendCompactPriceChanges(html, signal.symbol, signal.price, allData);
            html.append("                    </div></td>\n");
            
            html.append("                </tr>\n");
        }
        
        html.append("            </tbody>\n");
        html.append("        </table>\n");
    }

    private static void generateStockDetail(StringBuilder html, SignalGenerator.SignalResult signal,
                                           Map<String, List<StockData>> allData) {
        List<StockData> data = allData.get(signal.symbol);
        if (data == null || data.isEmpty()) return;
        
        StockData latest = data.get(data.size() - 1);
        
        StockData latestWithVolume = latest;
        for (int i = data.size() - 1; i >= Math.max(0, data.size() - 50); i--) {
            if (data.get(i).getVolume() > 0) {
                latestWithVolume = data.get(i);
                break;
            }
        }
        
        html.append("        <div class=\"stock-section\" id=\"detail-").append(signal.symbol).append("\">\n");
        html.append("            <h3>").append(signal.symbol).append("</h3>\n");
        html.append("            <div class=\"stock-info\">\n");
        html.append("                <div class=\"info-item\">\n");
        html.append("                    <span class=\"label\">Fiyat:</span>\n");
        html.append("                    <span class=\"value\">").append(String.format("%.2f", latest.getClose())).append(" ₺</span>\n");
        html.append("                </div>\n");
        html.append("                <div class=\"info-item\">\n");
        html.append("                    <span class=\"label\">Yüksek:</span>\n");
        html.append("                    <span class=\"value\">").append(String.format("%.2f", latest.getHigh())).append(" ₺</span>\n");
        html.append("                </div>\n");
        html.append("                <div class=\"info-item\">\n");
        html.append("                    <span class=\"label\">Düşük:</span>\n");
        html.append("                    <span class=\"value\">").append(String.format("%.2f", latest.getLow())).append(" ₺</span>\n");
        html.append("                </div>\n");
        html.append("                <div class=\"info-item\">\n");
        html.append("                    <span class=\"label\">Hacim:</span>\n");
        html.append("                    <span class=\"value\">").append(String.format("%,d", latestWithVolume.getVolume())).append("</span>\n");
        html.append("                </div>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"charts\">\n");
        html.append("                <img src=\"charts/").append(signal.symbol).append("_chart.png\" alt=\"Teknik Analiz Grafiği\" class=\"chart-img\">\n");
        html.append("            </div>\n");
        html.append("            <div class=\"analysis\">\n");
        html.append("                <h4>Teknik Analiz Detayları</h4>\n");
        html.append("                <div class=\"analysis-details\">\n");
        html.append(signal.details);
        html.append("                </div>\n");
        html.append("            </div>\n");
        html.append("        </div>\n");
    }

    private static String getCSS() {
        return "* {margin: 0;padding: 0;box-sizing: border-box;} " +
            "body {font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);color: #333;line-height: 1.6;padding: 20px;} " +
            ".container {max-width: 1400px;margin: 0 auto;background: white;border-radius: 10px;box-shadow: 0 10px 30px rgba(0,0,0,0.3);padding: 30px;} " +
            "h1 {color: #667eea;margin-bottom: 10px;font-size: 2.5em;border-bottom: 3px solid #667eea;padding-bottom: 15px;} " +
            "h2 {color: #764ba2;margin-top: 30px;margin-bottom: 15px;font-size: 1.8em;} " +
            "h3 {color: #667eea;margin-top: 20px;margin-bottom: 10px;} " +
            ".timestamp {color: #666;font-size: 0.9em;margin-bottom: 20px;} " +
            ".summary {margin-bottom: 40px;} " +
            ".signals-table {width: 100%;border-collapse: collapse;margin-top: 15px;box-shadow: 0 2px 5px rgba(0,0,0,0.1);} " +
            ".signals-table th {background: #667eea;color: white;padding: 12px;text-align: left;font-weight: 600;} " +
            ".signals-table td {padding: 12px;border-bottom: 1px solid #eee;} " +
            ".signals-table tbody tr:hover {background: #f0f0f0;} .signals-table tbody tr:hover td {color: #333;} " +
            ".signals-table tbody tr.signal-strong-buy {background: #e8f5e9;} " +
            ".signals-table tbody tr.signal-strong-buy td {color: #2e7d32;} " +
            ".signals-table tbody tr.signal-buy {background: #f1f8e9;} " +
            ".signals-table tbody tr.signal-buy td {color: #33691e;} " +
            ".signals-table tbody tr.signal-strong-sell {background: #ffebee;} " +
            ".signals-table tbody tr.signal-strong-sell td {color: #b71c1c;} " +
            ".signals-table tbody tr.signal-sell {background: #fff3e0;} " +
            ".signals-table tbody tr.signal-sell td {color: #b71c1c;} " +
            ".signal-badge {display: inline-block;padding: 6px 12px;border-radius: 20px;font-weight: 600;font-size: 0.85em;text-transform: uppercase;} " +
            ".signal-strong-buy {background: #00c851;color: white;} " +
            ".signal-buy {background: #7cb342;color: white;} " +
            ".signal-hold {background: #ffb300;color: white;} " +
            ".signal-sell {background: #ff6b6b;color: white;} " +
            ".signal-strong-sell {background: #d50000;color: white;} " +
            ".confidence-bar {width: 100%;height: 30px;background: #eee;border-radius: 15px;position: relative;overflow: hidden;} " +
            ".confidence-fill {height: 100%;background: linear-gradient(90deg, #667eea, #764ba2);transition: width 0.3s ease;} " +
            ".confidence-text {position: absolute;top: 50%;left: 50%;transform: translate(-50%, -50%);font-weight: 600;color: #333;font-size: 0.9em;} " +
            ".detailed {margin-top: 40px;} " +
            ".stock-section {background: #f9f9f9;padding: 20px;margin-bottom: 30px;border-radius: 8px;border-left: 4px solid #667eea;} " +
            ".stock-info {display: grid;grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));gap: 15px;margin-bottom: 20px;} " +
            ".info-item {background: white;padding: 10px;border-radius: 5px;border: 1px solid #ddd;transition: all 0.3s ease;} .info-item:hover {box-shadow: 0 2px 8px rgba(0,0,0,0.1);border-color: #667eea;} " +
            ".info-item .label {display: block;font-size: 0.85em;color: #666;margin-bottom: 5px;} " +
            ".info-item .value {display: block;font-size: 1.2em;font-weight: 600;color: #667eea;} " +
            ".charts {margin-top: 20px;text-align: center;} " +
            ".chart-img {width: 100%;max-width: 1600px;border-radius: 8px;box-shadow: 0 4px 12px rgba(0,0,0,0.15);} " +
            ".analysis {background: white;padding: 20px;border-radius: 8px;margin-top: 20px;border-left: 4px solid #764ba2;} " +
            ".analysis h4 {color: #764ba2;margin-bottom: 15px;font-size: 1.2em;} " +
            ".analysis-details {display: flex;flex-direction: column;gap: 10px;line-height: 1.8;} " +
            ".analysis-details strong {color: #667eea;display: block;margin-top: 5px;} " +
            ".badge {display: inline-block;padding: 5px 10px;border-radius: 15px;font-weight: 600;font-size: 0.8em;color: white;white-space: nowrap;} " +
            ".badge-strong-buy {background: #00c851;} " +
            ".badge-buy {background: #7cb342;} " +
            ".badge-hold {background: #ffb300;} " +
            ".badge-sell {background: #ff6b6b;} " +
            ".badge-strong-sell {background: #d50000;} " +
            ".legend {background: #f0f4ff;padding: 20px;border-radius: 8px;margin-top: 40px;} " +
            ".legend-items {display: grid;grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));gap: 15px;margin-top: 15px;} " +
            ".legend-item {display: flex;align-items: center;gap: 10px;} " +
            ".positive {color: #1b5e20;font-weight: 700;} " +
            ".negative {color: #b71c1c;font-weight: 700;} " +
            ".stock-link {color: #667eea;text-decoration: none;font-weight: 700;transition: color 0.2s;} " +
            ".stock-link:hover {color: #764ba2;text-decoration: underline;} " +
            ".price-changes {font-size: 0.85em;line-height: 1.6;} " +
            ".price-change-item {display: inline-block;margin-right: 8px;white-space: nowrap;} " +
            ".price-change-label {color: #666;font-size: 0.9em;} " +
            ".back-to-top {position: fixed;bottom: 30px;right: 30px;background: linear-gradient(135deg, #667eea, #764ba2);color: white;width: 50px;height: 50px;border-radius: 50%;display: flex;align-items: center;justify-content: center;text-decoration: none;font-size: 24px;box-shadow: 0 4px 12px rgba(0,0,0,0.3);transition: all 0.3s ease;z-index: 1000;} " +
            ".back-to-top:hover {transform: translateY(-5px);box-shadow: 0 6px 16px rgba(0,0,0,0.4);} " +
            "footer {margin-top: 40px;padding-top: 20px;border-top: 2px solid #eee;text-align: center;color: #666;font-size: 0.9em;} " +
            ".modal {display: none;position: fixed;z-index: 2000;left: 0;top: 0;width: 100%;height: 100%;background-color: rgba(0,0,0,0.9);} " +
            ".modal-content {margin: auto;display: block;max-width: 90%;max-height: 90%;margin-top: 50px;} " +
            ".modal-close {position: absolute;top: 30px;right: 45px;color: #f1f1f1;font-size: 50px;font-weight: bold;cursor: pointer;transition: 0.3s;} " +
            ".modal-close:hover {color: #bbb;} " +
            ".chart-img {cursor: zoom-in;} " +
            "@media (max-width: 768px) {.container {padding: 15px;} h1 {font-size: 1.8em;} .charts {grid-template-columns: 1fr;} .signals-table {font-size: 0.9em;} .signals-table td, .signals-table th {padding: 8px;} .back-to-top {bottom: 20px;right: 20px;width: 45px;height: 45px;font-size: 20px;}} ";
    }

    private static String getJavaScript() {
        return "document.addEventListener('DOMContentLoaded', function() {" +
            "    var modal = document.getElementById('imageModal');" +
            "    var modalImg = document.getElementById('modalImage');" +
            "    var closeBtn = document.getElementsByClassName('modal-close')[0];" +
            "    var imgs = document.getElementsByClassName('chart-img');" +
            "    for (var i = 0; i < imgs.length; i++) {" +
            "        imgs[i].onclick = function() {" +
            "            modal.style.display = 'block';" +
            "            modalImg.src = this.src;" +
            "        };" +
            "    }" +
            "    closeBtn.onclick = function() {" +
            "        modal.style.display = 'none';" +
            "    };" +
            "    modal.onclick = function(e) {" +
            "        if (e.target === modal) {" +
            "            modal.style.display = 'none';" +
            "        }" +
            "    };" +
            "});";
    }

    private static int getSignalValue(String signal) {
        switch (signal) {
            case "STRONG_BUY":
                return 5;
            case "BUY":
                return 4;
            case "HOLD":
                return 3;
            case "SELL":
                return 2;
            case "STRONG_SELL":
                return 1;
            default:
                return 0;
        }
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

    /**
     * Generate price change table showing % change over various time periods
     */
    private static void generatePriceChangeTable(StringBuilder html, List<SignalGenerator.SignalResult> signals,
                                                 Map<String, List<StockData>> allData) {
        html.append("    <section class=\"summary\">\n");
        html.append("        <h2>📈 Fiyat Değişim Tablosu</h2>\n");
        html.append("        <table class=\"signals-table\">\n");
        html.append("            <thead>\n");
        html.append("                <tr>\n");
        html.append("                    <th>Hisse</th>\n");
        html.append("                    <th>Güncel Fiyat</th>\n");
        html.append("                    <th>1 Günlük</th>\n");
        html.append("                    <th>1 Haftalık</th>\n");
        html.append("                    <th>1 Aylık</th>\n");
        html.append("                    <th>3 Aylık</th>\n");
        html.append("                    <th>1 Yıllık</th>\n");
        html.append("                </tr>\n");
        html.append("            </thead>\n");
        html.append("            <tbody>\n");

        for (SignalGenerator.SignalResult signal : signals) {
            List<StockData> data = allData.get(signal.symbol);
            if (data == null || data.isEmpty()) continue;

            double currentPrice = signal.price;
            int lastIdx = data.size() - 1;

            html.append("                <tr>\n");
            html.append("                    <td><strong>").append(signal.symbol).append("</strong></td>\n");
            html.append("                    <td>").append(String.format("%.2f ₺", currentPrice)).append("</td>\n");

            // 1 day change
            appendPriceChange(html, data, lastIdx, 1, currentPrice);

            // 1 week (5 trading days)
            appendPriceChange(html, data, lastIdx, 5, currentPrice);

            // 1 month (20 trading days)
            appendPriceChange(html, data, lastIdx, 20, currentPrice);

            // 3 months (60 trading days)
            appendPriceChange(html, data, lastIdx, 60, currentPrice);

            // 1 year (252 trading days)
            appendPriceChange(html, data, lastIdx, 252, currentPrice);

            html.append("                </tr>\n");
        }

        html.append("            </tbody>\n");
        html.append("        </table>\n");
        html.append("    </section>\n");
    }

    private static void appendPriceChange(StringBuilder html, List<StockData> data, int lastIdx, 
                                          int daysBack, double currentPrice) {
        int targetIdx = lastIdx - daysBack;
        if (targetIdx < 0 || targetIdx >= data.size()) {
            html.append("                    <td>-</td>\n");
            return;
        }

        double oldPrice = data.get(targetIdx).getClose();
        double changePercent = ((currentPrice - oldPrice) / oldPrice) * 100;

        String colorClass = changePercent >= 0 ? "positive" : "negative";
        String arrow = changePercent >= 0 ? "▲" : "▼";

        html.append("                    <td class=\"").append(colorClass).append("\">")
            .append(arrow).append(" ")
            .append(String.format("%.2f%%", Math.abs(changePercent)))
            .append("</td>\n");
    }

    /**
     * Append compact price changes for signal table
     */
    private static void appendCompactPriceChanges(StringBuilder html, String symbol, double currentPrice,
                                                  Map<String, List<StockData>> allData) {
        List<StockData> data = allData.get(symbol);
        if (data == null || data.isEmpty()) {
            html.append("<span class=\"price-change-item\">Veri yok</span>");
            return;
        }

        int lastIdx = data.size() - 1;
        
        // 1 day
        appendCompactChange(html, "1g", data, lastIdx, 1, currentPrice);
        html.append(" ");
        
        // 1 week
        appendCompactChange(html, "1h", data, lastIdx, 5, currentPrice);
        html.append(" ");
        
        // 1 month
        appendCompactChange(html, "1a", data, lastIdx, 20, currentPrice);
        html.append(" ");
        
        // 3 months
        appendCompactChange(html, "3a", data, lastIdx, 60, currentPrice);
        html.append(" ");
        
        // 1 year
        appendCompactChange(html, "1y", data, lastIdx, 252, currentPrice);
    }

    private static void appendCompactChange(StringBuilder html, String label, List<StockData> data,
                                           int lastIdx, int daysBack, double currentPrice) {
        int targetIdx = lastIdx - daysBack;
        if (targetIdx < 0 || targetIdx >= data.size()) {
            html.append("<span class=\"price-change-item\"><span class=\"price-change-label\">").append(label)
                .append(":</span> -</span>");
            return;
        }

        double oldPrice = data.get(targetIdx).getClose();
        double changePercent = ((currentPrice - oldPrice) / oldPrice) * 100;

        String colorClass = changePercent >= 0 ? "positive" : "negative";
        String arrow = changePercent >= 0 ? "▲" : "▼";

        html.append("<span class=\"price-change-item\"><span class=\"price-change-label\">").append(label)
            .append(":</span> <span class=\"").append(colorClass).append("\">")
            .append(arrow).append(String.format("%.1f%%", Math.abs(changePercent)))
            .append("</span></span>");
    }

    /**
     * Get 1-month price change percentage
     */
    private static double get1MonthChange(String symbol, double currentPrice, Map<String, List<StockData>> allData) {
        List<StockData> data = allData.get(symbol);
        if (data == null || data.isEmpty()) return 0.0;

        int lastIdx = data.size() - 1;
        int targetIdx = lastIdx - 20; // 20 trading days ~1 month
        
        if (targetIdx < 0 || targetIdx >= data.size()) return 0.0;

        double oldPrice = data.get(targetIdx).getClose();
        return ((currentPrice - oldPrice) / oldPrice) * 100;
    }

    /**
     * Read priority stocks from stock_list.txt (stocks before --- separator)
     */
    private static List<String> readPriorityStocks() {
        List<String> priorityStocks = new ArrayList<>();
        try {
            java.nio.file.Path path = java.nio.file.Paths.get("stock_list.txt");
            if (!java.nio.file.Files.exists(path)) {
                return priorityStocks;
            }
            
            List<String> lines = java.nio.file.Files.readAllLines(path, java.nio.charset.StandardCharsets.UTF_8);
            for (String line : lines) {
                line = line.trim();
                // Stop at separator
                if (line.startsWith("---") || line.startsWith("--")) {
                    break;
                }
                // Skip comments and empty lines
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                priorityStocks.add(line.toUpperCase());
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not read priority stocks: " + e.getMessage());
        }
        return priorityStocks;
    }
}
