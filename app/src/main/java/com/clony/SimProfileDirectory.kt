package com.clony

import java.util.Locale

/**
 * Bundled, reviewed SIM profiles. Carrier ranking and PLMN sources are stored
 * on each entry so updates can be checked without a runtime network request.
 * The MCC/MNC-derived catalog data is attributed in docs/sim-profile-catalog.md.
 */
internal data class SimCarrierProfile(
    val countryIso: String,
    val countryName: String,
    val carrierName: String,
    val operatorCode: String,
    val subscriberEvidence: String,
    val rankingAsOf: String,
    val rankingSourceUrl: String,
    val operatorCodeSourceUrl: String,
    val reviewedOn: String
)

internal object SimProfileDirectory {
    private const val REVIEWED_ON = "2026-11-27"
    private val isoCountryCodes = Locale.getISOCountries()
        .map { it.lowercase(Locale.ROOT) }
        .toSet()
    private const val GLOBAL_RANKING_SOURCE =
        "https://resources.telegeography.com/mobile-monsters-the-worlds-biggest-operators"
    private const val EUROPE_RANKING_SOURCE =
        "https://mcc-mnc.dev/insights/largest-mobile-operator-europe-2026"
    private const val UAE_RANKING_SOURCE =
        "https://ise-prodnr-eu-west-1-data-integration.s3-eu-west-1.amazonaws.com/202603/bed5be39-293a-4ea5-b880-4ed20c2c0279.pdf"

    private fun entry(
        iso: String,
        country: String,
        carrier: String,
        plmn: String,
        evidence: String,
        rankingAsOf: String,
        rankingSource: String,
        countrySlug: String
    ) = SimCarrierProfile(
        countryIso = iso.lowercase(Locale.ROOT),
        countryName = country,
        carrierName = carrier,
        operatorCode = plmn,
        subscriberEvidence = evidence,
        rankingAsOf = rankingAsOf,
        rankingSourceUrl = rankingSource,
        operatorCodeSourceUrl = "https://mcc-mnc.dev/country/$countrySlug",
        reviewedOn = REVIEWED_ON
    )

    private val profiles = listOf(
        entry("bd", "Bangladesh", "Grameenphone", "47001", "81,261,017 subscriptions", "2026-06", GLOBAL_RANKING_SOURCE, "bangladesh"),
        entry("br", "Brazil", "Vivo", "72406", "85,361,324 subscriptions", "2026-06", GLOBAL_RANKING_SOURCE, "brazil"),
        entry("cn", "China", "China Mobile", "46000", "1,011,046,000 subscriptions", "2026-06", GLOBAL_RANKING_SOURCE, "china"),
        entry("de", "Germany", "Telekom", "26201", "75.32 million subscriptions", "2026-Q1", EUROPE_RANKING_SOURCE, "germany"),
        entry("et", "Ethiopia", "Ethio Telecom", "63601", "86,910,000 subscriptions", "2026-06", GLOBAL_RANKING_SOURCE, "ethiopia"),
        entry("fr", "France", "Orange", "20801", "27.5 million subscriptions", "2025-Q2", EUROPE_RANKING_SOURCE, "france"),
        entry("in", "India", "Jio", "405854", "477,823,616 subscriptions", "2026-06", GLOBAL_RANKING_SOURCE, "india"),
        entry("id", "Indonesia", "Telkomsel", "51010", "153,500,000 subscriptions", "2026-06", GLOBAL_RANKING_SOURCE, "indonesia"),
        entry("ir", "Iran", "MCI (Hamrah-e-Avval)", "43211", "79,000,000 subscriptions", "2026-06", GLOBAL_RANKING_SOURCE, "iran"),
        entry("jp", "Japan", "NTT DOCOMO", "44010", "89,673,000 subscriptions", "2026-06", GLOBAL_RANKING_SOURCE, "japan"),
        entry("mx", "Mexico", "Telcel", "334020", "84,531,000 subscriptions", "2026-06", GLOBAL_RANKING_SOURCE, "mexico"),
        entry("ng", "Nigeria", "MTN Nigeria", "62130", "92,208,000 subscriptions", "2026-06", GLOBAL_RANKING_SOURCE, "nigeria"),
        entry("pk", "Pakistan", "Jazz", "41001", "75,421,212 subscriptions", "2026-06", GLOBAL_RANKING_SOURCE, "pakistan"),
        entry("ru", "Russia", "MTS", "25001", "83.4 million subscriptions", "2025-Q4", EUROPE_RANKING_SOURCE, "russia"),
        entry("tr", "Turkey", "Turkcell", "28601", "38.3 million subscriptions", "2026-08", EUROPE_RANKING_SOURCE, "turkey"),
        entry("ae", "United Arab Emirates", "Etisalat", "42402", "14.57 million active mobile subscribers; 60.0% subscriber share", "2025-12-31", UAE_RANKING_SOURCE, "united-arab-emirates"),
        entry("us", "United States", "T-Mobile", "310120", "165,541,000 subscriptions", "2026-06", GLOBAL_RANKING_SOURCE, "united-states")
    ).sortedBy(SimCarrierProfile::countryName)

    fun allProfiles(): List<SimCarrierProfile> = profiles

    fun findByIso(countryIso: String): SimCarrierProfile? {
        val normalized = countryIso.trim().lowercase(Locale.ROOT)
        return profiles.firstOrNull { it.countryIso == normalized }
    }

    fun isComplete(profile: SimCarrierProfile): Boolean =
        profile.countryIso in isoCountryCodes &&
            profile.countryName.isNotBlank() &&
            profile.carrierName.isNotBlank() &&
            profile.operatorCode.matches(Regex("[0-9]{5,6}")) &&
            profile.subscriberEvidence.isNotBlank() &&
            profile.rankingAsOf.isNotBlank() &&
            profile.rankingSourceUrl.startsWith("https://") &&
            profile.operatorCodeSourceUrl.startsWith("https://") &&
            profile.reviewedOn.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
}
