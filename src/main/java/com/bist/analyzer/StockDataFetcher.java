package com.bist.analyzer;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import org.json.JSONObject;
import org.json.JSONArray;

public class StockDataFetcher {
    
    private static final String YAHOO_FINANCE_URL = "https://query1.finance.yahoo.com/v8/finance/chart";
    private static final int TIMEOUT = 10000;

    /**
     * Fetch historical data from Yahoo Finance
     * interval: "1h" for hourly, "1d" for daily
     * range: "3mo" for 3 months, "5y" for 5 years
     */
    public static List<StockData> fetchData(String symbol, String interval, String range) {
        List<StockData> dataList = new ArrayList<>();
        
        try {
            // Add .IS suffix for BIST stocks
            String yahooSymbol = symbol.contains(".") ? symbol : symbol + ".IS";
            
            String urlStr = String.format("%s/%s?interval=%s&range=%s", 
                YAHOO_FINANCE_URL, URLEncoder.encode(yahooSymbol, "UTF-8"), interval, range);
            
            System.out.println("Fetching data for " + yahooSymbol + " (" + interval + ", " + range + ")");
            
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                System.err.println("Error: HTTP " + responseCode + " for " + yahooSymbol);
                return dataList;
            }
            
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String inputLine;
            
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            
            JSONObject json = new JSONObject(response.toString());
            
            // Error handling for invalid API responses
            if (!json.has("chart")) {
                System.err.println("No chart data in response for " + symbol);
                return new ArrayList<>();
            }
            
            JSONArray resultArray = json.getJSONObject("chart").getJSONArray("result");
            if (resultArray.length() == 0 || resultArray.getJSONObject(0).isEmpty()) {
                System.err.println("Empty result set for " + symbol);
                return new ArrayList<>();
            }
            
            JSONObject result = resultArray.getJSONObject(0);
            
            // Check if timestamp exists
            if (!result.has("timestamp") || result.isNull("timestamp")) {
                System.err.println("No timestamp data for " + symbol + " - API may not support this symbol");
                return new ArrayList<>();
            }
            
            JSONObject indicators = result.getJSONObject("indicators");
            JSONArray quoteArr = indicators.getJSONArray("quote");
            JSONObject quote = quoteArr.getJSONObject(0);
            
            JSONArray timestamps = result.getJSONArray("timestamp");
            JSONArray closes = quote.getJSONArray("close");
            JSONArray opens = quote.getJSONArray("open");
            JSONArray highs = quote.getJSONArray("high");
            JSONArray lows = quote.getJSONArray("low");
            JSONArray volumes = quote.getJSONArray("volume");
            
            for (int i = 0; i < timestamps.length(); i++) {
                try {
                    long timestamp = timestamps.getLong(i) * 1000; // Convert to milliseconds
                    double open = opens.isNull(i) ? 0 : opens.getDouble(i);
                    double high = highs.isNull(i) ? 0 : highs.getDouble(i);
                    double low = lows.isNull(i) ? 0 : lows.getDouble(i);
                    double close = closes.isNull(i) ? 0 : closes.getDouble(i);
                    
                    // Try to get volume - handle both int and long types
                    long volume = 0;
                    try {
                        if (!volumes.isNull(i)) {
                            Object volObj = volumes.get(i);
                            if (volObj instanceof Number) {
                                volume = ((Number) volObj).longValue();
                            }
                        }
                    } catch (Exception ve) {
                        volume = 0;
                    }
                    
                    if (close > 0) { // Only add valid data
                        dataList.add(new StockData(symbol, timestamp, open, high, low, close, volume));
                    }
                } catch (Exception e) {
                    System.err.println("Error parsing data point: " + e.getMessage());
                }
            }
            
            System.out.println("Successfully fetched " + dataList.size() + " data points for " + symbol);
            
        } catch (Exception e) {
            System.err.println("Error fetching data for " + symbol + ": " + e.getMessage());
            e.printStackTrace();
        }
        
        return dataList;
    }

    /**
     * Get latest trading data for a symbol
     */
    public static StockData fetchLatestData(String symbol) {
        List<StockData> data = fetchData(symbol, "1d", "5d");
        if (!data.isEmpty()) {
            return data.get(data.size() - 1);
        }
        return null;
    }

    /**
     * Format timestamp to readable date
     */
    public static String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        return sdf.format(new Date(timestamp));
    }
}
