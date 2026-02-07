package com.bist.analyzer;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.annotations.XYPointerAnnotation;
import org.jfree.chart.ui.TextAnchor;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class ChartGenerator {
    
    private static final int CHART_WIDTH = 1600;
    private static final int CHART_HEIGHT = 900;
    
    // Modern color palette (TradingView inspired)
    private static final Color BG_COLOR = new Color(250, 250, 250);
    private static final Color GRID_COLOR = new Color(230, 230, 230);
    private static final Color PRICE_COLOR = new Color(26, 26, 26); // Dark gray/black
    private static final Color SMA20_COLOR = new Color(66, 133, 244); // Blue
    private static final Color SMA50_COLOR = new Color(251, 188, 5); // Gold/Orange
    private static final Color EMA12_COLOR = new Color(52, 211, 153); // Teal/Green
    private static final Color RSI_COLOR = new Color(168, 85, 247); // Purple
    private static final Color VOLUME_COLOR = new Color(100, 150, 200, 80); // Semi-transparent blue
    private static final Color BUY_SIGNAL_COLOR = new Color(34, 197, 94); // Green
    private static final Color SELL_SIGNAL_COLOR = new Color(239, 68, 68); // Red

    /**
     * Generate professional-grade technical analysis chart with price + indicators + RSI
     */
    public static void generateCandleChart(String symbol, List<StockData> data, String outputPath) throws IOException {
        if (data.isEmpty()) {
            System.out.println("No data available for chart generation");
            return;
        }
        
        // Redirect to technical chart which includes price + indicators
        generateTechnicalChart(symbol, data, new double[data.size()], new double[data.size()], 
                             new double[data.size()], new double[data.size()], outputPath);
    }

    /**
     * Generate multi-panel technical chart:
     * Panel 1: Price with SMA20, SMA50, EMA12
     * Panel 2: RSI with overbought/oversold levels
     */
    public static void generateTechnicalChart(String symbol, List<StockData> data, 
                                            double[] sma20, double[] sma50, double[] ema12,
                                            double[] rsi, String outputPath) throws IOException {
        if (data.isEmpty()) {
            System.out.println("No data available for technical chart generation");
            return;
        }

        // Calculate all indicators if not provided
        double[] prices = new double[data.size()];
        for (int i = 0; i < data.size(); i++) {
            prices[i] = data.get(i).getClose();
        }
        
        if (sma20[0] == 0.0) sma20 = TechnicalIndicators.calculateSMA(data, 20);
        if (sma50[0] == 0.0) sma50 = TechnicalIndicators.calculateSMA(data, 50);
        if (ema12[0] == 0.0) ema12 = TechnicalIndicators.calculateEMA(data, 12);
        if (rsi[0] == 0.0) rsi = TechnicalIndicators.calculateRSI(data, 14);
        
        // Find historical BUY/SELL signals
        List<SignalGenerator.TradePoint> tradeSignals = SignalGenerator.findHistoricalSignals(data, sma20, sma50, ema12, rsi);

        // ===== PANEL 1: Price Chart with Moving Averages =====
        XYSeriesCollection priceDataset = new XYSeriesCollection();
        
        // Close Price
        XYSeries closeSeries = new XYSeries("Kapanış");
        for (int i = 0; i < data.size(); i++) {
            closeSeries.add(i, prices[i]);
        }
        priceDataset.addSeries(closeSeries);
        
        // SMA20
        XYSeries sma20Series = new XYSeries("SMA20");
        for (int i = 0; i < sma20.length; i++) {
            if (!Double.isNaN(sma20[i])) {
                sma20Series.add(i, sma20[i]);
            }
        }
        priceDataset.addSeries(sma20Series);
        
        // SMA50
        XYSeries sma50Series = new XYSeries("SMA50");
        for (int i = 0; i < sma50.length; i++) {
            if (!Double.isNaN(sma50[i])) {
                sma50Series.add(i, sma50[i]);
            }
        }
        priceDataset.addSeries(sma50Series);
        
        // EMA12
        XYSeries ema12Series = new XYSeries("EMA12");
        for (int i = 0; i < ema12.length; i++) {
            if (!Double.isNaN(ema12[i])) {
                ema12Series.add(i, ema12[i]);
            }
        }
        priceDataset.addSeries(ema12Series);
        
        XYPlot pricePlot = new XYPlot(priceDataset, null, new NumberAxis("Fiyat (₺)"), null);
        
        // Price renderer - thick solid lines
        XYLineAndShapeRenderer priceRenderer = new XYLineAndShapeRenderer(true, false);
        priceRenderer.setSeriesStroke(0, new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); // Close
        priceRenderer.setSeriesStroke(1, new BasicStroke(1.8f)); // SMA20
        priceRenderer.setSeriesStroke(2, new BasicStroke(1.8f)); // SMA50
        priceRenderer.setSeriesStroke(3, new BasicStroke(1.8f)); // EMA12
        
        priceRenderer.setSeriesPaint(0, PRICE_COLOR);     // Black close
        priceRenderer.setSeriesPaint(1, SMA20_COLOR);     // Blue SMA20
        priceRenderer.setSeriesPaint(2, SMA50_COLOR);     // Gold SMA50
        priceRenderer.setSeriesPaint(3, EMA12_COLOR);     // Teal EMA12
        
        pricePlot.setRenderer(priceRenderer);
        
        // Add BUY/SELL signal markers
        addTradeSignalMarkers(pricePlot, tradeSignals, data);
        
        pricePlot.setRangeGridlinePaint(GRID_COLOR);
        pricePlot.setRangeGridlinesVisible(true);
        pricePlot.setDomainGridlinesVisible(false);
        pricePlot.setBackgroundPaint(BG_COLOR);
        
        // Auto-scale Y axis with padding
        NumberAxis priceAxis = (NumberAxis) pricePlot.getRangeAxis();
        double minPrice = findMin(prices);
        double maxPrice = findMax(prices);
        double range = maxPrice - minPrice;
        double padding = range * 0.05; // 5% padding
        priceAxis.setRange(minPrice - padding, maxPrice + padding);
        priceAxis.setNumberFormatOverride(new java.text.DecimalFormat("#,##0.00"));
        priceAxis.setLabelFont(new Font("Arial", Font.BOLD, 12));
        
        // ===== PANEL 2: RSI Indicator =====
        XYSeriesCollection rsiDataset = new XYSeriesCollection();
        
        XYSeries rsiSeries = new XYSeries("RSI(14)");
        for (int i = 0; i < rsi.length; i++) {
            if (!Double.isNaN(rsi[i])) {
                rsiSeries.add(i, rsi[i]);
            }
        }
        rsiDataset.addSeries(rsiSeries);
        
        XYPlot rsiPlot = new XYPlot(rsiDataset, new NumberAxis("Dönem"), new NumberAxis("RSI"), null);
        
        // RSI renderer
        XYLineAndShapeRenderer rsiRenderer = new XYLineAndShapeRenderer(true, false);
        rsiRenderer.setSeriesStroke(0, new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        rsiRenderer.setSeriesPaint(0, RSI_COLOR);
        
        rsiPlot.setRenderer(rsiRenderer);
        rsiPlot.setRangeGridlinePaint(GRID_COLOR);
        rsiPlot.setRangeGridlinesVisible(true);
        rsiPlot.setDomainGridlinesVisible(false);
        rsiPlot.setBackgroundPaint(BG_COLOR);
        
        // RSI axis configuration
        NumberAxis rsiAxis = (NumberAxis) rsiPlot.getRangeAxis();
        rsiAxis.setRange(0, 100);
        rsiAxis.setLabelFont(new Font("Arial", Font.BOLD, 12));
        
        // Add RSI reference levels (30, 50, 70)
        addReferenceLines(rsiPlot);
        
        // ===== COMBINE PANELS =====
        CombinedDomainXYPlot combinedPlot = new CombinedDomainXYPlot(new NumberAxis("Dönem"));
        combinedPlot.add(pricePlot, 3); // Price takes 3/4 of space
        combinedPlot.add(rsiPlot, 1);   // RSI takes 1/4 of space
        combinedPlot.setGap(10);
        combinedPlot.setBackgroundPaint(BG_COLOR);
        
        JFreeChart chart = new JFreeChart(
            symbol + " - Teknik Analiz (SMA20, SMA50, EMA12, RSI)",
            new Font("Arial", Font.BOLD, 16),
            combinedPlot,
            true);
        
        chart.setBackgroundPaint(Color.WHITE);
        
        ChartUtils.saveChartAsPNG(new File(outputPath), chart, CHART_WIDTH, CHART_HEIGHT);
        System.out.println("Technical chart saved: " + outputPath);
    }

    /**
     * Generate combined price and volume chart with dual axis
     */
    public static void generateCombinedPriceVolumeChart(String symbol, List<StockData> data, String outputPath) throws IOException {
        if (data.isEmpty()) {
            System.out.println("No data available for combined chart generation");
            return;
        }

        // Take last 60 data points for better visualization
        int startIndex = Math.max(0, data.size() - 60);
        
        // Price series
        XYSeries priceSeries = new XYSeries("Fiyat");
        for (int i = startIndex; i < data.size(); i++) {
            priceSeries.add(i - startIndex, data.get(i).getClose());
        }
        XYSeriesCollection priceDataset = new XYSeriesCollection(priceSeries);

        // Volume series
        XYSeries volumeSeries = new XYSeries("Hacim");
        for (int i = startIndex; i < data.size(); i++) {
            volumeSeries.add(i - startIndex, data.get(i).getVolume());
        }
        XYSeriesCollection volumeDataset = new XYSeriesCollection(volumeSeries);

        // Create price chart
        JFreeChart chart = ChartFactory.createXYLineChart(
            symbol + " - Fiyat ve Hacim Analizi",
            "Zaman Periyodu",
            "Fiyat (₺)",
            priceDataset,
            PlotOrientation.VERTICAL,
            true,
            true,
            false);

        XYPlot plot = chart.getXYPlot();
        
        // Customize price renderer
        XYLineAndShapeRenderer priceRenderer = new XYLineAndShapeRenderer(true, false);
        priceRenderer.setSeriesPaint(0, PRICE_COLOR);
        priceRenderer.setSeriesStroke(0, new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        plot.setRenderer(0, priceRenderer);
        
        // Add volume dataset to second axis
        NumberAxis volumeAxis = new NumberAxis("Hacim");
        volumeAxis.setNumberFormatOverride(new java.text.DecimalFormat("#,###"));
        
        // Set volume axis range to make volume bars smaller
        double maxVolume = 0;
        for (int i = startIndex; i < data.size(); i++) {
            maxVolume = Math.max(maxVolume, data.get(i).getVolume());
        }
        volumeAxis.setRange(0, maxVolume * 6.0);
        
        plot.setRangeAxis(1, volumeAxis);
        plot.setDataset(1, volumeDataset);
        plot.mapDatasetToRangeAxis(1, 1);
        
        // Volume renderer (bar chart)
        XYBarRenderer volumeRenderer = new XYBarRenderer();
        volumeRenderer.setSeriesPaint(0, VOLUME_COLOR);
        volumeRenderer.setMargin(0.2);
        volumeRenderer.setShadowVisible(false);
        plot.setRenderer(1, volumeRenderer);

        // Customize plot appearance
        chart.setBackgroundPaint(Color.WHITE);
        plot.setBackgroundPaint(BG_COLOR);
        plot.setDomainGridlinePaint(GRID_COLOR);
        plot.setRangeGridlinePaint(GRID_COLOR);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinesVisible(true);

        // Save chart
        ChartUtils.saveChartAsPNG(new File(outputPath), chart, CHART_WIDTH, CHART_HEIGHT);
        System.out.println("Combined price-volume chart saved: " + outputPath);
    }

    /**
     * Add RSI reference lines (30, 50, 70) to indicate overbought/oversold
     */
    private static void addReferenceLines(XYPlot plot) {
        ValueMarker marker30 = new ValueMarker(30);
        marker30.setPaint(new Color(255, 100, 100, 100));
        marker30.setStroke(new BasicStroke(1.0f));
        plot.addRangeMarker(marker30);
        
        ValueMarker marker50 = new ValueMarker(50);
        marker50.setPaint(new Color(150, 150, 150, 50));
        marker50.setStroke(new BasicStroke(0.8f));
        plot.addRangeMarker(marker50);
        
        ValueMarker marker70 = new ValueMarker(70);
        marker70.setPaint(new Color(100, 100, 255, 100));
        marker70.setStroke(new BasicStroke(1.0f));
        plot.addRangeMarker(marker70);
    }

    /**
     * Find minimum price value (excluding NaN)
     */
    private static double findMin(double[] prices) {
        double min = Double.MAX_VALUE;
        for (double p : prices) {
            if (!Double.isNaN(p) && p > 0) {
                min = Math.min(min, p);
            }
        }
        return min == Double.MAX_VALUE ? 0 : min;
    }

    /**
     * Find maximum price value (excluding NaN)
     */
    private static double findMax(double[] prices) {
        double max = 0;
        for (double p : prices) {
            if (!Double.isNaN(p) && p > 0) {
                max = Math.max(max, p);
            }
        }
        return max;
    }
    
    /**
     * Add BUY/SELL signal markers to price chart
     */
    private static void addTradeSignalMarkers(XYPlot plot, List<SignalGenerator.TradePoint> signals, List<StockData> data) {
        if (signals.isEmpty()) {
            return;
        }
        
        for (SignalGenerator.TradePoint signal : signals) {
            if (signal.index >= data.size()) continue;
            
            double x = signal.index;
            double y = signal.price;
            
            if ("BUY".equals(signal.type)) {
                // Green upward arrow for BUY
                XYPointerAnnotation buyAnnotation = new XYPointerAnnotation(
                    "AL",
                    x, y,
                    Math.PI / 2.0  // Point upward (90 degrees)
                );
                buyAnnotation.setArrowLength(12);
                buyAnnotation.setArrowWidth(8);
                buyAnnotation.setBaseRadius(20);
                buyAnnotation.setTipRadius(5);
                buyAnnotation.setArrowPaint(BUY_SIGNAL_COLOR);
                buyAnnotation.setPaint(BUY_SIGNAL_COLOR);
                buyAnnotation.setFont(new Font("Arial", Font.BOLD, 11));
                buyAnnotation.setTextAnchor(TextAnchor.TOP_CENTER);
                buyAnnotation.setToolTipText(signal.reason);
                plot.addAnnotation(buyAnnotation);
                
            } else if ("SELL".equals(signal.type)) {
                // Red downward arrow for SELL
                XYPointerAnnotation sellAnnotation = new XYPointerAnnotation(
                    "SAT",
                    x, y,
                    -Math.PI / 2.0  // Point downward (-90 degrees)
                );
                sellAnnotation.setArrowLength(12);
                sellAnnotation.setArrowWidth(8);
                sellAnnotation.setBaseRadius(20);
                sellAnnotation.setTipRadius(5);
                sellAnnotation.setArrowPaint(SELL_SIGNAL_COLOR);
                sellAnnotation.setPaint(SELL_SIGNAL_COLOR);
                sellAnnotation.setFont(new Font("Arial", Font.BOLD, 11));
                sellAnnotation.setTextAnchor(TextAnchor.BOTTOM_CENTER);
                sellAnnotation.setToolTipText(signal.reason);
                plot.addAnnotation(sellAnnotation);
            }
        }
    }
}
