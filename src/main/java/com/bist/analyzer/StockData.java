package com.bist.analyzer;

public class StockData {
    private String symbol;
    private long timestamp;
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;

    public StockData(String symbol, long timestamp, double open, double high, double low, double close, long volume) {
        this.symbol = symbol;
        this.timestamp = timestamp;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    // Getters
    public String getSymbol() { return symbol; }
    public long getTimestamp() { return timestamp; }
    public double getOpen() { return open; }
    public double getHigh() { return high; }
    public double getLow() { return low; }
    public double getClose() { return close; }
    public long getVolume() { return volume; }

    @Override
    public String toString() {
        return String.format("%s[%d]: O=%.2f H=%.2f L=%.2f C=%.2f V=%d", 
            symbol, timestamp, open, high, low, close, volume);
    }
}
