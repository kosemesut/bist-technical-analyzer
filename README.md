# BIST Technical Analysis System

Automated technical analysis system for BIST stocks with real-time trading signal generation.

## Features

✅ **Automated Data Fetching**
- Hourly data (1-hour candles, 3 months history)
- Daily data (1-day candles, 5 years history)
- Data sourced from Yahoo Finance

✅ **Technical Indicators**
- RSI (Relative Strength Index) - 14 period
- MACD (Moving Average Convergence Divergence)
- Simple Moving Averages (SMA 20, 50)
- Exponential Moving Averages (EMA 12, 26)
- Bollinger Bands (20 period, 2 std dev)

✅ **Trading Signals**
- STRONG_BUY: Multiple bullish signals
- BUY: Positive technical indicators
- HOLD: Mixed signals
- SELL: Negative technical indicators
- STRONG_SELL: Multiple bearish signals

✅ **Visualizations**
- Candlestick price charts with volume
- Technical indicator overlay charts
- RSI indicator charts

✅ **HTML Report**
- Interactive trading signals dashboard
- Real-time stock data
- Signal confidence visualization
- Detailed analysis per stock

✅ **Automation**
- GitHub Actions: 15-minute update cycle
- GitHub Pages: Automatic report publishing
- No manual intervention needed

## Setup Instructions

### Prerequisites
- GitHub account
- Git installed locally
- Java 11+ (optional for local testing)

### Installation

1. **Fork or Clone Repository**
   ```bash
   git clone https://github.com/kosemesut/bist-technical-analyzer.git
   cd bist-technical-analyzer
   ```

2. **Edit Stock List**
   Edit `stock_list.txt` and add BIST stock symbols (one per line):
   ```
   THYAO
   SOKM
   FROTO
   SISE
   ```

3. **Configure GitHub Actions**
   - Repository must be public (for free GitHub Pages)
   - Settings → Pages → Source: Deploy from branch
   - Select 'gh-pages' branch

4. **Enable GitHub Actions**
   - Actions tab → Enable workflows

5. **First Analysis**
   - Go to Actions tab
   - Manually trigger "BIST Technical Analysis" workflow
   - Wait for completion - check "Deploy to GitHub Pages" output

### Access Report

After first successful run, your report will be available at:
```
https://kosemesut.github.io/bist-technical-analyzer
```

## Local Testing

```bash
# Build project
mvn clean package

# Run analysis
java -jar target/bist-technical-analyzer-1.0.0.jar

# Output files
# - output/index.html (main report)
# - output/charts/ (PNG chart files)
```

## File Structure

```
.
├── src/main/java/com/bist/analyzer/
│   ├── BistAnalyzerApp.java           # Main application entry point
│   ├── StockDataFetcher.java          # Yahoo Finance data retrieval
│   ├── StockData.java                 # Data model
│   ├── TechnicalIndicators.java       # RSI, MACD, MA, BB calculations
│   ├── SignalGenerator.java           # Trading signal logic
│   ├── ChartGenerator.java            # JFreeChart visualization
│   └── HtmlReportGenerator.java       # HTML report generation
├── pom.xml                            # Maven configuration
├── stock_list.txt                     # Stock symbols to analyze
├── .github/workflows/analysis.yml     # GitHub Actions workflow
└── README.md                          # This file
```

## Stock Symbols

BIST stocks should be specified WITHOUT the ".IS" suffix:
- ✅ THYAO (Türk Hava Yolları)
- ✅ SOKM (Şişecam)
- ✅ FROTO (Froto Leasing)
- ✅ SISE (Sisecam)

## GitHub Actions Schedule

Current schedule: Every 15 minutes
Modify in `.github/workflows/analysis.yml`:
```yaml
schedule:
  - cron: '*/15 * * * *'
```

Cron format: `minute hour day month day_of_week`
- `*/15 * * * *` = Every 15 minutes
- `0 */1 * * *` = Every hour
- `0 9 * * 1-5` = Every weekday at 9 AM

## Customization

### Change Stock List
Edit `stock_list.txt` and push to GitHub. Actions will use the new list.

### Modify Technical Indicators
Edit `src/main/java/com/bist/analyzer/TechnicalIndicators.java`

### Adjust Signal Logic
Edit `src/main/java/com/bist/analyzer/SignalGenerator.java`

### Change Update Frequency
Edit `.github/workflows/analysis.yml` cron expression

## Dependencies

- JFreeChart 1.5.3 - Chart generation
- Apache HttpComponents - HTTP requests
- JSON-java - JSON parsing
- Apache Commons CSV - Data parsing
- SLF4J - Logging

## Important Notes

⚠️ **GitHub Pages Limitation**: Free GitHub Pages must be public. If you want privacy, use a paid plan.

⚠️ **Data Accuracy**: Yahoo Finance provides delayed data (typically 15-20 minutes for Turkish market).

⚠️ **Not Financial Advice**: This tool is for analysis only. Always conduct your own research before trading.

## Workflow Status

Check GitHub Actions tab for:
- Build status
- Execution logs
- Last update timestamp
- Workflow run history

## Troubleshooting

### Workflow fails silently
1. Check GitHub Actions logs
2. Ensure stock symbols are correct
3. Check internet connectivity
4. Verify Java version is 11+

### No charts appearing
1. Check if JFreeChart dependency is downloaded
2. Look for errors in workflow logs
3. Verify Maven build completes successfully

### GitHub Pages not updating
1. Check "Deploy to GitHub Pages" step in workflow
2. Go to Settings → Pages → Verify source branch
3. Check gh-pages branch exists

## License

MIT License

## Support

For issues and questions:
1. Check GitHub Issues
2. Review workflow logs
3. Verify configuration files

---

**Last Updated**: February 2026
**Version**: 1.0.0
**Status**: Production Ready ✅
