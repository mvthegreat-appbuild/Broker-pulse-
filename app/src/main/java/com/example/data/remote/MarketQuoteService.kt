package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class LiveQuote(
    val symbol: String,
    val shortName: String,
    val cmp: Double,
    val previousClose: Double,
    val dayChange: Double,
    val dayChangePercent: Double,
    val dayHigh: Double,
    val dayLow: Double,
    val marketState: String,
    val sparkline: List<Double>
)

data class StockSearchResult(
    val symbol: String,
    val companyName: String,
    val exchange: String
)

class MarketQuoteService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) {

    suspend fun fetchQuote(symbol: String): LiveQuote? = withContext(Dispatchers.IO) {
        val cleanSymbol = symbol.trim().uppercase()
        if (cleanSymbol.isBlank()) return@withContext null

        val encoded = URLEncoder.encode(cleanSymbol, "UTF-8")
        val urls = listOf(
            "https://query1.finance.yahoo.com/v8/finance/chart/$encoded?interval=15m&range=1d",
            "https://query2.finance.yahoo.com/v8/finance/chart/$encoded?interval=15m&range=1d"
        )

        for (url in urls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                    )
                    .header("Accept", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) continue
                    val body = response.body?.string() ?: continue
                    val parsed = parseChartResponse(cleanSymbol, body)
                    if (parsed != null) return@withContext parsed
                }
            } catch (_: Exception) {
                // Try fallback host
            }
        }
        null
    }

    suspend fun searchIndianStocks(query: String): List<StockSearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val url = "https://query1.finance.yahoo.com/v1/finance/search?q=$encoded&quotesCount=12&newsCount=0&region=IN&lang=en-IN"
        try {
            val request = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                )
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                val quotes = json.optJSONArray("quotes") ?: return@withContext emptyList()
                val results = mutableListOf<StockSearchResult>()
                for (i in 0 until quotes.length()) {
                    val item = quotes.optJSONObject(i) ?: continue
                    val sym = item.optString("symbol")
                    val quoteType = item.optString("quoteType")
                    if (sym.isBlank() || quoteType != "EQUITY") continue
                    val isIndian = sym.endsWith(".NS") || sym.endsWith(".BO")
                    if (!isIndian) continue
                    val name = item.optString("shortname")
                        .ifBlank { item.optString("longname") }
                        .ifBlank { sym.removeSuffix(".NS").removeSuffix(".BO") }
                    val exch = if (sym.endsWith(".NS")) "NSE" else "BSE"
                    results += StockSearchResult(
                        symbol = sym,
                        companyName = name,
                        exchange = exch
                    )
                }
                return@withContext results.sortedByDescending { it.exchange == "NSE" }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseChartResponse(requestedSymbol: String, jsonBody: String): LiveQuote? {
        val root = JSONObject(jsonBody)
        val chart = root.optJSONObject("chart") ?: return null
        val resultArray = chart.optJSONArray("result") ?: return null
        if (resultArray.length() == 0) return null
        val first = resultArray.optJSONObject(0) ?: return null
        val meta = first.optJSONObject("meta") ?: return null

        val cmp = meta.optDouble("regularMarketPrice", Double.NaN)
        if (cmp.isNaN() || cmp <= 0.0) return null

        val prevClose = meta.optDouble("chartPreviousClose", Double.NaN).let {
            if (it.isNaN() || it <= 0.0) meta.optDouble("previousClose", cmp) else it
        }
        val dayHigh = meta.optDouble("regularMarketDayHigh", cmp)
        val dayLow = meta.optDouble("regularMarketDayLow", cmp)
        val change = cmp - prevClose
        val changePct = if (prevClose > 0.0) (change / prevClose) * 100.0 else 0.0
        val shortName = meta.optString("shortName")
            .ifBlank { meta.optString("symbol", requestedSymbol) }
        val marketState = meta.optString("marketState", "REGULAR")

        val sparkline = mutableListOf<Double>()
        val indicators = first.optJSONObject("indicators")
        val quoteArr = indicators?.optJSONArray("quote")
        val quoteObj = quoteArr?.optJSONObject(0)
        val closes = quoteObj?.optJSONArray("close")
        if (closes != null) {
            for (i in 0 until closes.length()) {
                if (!closes.isNull(i)) {
                    val v = closes.optDouble(i, Double.NaN)
                    if (!v.isNaN() && v > 0.0) {
                        sparkline += v
                    }
                }
            }
        }
        // Keep last 24 points for crisp mini-sparkline
        val trimmedSparkline = if (sparkline.size > 24) {
            val step = (sparkline.size / 24).coerceAtLeast(1)
            sparkline.filterIndexed { index, _ -> index % step == 0 }.takeLast(24)
        } else {
            sparkline
        }

        return LiveQuote(
            symbol = meta.optString("symbol", requestedSymbol),
            shortName = shortName,
            cmp = cmp,
            previousClose = prevClose,
            dayChange = change,
            dayChangePercent = changePct,
            dayHigh = dayHigh,
            dayLow = dayLow,
            marketState = marketState,
            sparkline = trimmedSparkline
        )
    }
}
