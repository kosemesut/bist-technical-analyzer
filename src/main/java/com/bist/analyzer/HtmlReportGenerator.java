package com.bist.analyzer;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class HtmlReportGenerator {

    public static void generateReport(List<SignalGenerator.SignalResult> signals, 
                                      Map<String, List<StockData>> allData,
                                      String outputPath) throws IOException {
        
        StringBuilder html = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>BIST Technical Analysis Report</title>\n");
        html.append("    <style>\n");
        html.append(getCSS());
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        
        html.append("<div class=\"container\">\n");
        html.append("    <h1>BIST Technical Analysis Report</h1>\n");
        html.append("    <div class=\"timestamp\">Generated: ").append(sdf.format(new Date())).append("</div>\n");
        
        // Summary table
        html.append("    <section class=\"summary\">\n");
        html.append("        <h2>Trading Signals Summary</h2>\n");
        html.append("        <table class=\"signals-table\">\n");
        html.append("            <thead>\n");
        html.append("                <tr>\n");
        html.append("                    <th>Stock</th>\n");
        html.append("                    <th>Current Price</th>\n");
        html.append("                    <th>Signal</th>\n");
        html.append("                    <th>Confidence</th>\n");
        html.append("                    <th>Details</th>\n");
        html.append("                </tr>\n");
        html.append("            </thead>\n");
        html.append("            <tbody>\n");
        
        // Sort signals by buy/sell strength
        List<SignalGenerator.SignalResult> sortedSignals = new ArrayList<>(signals);
        sortedSignals.sort((a, b) -> {
            int aValue = getSignalValue(a.signal);
            int bValue = getSignalValue(b.signal);
            return Integer.compare(bValue, aValue);
        });
        
        for (SignalGenerator.SignalResult signal : sortedSignals) {
            String signalClass = signal.signal.toLowerCase().replace("_", "-");
            html.append("                <tr class=\"signal-").append(signalClass).append("\">\n");
            html.append("                    <td><strong>").append(signal.symbol).append("</strong></td>\n");
            html.append("                    <td>").append(String.format("%.2f", signal.price)).append(" TRY</td>\n");
            html.append("                    <td><span class=\"signal-badge signal-").append(signalClass).append("\">")
                .append(signal.signal.replace("_", " ")).append("</span></td>\n");
            html.append("                    <td><div class=\"confidence-bar\">\n");
            html.append("                        <div class=\"confidence-fill\" style=\"width: ").append(signal.confidence).append("%\"></div>\n");
            html.append("                        <span class=\"confidence-text\">").append(String.format("%.0f%%", signal.confidence)).append("</span>\n");
            html.append("                    </div></td>\n");
            html.append("                    <td>").append(signal.details).append("</td>\n");
            html.append("                </tr>\n");
        }
        
        html.append("            </tbody>\n");
        html.append("        </table>\n");
        html.append("    </section>\n");
        
        // Detailed analysis for each stock
        html.append("    <section class=\"detailed\">\n");
        html.append("        <h2>Detailed Analysis</h2>\n");
        
        for (SignalGenerator.SignalResult signal : sortedSignals) {
            List<StockData> data = allData.get(signal.symbol);
            if (data != null && !data.isEmpty()) {
                StockData latest = data.get(data.size() - 1);
                
                html.append("        <div class=\"stock-section\">\n");
                html.append("            <h3>").append(signal.symbol).append("</h3>\n");
                html.append("            <div class=\"stock-info\">\n");
                html.append("                <div class=\"info-item\">\n");
                html.append("                    <span class=\"label\">Price:</span>\n");
                html.append("                    <span class=\"value\">").append(String.format("%.2f", latest.getClose())).append(" TRY</span>\n");
                html.append("                </div>\n");
                html.append("                <div class=\"info-item\">\n");
                html.append("                    <span class=\"label\">High:</span>\n");
                html.append("                    <span class=\"value\">").append(String.format("%.2f", latest.getHigh())).append(" TRY</span>\n");
                html.append("                </div>\n");
                html.append("                <div class=\"info-item\">\n");
                html.append("                    <span class=\"label\">Low:</span>\n");
                html.append("                    <span class=\"value\">").append(String.format("%.2f", latest.getLow())).append(" TRY</span>\n");
                html.append("                </div>\n");
                html.append("                <div class=\"info-item\">\n");
                html.append("                    <span class=\"label\">Volume:</span>\n");
                html.append("                    <span class=\"value\">").append(String.format("%,d", latest.getVolume())).append("</span>\n");
                html.append("                </div>\n");
                html.append("            </div>\n");
                html.append("            <div class=\"charts\">\n");
                html.append("                <img src=\"charts/").append(signal.symbol).append("_price.png\" alt=\"Price Chart\" class=\"chart-img\">\n");
                html.append("                <img src=\"charts/").append(signal.symbol).append("_technical.png\" alt=\"Technical Chart\" class=\"chart-img\">\n");
                html.append("            </div>\n");
                html.append("        </div>\n");
            }
        }
        
        html.append("    </section>\n");
        
        // Legend
        html.append("    <section class=\"legend\">\n");
        html.append("        <h2>Signal Legend</h2>\n");
        html.append("        <div class=\"legend-items\">\n");
        html.append("            <div class=\"legend-item\"><span class=\"badge badge-strong-buy\">STRONG BUY</span> - Very strong buying signals</div>\n");
        html.append("            <div class=\"legend-item\"><span class=\"badge badge-buy\">BUY</span> - Various buying signals detected</div>\n");
        html.append("            <div class=\"legend-item\"><span class=\"badge badge-hold\">HOLD</span> - Mixed or no clear signal</div>\n");
        html.append("            <div class=\"legend-item\"><span class=\"badge badge-sell\">SELL</span> - Various selling signals detected</div>\n");
        html.append("            <div class=\"legend-item\"><span class=\"badge badge-strong-sell\">STRONG SELL</span> - Very strong selling signals</div>\n");
        html.append("        </div>\n");
        html.append("    </section>\n");
        
        html.append("    <footer>\n");
        html.append("        <p><strong>Disclaimer:</strong> This analysis is for informational purposes only. Always conduct your own research before making investment decisions.</p>\n");
        html.append("        <p>BIST Technical Analysis System | Data from Yahoo Finance</p>\n");
        html.append("    </footer>\n");
        html.append("</div>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        
        // Write to file
        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write(html.toString());
        }
        
        System.out.println("HTML report generated: " + outputPath);
    }

    private static String getCSS() {
        return
            "* {" +
            "margin: 0;" +
            "padding: 0;" +
            "box-sizing: border-box;" +
            "}" +
            "body {" +
            "font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;" +
            "background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);" +
            "color: #333;" +
            "line-height: 1.6;" +
            "padding: 20px;" +
            "}" +
            ".container {" +
            "max-width: 1400px;" +
            "margin: 0 auto;" +
            "background: white;" +
            "border-radius: 10px;" +
            "box-shadow: 0 10px 30px rgba(0,0,0,0.3);" +
            "padding: 30px;" +
            "}" +
            "h1 {" +
            "color: #667eea;" +
            "margin-bottom: 10px;" +
            "font-size: 2.5em;" +
            "border-bottom: 3px solid #667eea;" +
            "padding-bottom: 15px;" +
            "}" +
            "h2 {" +
            "color: #764ba2;" +
            "margin-top: 30px;" +
            "margin-bottom: 15px;" +
            "font-size: 1.8em;" +
            "}" +
            "h3 {" +
            "color: #667eea;" +
            "margin-top: 20px;" +
            "margin-bottom: 10px;" +
            "}" +
            ".timestamp {" +
            "color: #666;" +
            "font-size: 0.9em;" +
            "margin-bottom: 20px;" +
            "}" +
            ".summary {" +
            "margin-bottom: 40px;" +
            "}" +
            ".signals-table {" +
            "width: 100%;" +
            "border-collapse: collapse;";
                margin-top: 15px;
                box-shadow: 0 2px 5px rgba(0,0,0,0.1);
            }
            
            .signals-table th {
                background: #667eea;
                color: white;
                padding: 12px;
                text-align: left;
                font-weight: 600;
            }
            
            .signals-table td {
                padding: 12px;
                border-bottom: 1px solid #eee;
            }
            
            .signals-table tbody tr:hover {
                background: #f9f9f9;
            }
            
            .signal-badge {
                display: inline-block;
                padding: 6px 12px;
                border-radius: 20px;
                font-weight: 600;
                font-size: 0.85em;
                text-transform: uppercase;
            }
            
            .signal-strong-buy {
                background: #00c851;
                color: white;
            }
            
            .signal-buy {
                background: #7cb342;
                color: white;
            }
            
            .signal-hold {
                background: #ffb300;
                color: white;
            }
            
            .signal-sell {
                background: #ff6b6b;
                color: white;
            }
            
            .signal-strong-sell {
                background: #d50000;
                color: white;
            }
            
            .confidence-bar {
                width: 100%;
                height: 30px;
                background: #eee;
                border-radius: 15px;
                position: relative;
                overflow: hidden;
            }
            
            .confidence-fill {
                height: 100%;
                background: linear-gradient(90deg, #667eea, #764ba2);
                transition: width 0.3s ease;
            }
            
            .confidence-text {
                position: absolute;
                top: 50%;
                left: 50%;
                transform: translate(-50%, -50%);
                font-weight: 600;
                color: #333;
                font-size: 0.9em;
            }
            
            .detailed {
                margin-top: 40px;
            }
            
            .stock-section {
                background: #f9f9f9;
                padding: 20px;
                margin-bottom: 30px;
                border-radius: 8px;
                border-left: 4px solid #667eea;
            }
            
            .stock-info {
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
                gap: 15px;
                margin-bottom: 20px;
            }
            
            .info-item {
                background: white;
                padding: 10px;
                border-radius: 5px;
                border: 1px solid #ddd;
            }
            
            .info-item .label {
                display: block;
                font-size: 0.85em;
                color: #666;
                margin-bottom: 5px;
            }
            
            .info-item .value {
                display: block;
                font-size: 1.2em;
                font-weight: 600;
                color: #667eea;
            }
            
            .charts {
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
                gap: 20px;
                margin-top: 20px;
            }
            
            .chart-img {
                max-width: 100%;
                height: auto;
                border-radius: 5px;
                box-shadow: 0 2px 5px rgba(0,0,0,0.1);
            }
            
            .legend {
                background: #f0f4ff;
                padding: 20px;
                border-radius: 8px;
                margin-top: 40px;
            }
            
            .legend-items {
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
                gap: 15px;
                margin-top: 15px;
            }
            
            .legend-item {
                display: flex;
                align-items: center;
                gap: 10px;
            }
            
            .badge {
                display: inline-block;
                padding: 5px 10px;
                border-radius: 15px;
                font-weight: 600;
                font-size: 0.8em;
                color: white;
                white-space: nowrap;
            }
            
            .badge-strong-buy { background: #00c851; }
            .badge-buy { background: #7cb342; }
            .badge-hold { background: #ffb300; }
            .badge-sell { background: #ff6b6b; }
            .badge-strong-sell { background: #d50000; }
            
            footer {
                margin-top: 40px;
                padding-top: 20px;
                border-top: 2px solid #eee;
                text-align: center;
                color: #666;
                font-size: 0.9em;
            }
            
            @media (max-width: 768px) {
                .container {
                    padding: 15px;
                }
                
                h1 {
                    font-size: 1.8em;
                }
                
                .charts {
                    grid-template-columns: 1fr;
                }
                
                .signals-table {
                    font-size: 0.9em;
                }
                
                .signals-table td, .signals-table th {
                    padding: 8px;
                }
            }
        """;
    }

    private static int getSignalValue(String signal) {
        return switch (signal) {
            case "STRONG_BUY" -> 5;
            case "BUY" -> 4;
            case "HOLD" -> 3;
            case "SELL" -> 2;
            case "STRONG_SELL" -> 1;
            default -> 0;
        };
    }
}
