package com.splitmypay.app.data.parser

data class ParsedPayment(
    val merchant: String,
    val amount: Double,
    val currency: String
)

object NotificationPaymentParser {

    private val GENERIC_TITLES = setOf(
        "payment successful",
        "payment approved",
        "pago realizado",
        "pago completado",
        "pago con tarjeta",
        "compra realizada",
        "google wallet",
        "google pay",
        "wallet",
        "transaction alert",
        "card transaction",
        "transacción",
        "tarjeta",
        "notificación de pago"
    )

    private val CURRENCY_MAP = mapOf(
        "€" to "EUR",
        "EUR" to "EUR",
        "$" to "USD",
        "USD" to "USD",
        "£" to "GBP",
        "GBP" to "GBP",
        "¥" to "JPY",
        "JPY" to "JPY",
        "₹" to "INR",
        "INR" to "INR",
        "CHF" to "CHF",
        "CAD" to "CAD",
        "AUD" to "AUD"
    )

    private val AMOUNT_WITH_CURRENCY_REGEX = Regex(
        """(?:(?<currPref>[\$€£¥₹]|EUR|USD|GBP|CHF|CAD|AUD)\s*)?(?<amount>\d{1,3}(?:[.,]\d{3})*(?:[.,]\d{1,2})|\d+[.,]\d{1,2})(?:\s*(?<currSuff>[\$€£¥₹]|EUR|USD|GBP|CHF|CAD|AUD))?""",
        RegexOption.IGNORE_CASE
    )

    // Pattern: "Merchant • 18,45 €" or "18,45 € • Merchant"
    private val BULLET_PATTERN = Regex(
        """(?<part1>[^•\n]+?)\s*•\s*(?<part2>[^•\n]+)"""
    )

    // Pattern: "Paid/Pago de €12.34 to/en Merchant" or "Spent $12 at Merchant"
    private val PAID_TO_PATTERN = Regex(
        """(?:paid|pago de|pago en|spent|gasto de|purchase of|compra de)\s+(?<curr1>[\$€£¥₹]|EUR|USD|GBP)?\s*(?<amount>\d+[.,]\d{2})\s*(?<curr2>[\$€£¥₹]|EUR|USD|GBP)?\s+(?:to|en|at|a)\s+(?<merchant>.+)""",
        RegexOption.IGNORE_CASE
    )

    // Pattern: "12.34 EUR at/en Merchant"
    private val AMOUNT_AT_PATTERN = Regex(
        """(?<amount>\d+[.,]\d{2})\s*(?<curr>[\$€£¥₹]|EUR|USD|GBP)\s+(?:at|en|to|a)\s+(?<merchant>.+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Parses notification title and text to extract payment details.
     * Returns null if no valid payment is identified.
     */
    fun parse(title: String?, text: String?): ParsedPayment? {
        val cleanTitle = title?.trim().orEmpty()
        val cleanText = text?.trim().orEmpty()

        if (cleanTitle.isEmpty() && cleanText.isEmpty()) return null

        val fullText = "$cleanTitle $cleanText".trim()

        // 1. Check Paid to / at pattern
        PAID_TO_PATTERN.find(fullText)?.let { match ->
            val currStr = match.groups["curr1"]?.value ?: match.groups["curr2"]?.value ?: "EUR"
            val amtStr = match.groups["amount"]?.value ?: return@let
            val merchStr = match.groups["merchant"]?.value ?: return@let
            val parsedAmt = parseAmount(amtStr) ?: return@let
            val currency = normalizeCurrency(currStr)
            val merchant = cleanMerchant(merchStr)
            if (merchant.isNotEmpty() && parsedAmt > 0.0) {
                return ParsedPayment(merchant = merchant, amount = parsedAmt, currency = currency)
            }
        }

        // 2. Check Amount at pattern
        AMOUNT_AT_PATTERN.find(fullText)?.let { match ->
            val currStr = match.groups["curr"]?.value ?: "EUR"
            val amtStr = match.groups["amount"]?.value ?: return@let
            val merchStr = match.groups["merchant"]?.value ?: return@let
            val parsedAmt = parseAmount(amtStr) ?: return@let
            val currency = normalizeCurrency(currStr)
            val merchant = cleanMerchant(merchStr)
            if (merchant.isNotEmpty() && parsedAmt > 0.0) {
                return ParsedPayment(merchant = merchant, amount = parsedAmt, currency = currency)
            }
        }

        // 3. Check Bullet pattern ("Mercadona • 18,45 €" or "18,45 € • Mercadona")
        val bulletMatch = BULLET_PATTERN.find(cleanText) ?: BULLET_PATTERN.find(cleanTitle)
        if (bulletMatch != null) {
            val part1 = bulletMatch.groups["part1"]?.value?.trim().orEmpty()
            val part2 = bulletMatch.groups["part2"]?.value?.trim().orEmpty()

            val amt1 = extractAmountAndCurrency(part1)
            val amt2 = extractAmountAndCurrency(part2)

            if (amt1 != null && amt2 == null) {
                val merchant = cleanMerchant(part2)
                if (merchant.isNotEmpty()) {
                    return ParsedPayment(merchant = merchant, amount = amt1.first, currency = amt1.second)
                }
            } else if (amt2 != null && amt1 == null) {
                val merchant = cleanMerchant(part1)
                if (merchant.isNotEmpty()) {
                    return ParsedPayment(merchant = merchant, amount = amt2.first, currency = amt2.second)
                }
            }
        }

        // 4. Check if Title is Merchant and Text contains Amount (e.g. Title: "Starbucks", Text: "$4.50 with Google Pay")
        val titleIsGeneric = isGenericTitle(cleanTitle)
        val textAmt = extractAmountAndCurrency(cleanText)
        if (textAmt != null && cleanTitle.isNotEmpty() && !titleIsGeneric) {
            val merchant = cleanMerchant(cleanTitle)
            if (merchant.isNotEmpty()) {
                return ParsedPayment(merchant = merchant, amount = textAmt.first, currency = textAmt.second)
            }
        }

        // 5. Check if Title contains Amount and Text contains Merchant
        val titleAmt = extractAmountAndCurrency(cleanTitle)
        if (titleAmt != null && cleanText.isNotEmpty()) {
            val merchant = cleanMerchant(cleanText)
            if (merchant.isNotEmpty()) {
                return ParsedPayment(merchant = merchant, amount = titleAmt.first, currency = titleAmt.second)
            }
        }

        // 6. Fallback: Search for amount in full text, and pick whatever non-generic string remains
        if (textAmt != null) {
            val candidateMerchant = if (!titleIsGeneric && cleanTitle.isNotEmpty()) {
                cleanTitle
            } else {
                cleanText.replace(AMOUNT_WITH_CURRENCY_REGEX, "").trim()
            }
            val merchant = cleanMerchant(candidateMerchant)
            if (merchant.isNotEmpty()) {
                return ParsedPayment(merchant = merchant, amount = textAmt.first, currency = textAmt.second)
            }
        }

        return null
    }

    fun isGenericTitle(title: String): Boolean {
        val lower = title.lowercase().trim()
        return GENERIC_TITLES.any { lower == it || lower.startsWith(it) }
    }

    fun normalizeCurrency(raw: String?): String {
        if (raw == null) return "EUR"
        val trimmed = raw.trim().uppercase()
        return CURRENCY_MAP[trimmed] ?: CURRENCY_MAP[raw.trim()] ?: if (trimmed.length == 3) trimmed else "EUR"
    }

    fun parseAmount(amountStr: String): Double? {
        val clean = amountStr.trim().replace(" ", "")
        return try {
            if (clean.contains(",") && clean.contains(".")) {
                // Determine which is decimal: the one appearing last
                if (clean.lastIndexOf(',') > clean.lastIndexOf('.')) {
                    // 1.234,56
                    clean.replace(".", "").replace(",", ".").toDoubleOrNull()
                } else {
                    // 1,234.56
                    clean.replace(",", "").toDoubleOrNull()
                }
            } else if (clean.contains(",")) {
                clean.replace(",", ".").toDoubleOrNull()
            } else {
                clean.toDoubleOrNull()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractAmountAndCurrency(text: String): Pair<Double, String>? {
        for (match in AMOUNT_WITH_CURRENCY_REGEX.findAll(text)) {
            val amtStr = match.groups["amount"]?.value ?: continue
            val pref = match.groups["currPref"]?.value
            val suff = match.groups["currSuff"]?.value
            val currRaw = pref ?: suff

            // Only treat as amount if currency is present OR string has decimal places
            if (currRaw != null || amtStr.contains(".") || amtStr.contains(",")) {
                val parsedAmt = parseAmount(amtStr) ?: continue
                if (parsedAmt > 0.0) {
                    val currency = normalizeCurrency(currRaw)
                    return Pair(parsedAmt, currency)
                }
            }
        }
        return null
    }

    fun cleanMerchant(raw: String): String {
        var result = raw.trim()

        // Strip prefixes
        val prefixes = listOf(
            "paid to", "paid at", "pago en", "pago de", "compra en", "compra de",
            "spent at", "en", "at", "to", "a", "payment to"
        )
        for (prefix in prefixes) {
            if (result.lowercase().startsWith("$prefix ")) {
                result = result.substring(prefix.length).trim()
            }
        }

        // Strip suffixes like "with Google Pay", "with Visa", "con tarjeta", "tarjeta terminada en..."
        val suffixes = listOf(
            "with google pay", "con google pay", "via google pay", "con tarjeta",
            "with visa", "with mastercard", "con visa", "con mastercard"
        )
        for (suffix in suffixes) {
            val idx = result.lowercase().indexOf(suffix)
            if (idx > 0) {
                result = result.substring(0, idx).trim()
            }
        }

        // Strip card suffix patterns e.g. "Tarjeta ...", "Card ..."
        result = result.replace(Regex("""(?i)\s*(?:tarjeta|card)\s*(?:terminada|ending)?\s*(?:en)?\s*\d+.*"""), "").trim()

        // Strip trailing dots, commas, dashes, bullets
        result = result.trimEnd('.', ',', '-', '•', ':', ';', ' ')

        return result
    }
}
