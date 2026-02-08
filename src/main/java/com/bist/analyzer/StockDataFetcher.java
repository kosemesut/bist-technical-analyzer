package com.bist.analyzer;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.TimeZone;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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
        FileWriter logWriter = null;
        
        try {
            // Create log file for debugging
            logWriter = new FileWriter("yahoo_data_log_" + symbol + ".txt", false);
            logWriter.write("=== Yahoo Finance Data Log for " + symbol + " ===\n");
            logWriter.write("Timestamp UTC\tTimestamp MS\tDate (UTC)\tDate (Istanbul)\tOpen\tHigh\tLow\tClose\tVolume\n");
            logWriter.flush();
            
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
                if (logWriter != null) {
                    logWriter.write("\nERROR: HTTP " + responseCode + "\n");
                    logWriter.flush();
                }
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
                if (logWriter != null) {
                    logWriter.write("\nERROR: No chart data in response\n");
                    logWriter.flush();
                }
                return new ArrayList<>();
            }
            
            JSONArray resultArray = json.getJSONObject("chart").getJSONArray("result");
            if (resultArray.length() == 0 || resultArray.getJSONObject(0).isEmpty()) {
                System.err.println("Empty result set for " + symbol);
                if (logWriter != null) {
                    logWriter.write("\nERROR: Empty result set\n");
                    logWriter.flush();
                }
                return new ArrayList<>();
            }
            
            JSONObject result = resultArray.getJSONObject(0);
            
            // Check if timestamp exists
            if (!result.has("timestamp") || result.isNull("timestamp")) {
                System.err.println("No timestamp data for " + symbol + " - API may not support this symbol");
                if (logWriter != null) {
                    logWriter.write("\nERROR: No timestamp data\n");
                    logWriter.flush();
                }
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
            
            // Get timezone info from Yahoo API if available
            String yahooTimezone = "UTC";
            if (result.has("meta") && result.getJSONObject("meta").has("timezone")) {
                yahooTimezone = result.getJSONObject("meta").getString("timezone");
            }
            logWriter.write("\nYahoo API Timezone: " + yahooTimezone + "\n");
            logWriter.write("System Default Timezone: " + TimeZone.getDefault().getID() + "\n\n");
            logWriter.flush();
            
            for (int i = 0; i < timestamps.length(); i++) {
                try {
                    long timestampSeconds = timestamps.getLong(i);
                    long timestampMs = timestampSeconds * 1000; // Convert to milliseconds
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
                        // Format dates for logging
                        SimpleDateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                        String utcDate = utcFormat.format(new Date(timestampMs));
                        
                        // Format with Istanbul timezone
                        ZonedDateTime istanbulTime = ZonedDateTime.ofInstant(
                            Instant.ofEpochMilli(timestampMs),
                            ZoneId.of("Europe/Istanbul")
                        );
                        DateTimeFormatter istanbulFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                        String istanbulDate = istanbulTime.format(istanbulFormatter);
                        
                        logWriter.write(timestampSeconds + "\t" + timestampMs + "\t" + utcDate + "\t" + 
                                      istanbulDate + "\t" + open + "\t" + high + "\t" + low + "\t" + close + "\t" + volume + "\n");
                        logWriter.flush();
                        
                        dataList.add(new StockData(symbol, timestampMs, open, high, low, close, volume));
                    }
                } catch (Exception e) {
                    System.err.println("Error parsing data point: " + e.getMessage());
                    if (logWriter != null) {
                        logWriter.write("ERROR parsing data point: " + e.getMessage() + "\n");
                        logWriter.flush();
                    }
                }
            }
            
            System.out.println("Successfully fetched " + dataList.size() + " data points for " + symbol);
            if (logWriter != null) {
                logWriter.write("\n\nTotal data points: " + dataList.size() + "\n");
                logWriter.flush();
            }
            
        } catch (Exception e) {
            System.err.println("Error fetching data for " + symbol + ": " + e.getMessage());
            e.printStackTrace();
            try {
                if (logWriter != null) {
                    logWriter.write("\n\nFATAL ERROR: " + e.getMessage() + "\n");
                    logWriter.flush();
                }
            } catch (IOException ioe) {
                // Ignore
            }
        } finally {
            if (logWriter != null) {
                try {
                    logWriter.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
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
