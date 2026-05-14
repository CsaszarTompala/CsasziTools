package com.example.moneysplitter.logic

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object ReceiptScanner {

    private const val TAG = "ReceiptScanner"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS) // reasoning models can be slow
        .build()

    private val gson = Gson()

    data class ReceiptItem(
        val name: String,
        val amount: Double,
        val currency: String
    )

    data class ScanResult(
        val items: List<ReceiptItem>,
        val error: String? = null
    )

    private data class ParsedResult(
        @SerializedName("items") val items: List<ParsedItem>?,
        @SerializedName("total") val total: Double?,
        @SerializedName("error") val error: String?
    )

    private data class ParsedItem(
        @SerializedName("name") val name: String?,
        @SerializedName("amount") val amount: Double?,
        @SerializedName("currency") val currency: String?
    )

    fun scan(imageBytes: ByteArray, apiKey: String, defaultCurrency: String): ScanResult {
        val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

        // First pass
        val firstRaw = callApi(base64Image, apiKey, defaultCurrency, retryContext = null)
        if (firstRaw.error != null && firstRaw.parsed == null) {
            return ScanResult(emptyList(), firstRaw.error)
        }

        val items = parseItems(firstRaw.parsed, defaultCurrency)
        if (items.isEmpty()) {
            return ScanResult(emptyList(), firstRaw.error ?: "No items detected on receipt")
        }

        val total = firstRaw.parsed?.total

        // If receipt had a printed total and our sum doesn't match, retry once
        if (total != null && total > 0) {
            val sum = items.sumOf { it.amount }
            if (abs(sum - total) > 1.0) {
                Log.d(TAG, "Total mismatch: items sum=${"%.2f".format(sum)}, receipt total=${"%.2f".format(total)}. Retrying...")
                val retryContext = "Your previous extraction had ${items.size} items summing to ${"%.2f".format(sum)}, but the receipt total is ${"%.2f".format(total)}. You are missing items or have wrong amounts. Re-examine the receipt carefully and extract ALL items so the sum matches the total."
                val retryRaw = callApi(base64Image, apiKey, defaultCurrency, retryContext = retryContext)
                val retryItems = parseItems(retryRaw.parsed, defaultCurrency)
                if (retryItems.isNotEmpty()) {
                    return ScanResult(retryItems)
                }
                // Fall back to first result if retry failed
            }
        }

        return ScanResult(items)
    }

    private data class RawResult(
        val parsed: ParsedResult?,
        val error: String?
    )

    private fun callApi(base64Image: String, apiKey: String, defaultCurrency: String, retryContext: String?): RawResult {
        val retryLine = if (retryContext != null) "\n\nCORRECTION NEEDED: $retryContext" else ""

        val prompt = """You are an expert receipt OCR system. Extract EVERY SINGLE printed line item from this receipt.

RULES:
1. Return ONLY a JSON object, no markdown fences, no explanation, no reasoning text
2. Format: {"items":[{"name":"English name","amount":123.45,"currency":"HUF"}],"total":1234.56}
3. "total" = the grand total / sum printed on the receipt (e.g. after "ÖSSZESEN", "TOTAL", "GESAMT"). Set to null if no total is visible
4. Extract EVERY line that has a price — even small fees, deposits, refunds, surcharges
5. Deposit/return fees (e.g. "Visszav.díj", "bottle deposit", "pfand") are SEPARATE items — extract each one individually
6. If the same product appears multiple times as separate lines, list each occurrence as a SEPARATE item — do NOT merge them
7. Translate item names to English
8. Use the receipt's currency. If unclear, use "$defaultCurrency"
9. Do NOT include totals, subtotals, tax summaries, or payment method lines as items
10. Ignore any handwritten text — only extract printed text
11. VERIFY: the sum of all item amounts should equal the total. If it doesn't, you missed items — go back and re-read

If unreadable: {"items":[],"total":null,"error":"Could not read receipt"}$retryLine"""

        val requestBody = """
{
  "model": "o4-mini",
  "messages": [
    {
      "role": "user",
      "content": [
        {"type": "text", "text": ${gson.toJson(prompt)}},
        {"type": "image_url", "image_url": {"url": "data:image/png;base64,$base64Image", "detail": "high"}}
      ]
    }
  ],
  "max_completion_tokens": 16384
}""".trimIndent()

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (body.isNullOrBlank()) {
                return RawResult(null, "Empty response from API")
            }

            Log.d(TAG, "API response code: ${response.code}, body length: ${body.length}")

            if (!response.isSuccessful) {
                val errorMsg = try {
                    val obj = JsonParser.parseString(body).asJsonObject
                    obj.getAsJsonObject("error")?.get("message")?.asString
                } catch (_: Exception) { null }
                return RawResult(null, "API error ${response.code}: ${errorMsg ?: body.take(200)}")
            }

            // Parse the response — handle both standard and reasoning model formats
            val content = extractContent(body)
            if (content == null) {
                Log.e(TAG, "Could not extract content from response: ${body.take(500)}")
                return RawResult(null, "Could not parse API response. Raw: ${body.take(300)}")
            }

            Log.d(TAG, "Extracted content: ${content.take(500)}")

            // Extract JSON from content (may have markdown fences or extra text)
            val jsonStr = extractJson(content)
            if (jsonStr == null) {
                Log.e(TAG, "Could not find JSON in content: ${content.take(500)}")
                return RawResult(null, "Model returned non-JSON response: ${content.take(300)}")
            }

            val parsed = try {
                gson.fromJson(jsonStr, ParsedResult::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "JSON parse error: ${e.message}, json: ${jsonStr.take(500)}")
                return RawResult(null, "Failed to parse items: ${e.message}")
            }

            if (parsed?.error != null) {
                return RawResult(parsed, parsed.error)
            }

            if (parsed?.items == null || parsed.items.isEmpty()) {
                return RawResult(null, "Model returned empty items list")
            }

            RawResult(parsed, null)

        } catch (e: SocketTimeoutException) {
            RawResult(null, "Request timed out (receipt analysis took too long). Try again or use a clearer photo.")
        } catch (e: Exception) {
            Log.e(TAG, "Scan error", e)
            RawResult(null, "Error: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /**
     * Extract the text content from an OpenAI API response.
     * Handles both standard models (choices[0].message.content as string)
     * and reasoning models (choices[0].message.content as array of content parts).
     */
    private fun extractContent(responseBody: String): String? {
        return try {
            val root = JsonParser.parseString(responseBody).asJsonObject
            val choices = root.getAsJsonArray("choices")
            if (choices == null || choices.isEmpty) return null

            val message = choices[0].asJsonObject.getAsJsonObject("message") ?: return null

            // Try content as plain string first
            val contentElement = message.get("content")
            if (contentElement != null && contentElement.isJsonPrimitive) {
                return contentElement.asString
            }

            // Try content as array (reasoning models may return array of content parts)
            if (contentElement != null && contentElement.isJsonArray) {
                val parts = contentElement.asJsonArray
                val textParts = mutableListOf<String>()
                for (part in parts) {
                    val obj = part.asJsonObject
                    if (obj.get("type")?.asString == "text") {
                        textParts.add(obj.get("text").asString)
                    }
                }
                if (textParts.isNotEmpty()) return textParts.joinToString("\n")
            }

            // Some models use output_text at top level
            val outputText = root.get("output_text")
            if (outputText != null && outputText.isJsonPrimitive) {
                return outputText.asString
            }

            // Try output array (newer response format)
            val output = root.getAsJsonArray("output")
            if (output != null) {
                for (item in output) {
                    val obj = item.asJsonObject
                    if (obj.get("type")?.asString == "message") {
                        val msgContent = obj.getAsJsonArray("content")
                        if (msgContent != null) {
                            for (part in msgContent) {
                                val partObj = part.asJsonObject
                                if (partObj.get("type")?.asString == "output_text" || partObj.get("type")?.asString == "text") {
                                    return partObj.get("text")?.asString
                                }
                            }
                        }
                    }
                }
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "extractContent failed", e)
            null
        }
    }

    /**
     * Find and extract JSON object from a string that may contain markdown fences or extra text.
     */
    private fun extractJson(content: String): String? {
        // Strip markdown fences
        var cleaned = content
            .replace(Regex("```json\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("```\\s*", RegexOption.MULTILINE), "")
            .trim()

        // If it starts with {, try it directly
        if (cleaned.startsWith("{")) return cleaned

        // Try to find a JSON object in the text
        val braceStart = cleaned.indexOf('{')
        if (braceStart >= 0) {
            val braceEnd = cleaned.lastIndexOf('}')
            if (braceEnd > braceStart) {
                return cleaned.substring(braceStart, braceEnd + 1)
            }
        }

        return null
    }

    private fun parseItems(parsed: ParsedResult?, defaultCurrency: String): List<ReceiptItem> {
        if (parsed?.items == null || parsed.items.isEmpty()) return emptyList()
        return parsed.items.mapNotNull { item ->
            val name = item.name?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val amount = item.amount?.takeIf { it > 0 } ?: return@mapNotNull null
            val currency = item.currency?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
                ?: defaultCurrency
            ReceiptItem(name, amount, currency)
        }
    }
}
