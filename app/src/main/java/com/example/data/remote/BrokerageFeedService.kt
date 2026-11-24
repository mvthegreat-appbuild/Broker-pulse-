
package com.example.data.remote

import android.util.Xml
import com.example.data.local.CallType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class BrokerageFeedItem(
    val id: String,
    val title: String,
    val summary: String,
    val sourceName: String,
    val sourceUrl: String,
    val brokerage: String,
    val detectedSymbol: String?,
    val detectedCompany: String?,
    val callType: CallType,
    val extractedEntryPrice: Double?,
    val extractedTargetPrice: Double?,
    val extractedStopLoss: Double?,
    val publishedAtLabel: String
)

class BrokerageFeedService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {

    private data class FeedEndpoint(
        val url: String,
        val defaultSourceName: String,
        val filterForBrokerCalls: Boolean
    )

    private val endpoints = listOf(
        FeedEndpoint(
            url = "https://economictimes.indiatimes.com/markets/stocks/recos/rssfeeds/3053611.cms",
            defaultSourceName = "Economic Times",
            filterForBrokerCalls = false
        ),
        FeedEndpoint(
            url = "https://news.google.com/rss/search?q=site:ndtvprofit.com+target+price+Motilal+Oswal+OR+SBI+Securities+OR+Anand+Rathi+OR+ICICI+Direct&hl=en-IN&gl=IN&ceid=IN:en",
            defaultSourceName = "NDTV Profit",
            filterForBrokerCalls = false
        ),
        FeedEndpoint(
            url = "https://news.google.com/rss/search?q=site:economictimes.indiatimes.com+%22Motilal+Oswal%22+OR+%22SBI+Securities%22+OR+%22Anand+Rathi%22+%22target%22&hl=en-IN&gl=IN&ceid=IN:en",
            defaultSourceName = "Economic Times",
            filterForBrokerCalls = false
        ),
        FeedEndpoint(
            url = "https://www.livemint.com/rss/markets",
            defaultSourceName = "LiveMint",
            filterForBrokerCalls = true
        ),
        FeedEndpoint(
            url = "https://economictimes.indiatimes.com/markets/stocks/rssfeeds/2146842.cms",
            defaultSourceName = "Economic Times",
            filterForBrokerCalls = true
        )
    )

    suspend fun fetchLiveBrokerageRecommendations(): List<BrokerageFeedItem> =
        withContext(Dispatchers.IO) {
            val allItems = coroutineScope {
                endpoints.map { endpoint ->
                    async { fetchSingleFeed(endpoint) }
                }.awaitAll().flatten()
            }

            val seen = HashSet<String>()
            allItems
                .filter { item ->
                    val key = item.title.lowercase(Locale.US).take(60)
                    seen.add(key)
                }
                .sortedWith(
                    compareByDescending<BrokerageFeedItem> {
                        it.extractedTargetPrice != null
                    }
                        .thenByDescending {
                            it.brokerage != "Brokerage Consensus"
                        }
                        .thenByDescending {
                            it.detectedSymbol != null
                        }
                )
                .take(45)
        }

    private fun fetchSingleFeed(endpoint: FeedEndpoint): List<BrokerageFeedItem> {
        return try {
            val request = Request.Builder()
                .url(endpoint.url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                )
                .header("Accept", "application/rss+xml, application/xml, text/xml")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val xml = response.body?.string() ?: return emptyList()
                parseRssXml(xml, endpoint)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseRssXml(
        xml: String,
        endpoint: FeedEndpoint
    ): List<BrokerageFeedItem> {
        val items = mutableListOf<BrokerageFeedItem>()

        val nowMillis = System.currentTimeMillis()
        val tenDaysAgoMillis = nowMillis - TEN_DAYS_MILLIS

        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var insideItem = false
            var title = ""
            var link = ""
            var description = ""
            var pubDate = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tagName = parser.name?.lowercase(Locale.US) ?: ""

                        if (tagName == "item") {
                            insideItem = true
                            title = ""
                            link = ""
                            description = ""
                            pubDate = ""
                        } else if (insideItem) {
                            when (tagName) {
                                "title" -> title = safeNextText(parser)
                                "link" -> link = safeNextText(parser)
                                "description" -> description = safeNextText(parser)
                                "pubdate" -> pubDate = safeNextText(parser)
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        if (parser.name.equals("item", ignoreCase = true) &&
                            insideItem
                        ) {
                            insideItem = false

                            val cleanTitle = stripHtml(title)
                            val cleanDesc = stripHtml(description)
                            val combined = "$cleanTitle $cleanDesc"

                            val publishedAtMillis = parsePubDateMillis(pubDate)

                            val isWithinLastTenDays =
                                publishedAtMillis != null &&
                                publishedAtMillis in tenDaysAgoMillis..nowMillis

                            if (
                                cleanTitle.isNotBlank() &&
                                isWithinLastTenDays &&
                                (!endpoint.filterForBrokerCalls ||
                                    looksLikeBrokerCall(combined))
                            ) {
                                items += buildFeedItem(
                                    title = cleanTitle,
                                    summary = cleanDesc,
                                    link = link.trim(),
                                    pubDate = formatPubDate(pubDate),
                                    defaultSource = endpoint.defaultSourceName
                                )
                            }
                        }
                    }
                }

                eventType = parser.next()
            }
        } catch (_: Exception) {
            // Ignore malformed RSS chunk
        }

        return items
    }

    private fun safeNextText(parser: XmlPullParser): String {
        return try {
            if (parser.next() == XmlPullParser.TEXT) {
                val text = parser.text ?: ""
                parser.nextTag()
                text
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun looksLikeBrokerCall(text: String): Boolean {
        val lower = text.lowercase(Locale.US)

        val hasAction =
            lower.contains("buy ") ||
            lower.contains("sell ") ||
            lower.contains("target") ||
            lower.contains("upside") ||
            lower.contains("stop loss")

        val hasBroker = knownBrokerages.any {
            lower.contains(it.lowercase(Locale.US))
        }

        return hasBroker ||
            (hasAction && (lower.contains("rs ") || lower.contains("₹")))
    }

    private fun buildFeedItem(
        title: String,
        summary: String,
        link: String,
        pubDate: String,
        defaultSource: String
    ): BrokerageFeedItem {
        val combined = "$title. $summary"
        val brokerage = detectBrokerage(combined)
        val source = detectSource(combined, link, defaultSource)
        val callType = detectCallType(combined)
        val target = extractPrice(targetRegex, combined)
        val stopLoss = extractPrice(stopLossRegex, combined)
        val entry = extractPrice(entryRegex, combined)
        val (symbol, company) =
            detectStock(title) ?: detectStock(combined) ?: (null to null)

        return BrokerageFeedItem(
            id = "${link.hashCode()}_${title.hashCode()}",
            title = cleanGoogleNewsTitleSuffix(title),
            summary = summary.ifBlank {
                "Analyst recommendation from $source"
            }.take(220),
            sourceName = source,
            sourceUrl = link,
            brokerage = brokerage,
            detectedSymbol = symbol,
            detectedCompany = company,
            callType = callType,
            extractedEntryPrice = entry,
            extractedTargetPrice = target,
            extractedStopLoss = stopLoss,
            publishedAtLabel = pubDate
        )
    }

    private fun cleanGoogleNewsTitleSuffix(title: String): String {
        return title
            .replace(" - NDTV Profit", "")
            .replace(" - The Economic Times", "")
            .replace(" - Economic Times", "")
            .replace(" - Mint", "")
            .trim()
    }

    private fun detectBrokerage(text: String): String {
        val lower = text.lowercase(Locale.US)

        return knownBrokerages.firstOrNull {
            lower.contains(it.lowercase(Locale.US))
        } ?: "Brokerage Consensus"
    }

    private fun detectSource(
        text: String,
        link: String,
        fallback: String
    ): String {
        val lowerLink = link.lowercase(Locale.US)
        val lowerText = text.lowercase(Locale.US)

        return when {
            lowerLink.contains("ndtvprofit") ||
                lowerText.contains("ndtv profit") -> "NDTV Profit"

            lowerLink.contains("economictimes") ||
                lowerText.contains("economic times") -> "Economic Times"

            lowerLink.contains("livemint") ||
                lowerText.contains("mint") -> "LiveMint"

            lowerLink.contains("moneycontrol") -> "Moneycontrol"
            lowerLink.contains("business-standard") -> "Business Standard"
            else -> fallback
        }
    }

    private fun detectCallType(text: String): CallType {
        val lower = text.lowercase(Locale.US)

        return if (
            lower.contains("sell ") ||
            lower.contains("reduce ") ||
            lower.contains("downgrade")
        ) {
            CallType.SELL
        } else {
            CallType.BUY
        }
    }

    private fun extractPrice(regex: Regex, text: String): Double? {
        val match = regex.find(text) ?: return null
        val raw = match.groupValues.getOrNull(1)
            ?.replace(",", "") ?: return null

        val parsed = raw.toDoubleOrNull() ?: return null
        return if (parsed in 5.0..200_000.0) parsed else null
    }

    private fun detectStock(text: String): Pair<String, String>? {
        val lower = text.lowercase(Locale.US)

        for ((keyword, pair) in indianStockDictionary) {
            if (
                Regex("\\b${Regex.escape(keyword)}\\b")
                    .containsMatchIn(lower)
            ) {
                return pair
            }
        }

        return null
    }

    private fun stripHtml(input: String): String {
        return input
            .replace(Regex("<[^>]*>"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun parsePubDateMillis(raw: String): Long? {
        val value = raw.trim()
        if (value.isBlank()) return null

        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss z",
            "EEE, dd MMM yyyy HH:mm Z",
            "EEE, dd MMM yyyy HH:mm z"
        )

        for (pattern in formats) {
            val formatter = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("GMT")
            }

            val position = ParsePosition(0)
            val parsed: Date? = formatter.parse(value, position)

            if (parsed != null && position.index == value.length) {
                return parsed.time
            }
        }

        return null
    }

    private fun formatPubDate(raw: String): String {
        val cleaned = raw.trim()
        if (cleaned.isBlank()) return "Recent"

        val parts = cleaned.split(" ")

        return if (parts.size >= 5) {
            "${parts[1]} ${parts[2]} • ${parts[4].take(5)}"
        } else {
            cleaned.take(16)
        }
    }

    companion object {
        private const val TEN_DAYS_MILLIS =
            10L * 24 * 60 * 60 * 1000

        private val targetRegex = Regex(
            "(?:target(?:\\s+price)?|tp)\\s*(?:of|at|to|:)?\\s*(?:rs\\.?|inr|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)",
            RegexOption.IGNORE_CASE
        )

        private val stopLossRegex = Regex(
            "(?:stop\\s*loss|stoploss|sl)\\s*(?:of|at|to|:)?\\s*(?:rs\\.?|inr|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)",
            RegexOption.IGNORE_CASE
        )

        private val entryRegex = Regex(
            "(?:buy|sell|entry|enter|cmp|around|near)\\s*(?:at|around|near|of|:)?\\s*(?:rs\\.?|inr|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)",
            RegexOption.IGNORE_CASE
        )

        val knownBrokerages = listOf(
            "Motilal Oswal",
            "SBI Securities",
            "Anand Rathi",
            "ICICI Direct",
            "ICICI Securities",
            "HDFC Securities",
            "Sharekhan",
            "Prabhudas Lilladher",
            "JM Financial",
            "Emkay Global",
            "Kotak Securities",
            "Nuvama",
            "Axis Securities",
            "Geojit",
            "Antique Stock Broking",
            "Elara Capital",
            "Choice Broking",
            "Jefferies",
            "Morgan Stanley",
            "Goldman Sachs",
            "Nomura",
            "CLSA",
            "Bernstein",
            "Citi",
            "UBS",
            "HSBC",
            "Macquarie"
        )

        val indianStockDictionary: Map<String, Pair<String, String>> =
            linkedMapOf(
                "reliance industries" to ("RELIANCE.NS" to "Reliance Industries"),
                "reliance" to ("RELIANCE.NS" to "Reliance Industries"),
                "tcs" to ("TCS.NS" to "Tata Consultancy Services"),
                "tata consultancy" to ("TCS.NS" to "Tata Consultancy Services"),
                "hdfc bank" to ("HDFCBANK.NS" to "HDFC Bank"),
                "icici bank" to ("ICICIBANK.NS" to "ICICI Bank"),
                "infosys" to ("INFY.NS" to "Infosys"),
                "state bank of india" to ("SBIN.NS" to "State Bank of India"),
                "sbi" to ("SBIN.NS" to "State Bank of India"),
                "bharti airtel" to ("BHARTIARTL.NS" to "Bharti Airtel"),
                "airtel" to ("BHARTIARTL.NS" to "Bharti Airtel"),
                "itc" to ("ITC.NS" to "ITC Ltd"),
                "larsen & toubro" to ("LT.NS" to "Larsen & Toubro"),
                "l&t" to ("LT.NS" to "Larsen & Toubro"),
                "hindustan unilever" to ("HINDUNILVR.NS" to "Hindustan Unilever"),
                "hul" to ("HINDUNILVR.NS" to "Hindustan Unilever"),
                "axis bank" to ("AXISBANK.NS" to "Axis Bank"),
                "kotak mahindra" to ("KOTAKBANK.NS" to "Kotak Mahindra Bank"),
                "kotak bank" to ("KOTAKBANK.NS" to "Kotak Mahindra Bank"),
                "bajaj finance" to ("BAJFINANCE.NS" to "Bajaj Finance"),
                "bajaj finserv" to ("BAJAJFINSV.NS" to "Bajaj Finserv"),
                "bajaj auto" to ("BAJAJ-AUTO.NS" to "Bajaj Auto"),
                "maruti suzuki" to ("MARUTI.NS" to "Maruti Suzuki"),
                "maruti" to ("MARUTI.NS" to "Maruti Suzuki"),
                "mahindra & mahindra" to ("M&M.NS" to "Mahindra & Mahindra"),
                "m&m" to ("M&M.NS" to "Mahindra & Mahindra"),
                "tata motors" to ("TATAMOTORS.NS" to "Tata Motors"),
                "tata steel" to ("TATASTEEL.NS" to "Tata Steel"),
                "tata power" to ("TATAPOWER.NS" to "Tata Power"),
                "tata consumer" to ("TATACONSUM.NS" to "Tata Consumer Products"),
                "sun pharma" to ("SUNPHARMA.NS" to "Sun Pharmaceutical"),
                "cipla" to ("CIPLA.NS" to "Cipla"),
                "dr reddy" to ("DRREDDY.NS" to "Dr. Reddy's Laboratories"),
                "titan" to ("TITAN.NS" to "Titan Company"),
                "ntpc" to ("NTPC.NS" to "NTPC Ltd"),
                "power grid" to ("POWERGRID.NS" to "Power Grid Corp"),
                "coal india" to ("COALINDIA.NS" to "Coal India"),
                "ongc" to ("ONGC.NS" to "ONGC"),
                "adani ports" to ("ADANIPORTS.NS" to "Adani Ports"),
                "adani enterprises" to ("ADANIENT.NS" to "Adani Enterprises"),
                "adani power" to ("ADANIPOWER.NS" to "Adani Power"),
                "ultratech cement" to ("ULTRACEMCO.NS" to "UltraTech Cement"),
                "grasim" to ("GRASIM.NS" to "Grasim Industries"),
                "jsw steel" to ("JSWSTEEL.NS" to "JSW Steel"),
                "hindalco" to ("HINDALCO.NS" to "Hindalco Industries"),
                "wipro" to ("WIPRO.NS" to "Wipro"),
                "hcl tech" to ("HCLTECH.NS" to "HCL Technologies"),
                "hcltech" to ("HCLTECH.NS" to "HCL Technologies"),
                "tech mahindra" to ("TECHM.NS" to "Tech Mahindra"),
                "asian paints" to ("ASIANPAINT.NS" to "Asian Paints"),
                "trent" to ("TRENT.NS" to "Trent Ltd"),
                "zomato" to ("ETERNAL.NS" to "Eternal (Zomato)"),
                "eternal" to ("ETERNAL.NS" to "Eternal (Zomato)"),
                "swiggy" to ("SWIGGY.NS" to "Swiggy"),
                "hindustan aeronautics" to ("HAL.NS" to "Hindustan Aeronautics"),
                "hal" to ("HAL.NS" to "Hindustan Aeronautics"),
                "bharat electronics" to ("BEL.NS" to "Bharat Electronics"),
                "bel" to ("BEL.NS" to "Bharat Electronics"),
                "mazagon dock" to ("MAZDOCK.NS" to "Mazagon Dock"),
                "indusind bank" to ("INDUSINDBK.NS" to "IndusInd Bank"),
                "bank of baroda" to ("BANKBARODA.NS" to "Bank of Baroda"),
                "pnb" to ("PNB.NS" to "Punjab National Bank"),
                "canara bank" to ("CANBK.NS" to "Canara Bank"),
                "interglobe aviation" to ("INDIGO.NS" to "IndiGo"),
                "indigo" to ("INDIGO.NS" to "IndiGo"),
                "dlf" to ("DLF.NS" to "DLF Ltd"),
                "varun beverages" to ("VBL.NS" to "Varun Beverages"),
                "vbl" to ("VBL.NS" to "Varun Beverages"),
                "avenue supermarts" to ("DMART.NS" to "DMart (Avenue Supermarts)"),
                "dmart" to ("DMART.NS" to "DMart (Avenue Supermarts)"),
                "pidilite" to ("PIDILITIND.NS" to "Pidilite Industries"),
                "nestle india" to ("NESTLEIND.NS" to "Nestle India"),
                "britannia" to ("BRITANNIA.NS" to "Britannia Industries"),
                "hero motocorp" to ("HEROMOTOCO.NS" to "Hero MotoCorp"),
                "eicher motors" to ("EICHERMOT.NS" to "Eicher Motors"),
                "tvs motor" to ("TVSMOTOR.NS" to "TVS Motor Company"),
                "vedanta" to ("VEDL.NS" to "Vedanta"),
                "rec" to ("RECLTD.NS" to "REC Ltd"),
                "pfc" to ("PFC.NS" to "Power Finance Corp"),
                "irctc" to ("IRCTC.NS" to "IRCTC"),
                "rvnl" to ("RVNL.NS" to "Rail Vikas Nigam"),
                "suzlon" to ("SUZLON.NS" to "Suzlon Energy"),
                "jio financial" to ("JIOFIN.NS" to "Jio Financial Services"),
                "apollo hospitals" to ("APOLLOHOSP.NS" to "Apollo Hospitals"),
                "divis lab" to ("DIVISLAB.NS" to "Divi's Laboratories"),
                "lupin" to ("LUPIN.NS" to "Lupin"),
                "aurobindo pharma" to ("AUROPHARMA.NS" to "Aurobindo Pharma"),
                "shriram finance" to ("SHRIRAMFIN.NS" to "Shriram Finance"),
                "chola" to ("CHOLAFIN.NS" to "Cholamandalam Inv"),
                "bhel" to ("BHEL.NS" to "BHEL"),
                "gail" to ("GAIL.NS" to "GAIL India"),
                "ioc" to ("IOC.NS" to "Indian Oil Corp"),
                "bpcl" to ("BPCL.NS" to "BPCL"),
                "hpcl" to ("HINDPETRO.NS" to "HPCL"),
                "polycab" to ("POLYCAB.NS" to "Polycab India"),
                "havells" to ("HAVELLS.NS" to "Havells India"),
                "dixon" to ("DIXON.NS" to "Dixon Technologies"),
                "persistent" to ("PERSISTENT.NS" to "Persistent Systems"),
                "coforge" to ("COFORGE.NS" to "Coforge"),
                "kpit" to ("KPITTECH.NS" to "KPIT Technologies"),
                "federal bank" to ("FEDERALBNK.NS" to "Federal Bank"),
                "idfc first" to ("IDFCFIRSTB.NS" to "IDFC First Bank"),
                "vodafone idea" to ("IDEA.NS" to "Vodafone Idea")
            )
    }
}
