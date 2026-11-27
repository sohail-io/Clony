# SIM profile catalog

The app bundles its SIM profile catalog in `SimProfileDirectory.kt`; choosing a
profile works offline. Each entry records the subscriber evidence, its reporting
period, the ranking source, the MCC/MNC reference, and the review date.

## Supported countries

The reviewed catalog currently covers Bangladesh, Brazil, China, Ethiopia,
France, Germany, India, Indonesia, Iran, Japan, Mexico, Nigeria, Pakistan,
Russia, Turkey, the United Arab Emirates, and the United States. The picker
lists only these countries. Add a country only after a leading-carrier ranking
and a valid representative PLMN can both be sourced.

The global ranking entries use TeleGeography's June 2026 largest-operator
table. Germany, France, Russia, and Turkey use MCC-MNC.dev's Europe 2026
ranking. The UAE entry uses Etisalat's FY2025 reporting: 14.57 million active
mobile subscribers and a 60.0% mobile subscriber share as of 2025-12-31. The
picker uses the operator's current Etisalat name. Ranking periods are saved on
each entry; they are not implied to be identical.

The catalog selects one representative PLMN for each carrier. A carrier may
have multiple PLMNs, and some carriers use regional codes. For example, India's
Jio entry uses the Jio PLMN `405854`.

## Attribution and data license

MCC/MNC reference data is adapted from MCC-MNC.dev's manually reviewed
registry, which is released under Creative Commons Attribution-ShareAlike 4.0
International (CC BY-SA 4.0). The MCC/MNC-derived catalog data in
`SimProfileDirectory.kt` is available under the same license. Attribution:

> Data: MCC-MNC.dev (https://mcc-mnc.dev) — CC BY-SA 4.0

The subscriber figures in the catalog are factual ranking data with source and
reporting period noted on each entry. TeleGeography's source table is available
at <https://resources.telegeography.com/mobile-monsters-the-worlds-biggest-operators>.

Sources:

- [MCC-MNC.dev data methodology and license](https://mcc-mnc.dev/about-data)
- [MCC-MNC.dev Europe operator ranking](https://mcc-mnc.dev/insights/largest-mobile-operator-europe-2026)
- [TeleGeography global operator ranking](https://resources.telegeography.com/mobile-monsters-the-worlds-biggest-operators)
- [e& FY2025 UAE subscriber and market-share reporting](https://ise-prodnr-eu-west-1-data-integration.s3-eu-west-1.amazonaws.com/202603/bed5be39-293a-4ea5-b880-4ed20c2c0279.pdf)
- [Etisalat's current group identity](https://www.eand.com/en/news/27-10-26-etisalat-record-9m-2026-performance.html)

Catalog review date: 2026-11-27.
