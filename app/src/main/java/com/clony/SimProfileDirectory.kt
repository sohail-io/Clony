package com.clony

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class OnlineSimProfileSample(
    val phoneNumber: String,
    val carrierName: String,
    val countryIso: String,
    val operatorCode: String
)

/** Fetches UAE carrier metadata from the public MCC-MNC directory. */
internal object SimProfileDirectory {
    private const val UAE_NETWORKS_URL =
        "https://mcc-mnc.org/api/v1/networks?country=AE&limit=50"

    fun fetchUaeSample(): OnlineSimProfileSample {
        val connection = URL(UAE_NETWORKS_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Accept", "application/json")
        try {
            val status = connection.responseCode
            if (status !in 200..299) error("Carrier directory returned HTTP $status.")
            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            val networks = JSONObject(payload).getJSONArray("data")
            val selected = (0 until networks.length())
                .map { networks.getJSONObject(it) }
                .firstOrNull { it.optString("brand").equals("du", ignoreCase = true) }
                ?: (0 until networks.length())
                    .map { networks.getJSONObject(it) }
                    .firstOrNull { it.optString("brand").contains("etisalat", ignoreCase = true) }
                ?: error("No UAE carrier records were returned.")

            val country = selected.optJSONObject("country")?.optString("iso")
                ?.takeIf(String::isNotBlank) ?: "AE"
            val operatorCode = selected.optString("mcc") + selected.optString("mnc").padStart(2, '0')
            val carrier = selected.optString("brand")
                .takeIf(String::isNotBlank)
                ?: selected.optString("operator").takeIf(String::isNotBlank)
                ?: error("The carrier directory record has no operator name.")
            if (operatorCode.length !in 5..6 || operatorCode.any { !it.isDigit() }) {
                error("The carrier directory returned an invalid operator code.")
            }

            return OnlineSimProfileSample(
                phoneNumber = "+971000000000",
                carrierName = carrier,
                countryIso = country.lowercase(),
                operatorCode = operatorCode
            )
        } finally {
            connection.disconnect()
        }
    }
}
