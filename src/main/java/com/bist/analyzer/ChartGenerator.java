package com.bist.analyzer;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.DefaultHighLowDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.chart.ChartUtils;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

public class ChartGenerator {
    
    private static final int CHART_WIDTH = 1200;
    private static final int CHART_HEIGHT = 600;

    /**
     * Generate a candlestick chart with price data
     */
    public static void generateCandleChart(String symbol, List<StockData> data, String outputPath) throws IOException {
        if (data.isEmpty()) {
            System.out.println("No data available for chart generation");
            return;
        }

        // Prepare data for candlestick chart
        Date[] dates = new Date[data.size()];
        double[] opens = new double[data.size()];
        double[] closes = new double[data.size()];
        double[] highs = new double[data.size()];
        double[] lows = new double[data.size()];
        double[] volumes = new double[data.size()];

        for (int i = 0; i < data.size(); i++) {
            StockData d = data.get(i);
            dates[i] = new Date(d.getTimestamp());
            opens[i] = d.getOpen();
            closes[i] = d.getClose();
            highs[i] = d.getHigh();
            lows[i] = d.getLow();
            volumes[i] = d.getVolume();
        }

        DefaultHighLowDataset dataset = new DefaultHighLowDataset(
            symbol, dates, highs, lows, opens, closes, volumes);

        JFreeChart chart = ChartFactory.createHighLowChart(
            symbol + " - Price Chart",
            "Date",
            "Price (TRY)",
            dataset,
            true);

        // Customize chart
        XYPlot plot = chart.getXYPlot();
        DateAxis xAxis = (DateAxis) plot.getDomainAxis();
        xAxis.setVerticalTickLabels(true);
        
        chart.setBackgroundPaint(Color.WHITE);
        plot.setBackgroundPaint(new Color(240, 240, 240));

        // Save chart
        ChartUtils.saveChartAsPNG(new File(outputPath), chart, CHART_WIDTH, CHART_HEIGHT);
        System.out.println("Chart saved: " + outputPath);
    }

    /**
     * Generate a chart with technical indicator lines (MA, EMA, etc)
     */
    public static void generateTechnicalChart(String symbol, List<StockData> data, 
                                            double[] sma20, double[] sma50, double[] ema12,
                                            double[] rsi, String outputPath) throws IOException {
        if (data.isEmpty()) {
            System.out.println("No data available for technical chart generation");
            return;
        }

        XYSeriesCollection dataset = new XYSeriesCollection();

        // Add price line
        XYSeries priceSeries = new XYSeries("Close Price");
        for (int i = 0; i < data.size(); i++) {
            priceSeries.add(i, data.get(i).getClose());
        }
        dataset.addSeries(priceSeries);

        // Add SMA20
        XYSeries sma20Series = new XYSeries("SMA20");
        for (int i = 0; i < sma20.length; i++) {
            if (!Double.isNaN(sma20[i])) {
                sma20Series.add(i, sma20[i]);
            }
        }
        dataset.addSeries(sma20Series);

        // Add SMA50
        XYSeries sma50Series = new XYSeries("SMA50");
        for (int i = 0; i < sma50.length; i++) {
            if (!Double.isNaN(sma50[i])) {
                sma50Series.add(i, sma50[i]);
            }
        }
        dataset.addSeries(sma50Series);

        // Add EMA12
        XYSeries ema12Series = new XYSeries("EMA12");
        for (int i = 0; i < ema12.length; i++) {
            if (!Double.isNaN(ema12[i])) {
                ema12Series.add(i, ema12[i]);
            }
        }
        dataset.addSeries(ema12Series);

        JFreeChart chart = ChartFactory.createXYLineChart(
            symbol + " - Technical Analysis",
            "Period",
            "Price (TRY)",
            dataset,
            PlotOrientation.VERTICAL,
            true,
            true,
            false);

        // Customize chart
        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
        renderer.setSeriesStroke(0, new BasicStroke(2.0f)); // Close price - thick line
        renderer.setSeriesStroke(1, new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
            10.0f, new float[]{5.0f}, 0.0f)); // SMA20 - dashed
        renderer.setSeriesStroke(2, new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
            10.0f, new float[]{5.0f}, 0.0f)); // SMA50 - dashed
        renderer.setSeriesStroke(3, new BasicStroke(1.5f)); // EMA12

        renderer.setSeriesPaint(0, Color.BLACK);
        renderer.setSeriesPaint(1, Color.BLUE);
        renderer.setSeriesPaint(2, Color.RED);
        renderer.setSeriesPaint(3, Color.GREEN);

        chart.setBackgroundPaint(Color.WHITE);
        plot.setBackgroundPaint(new Color(240, 240, 240));

        // Save chart
        ChartUtils.saveChartAsPNG(new File(outputPath), chart, CHART_WIDTH, CHART_HEIGHT);
        System.out.println("Technical chart saved: " + outputPath);
    }

    /**
     * Generic method to generate charts
     */
    public static void generateChart(String symbol, List<StockData> data, double[] indicator1, 
                                    double[] indicator2, String outputDir, String chartType) throws IOException {
        String fileName = outputDir + File.separator + symbol + "_" + chartType + ".png";
        
        if ("price".equals(chartType)) {
            generateCandleChart(symbol, data, fileName);
        } else if ("technical".equals(chartType)) {
            generateTechnicalChart(symbol, data, indicator1, indicator2, new double[data.size()], 
                                 new double[data.size()], fileName);
        }
    }

    /**
     * Generate RSI indicator chart
     */
    public static void generateRSIChart(String symbol, double[] rsi, String outputPath) throws IOException {
        XYSeries rsiSeries = new XYSeries("RSI");
        
        for (int i = 0; i < rsi.length; i++) {
            if (!Double.isNaN(rsi[i])) {
                rsiSeries.add(i, rsi[i]);
            }
        }

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(rsiSeries);

        JFreeChart chart = ChartFactory.createXYLineChart(
            symbol + " - RSI Indicator",
            "Period",
            "RSI",
            dataset,
            PlotOrientation.VERTICAL,
            true,
            true,
            false);

        // Add reference lines (30, 50, 70)
        XYPlot plot = chart.getXYPlot();
        plot.setRangeGridlinesVisible(true);

        XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, Color.BLUE);
        renderer.setSeriesStroke(0, new BasicStroke(2.0f));

        chart.setBackgroundPaint(Color.WHITE);
        plot.setBackgroundPaint(new Color(240, 240, 240));
        plot.getRangeAxis().setRange(0, 100);

        ChartUtils.saveChartAsPNG(new File(outputPath), chart, CHART_WIDTH, CHART_HEIGHT);
        System.out.println("RSI chart saved: " + outputPath);
    }
}
