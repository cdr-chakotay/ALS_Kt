/*
 * ALS_Kt - A Kotlin Library for Apple's Location Services
 * Copyright: Florian Kuenzig - 2026
 * Incorporates work from GrapheneOS NetworkLocation (MIT License)
 * https://github.com/GrapheneOS/platform_packages_apps_NetworkLocation
 * This file is released under the MIT License.
 */

package org.kufl.als_kt

/**
 * The five mobile generations understood by Apple's location service.
 */
enum class ALSTechnology {
    GSM,
    WCDMA,
    TD_SCDMA,
    LTE,
    NR;

    companion object {
        /**
         * Parse a technology string (case-insensitive). Unknown values default to LTE.
         * Accepts the following aliases:
         *  - "2G" -> GSM
         *  - "3G" -> WCDMA
         * - "UMTS" -> WCDMA
         * - "SCDMA" -> TD_SCDMA
         * - "4G" -> LTE
         * - "5G" -> NR
         * @return The corresponding [ALSTechnology], or LTE if the string is unknown.
         */
        fun from(value: String): ALSTechnology = when (value.uppercase()) {
            "UMTS" -> WCDMA
            "SCDMA" -> TD_SCDMA
            "2G" -> GSM
            "3G" -> WCDMA
            "4G" -> LTE
            "5G" -> NR
            else -> entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: LTE
        }
    }
}
