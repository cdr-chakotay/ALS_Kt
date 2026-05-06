/*
 * ALS_Kt - A Kotlin Library for Apple's Location Services
 * Copyright: Florian Kuenzig - 2026
 * Incorporates work from GrapheneOS NetworkLocation (MIT License)
 * https://github.com/GrapheneOS/platform_packages_apps_NetworkLocation
 * This file is released under the MIT License.
 */

package org.kufl.als_kt

import java.time.Duration
import kotlin.system.measureTimeMillis

/**
 * CLI client so the ALS lookup can be made without building an whole app around this library.
 */
object ALSCli {

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty() || args.contains("--help")) {
            printUsage()
            return
        }

        val config = try {
            parseConfig(args)
        } catch (ex: IllegalArgumentException) {
            System.err.println("Invalid arguments: ${ex.message}")
            printUsage()
            return
        }

        val client = ALSClient(timeout = config.timeout)
        val origin = ALSQueryCell(
            technology = config.technology,
            country = config.mcc,
            network = config.mnc,
            area = config.area,
            cellId = config.cell,
            location = null,
        )

        try {
            var cells: List<ALSQueryCell> = emptyList()
            val elapsedMs = measureTimeMillis {
                cells = client.requestCells(origin)
            }
            val validCells = cells.count { it.isValid() }
            println("ALS returned ${cells.size} cell(s) of which ${validCells} are valid. Lookup took ${elapsedMs} ms.")
            cells.forEachIndexed { index, cell -> println("[$index] $cell") }
        } catch (ex: ALSClientException) {
            System.err.println("ALS error: ${ex.message}")
        } catch (ex: Exception) {
            System.err.println("Unexpected error: ${ex.message}")
            ex.printStackTrace(System.err)
        }
    }

    /**
     * Parses the provided command-line arguments to create a CliConfig object.
     *
     * @param args An array of strings representing the command-line arguments.
     * The values for "tech", "mcc", "mnc", "area", and "cell" are mandatory.
     * An optional "timeoutMs" flag can be used to define the timeout duration in milliseconds.
     *
     * @return A CliConfig object containing the parsed configuration.
     *
     * @throws IllegalArgumentException If a flag does not start with "--",
     * if a mandatory flag is missing, or if a flag's value cannot be parsed
     * to the required type (e.g., integer or long).
     */
    private fun parseConfig(args: Array<String>): CliConfig {
        val tokens = mutableMapOf<String, String>()
        var index = 0
        while (index < args.size) {
            val key = args[index]
            if (!key.startsWith("--")) {
                throw IllegalArgumentException("Flags have to start with -- (found '$key')")
            }
            if (index + 1 >= args.size) {
                throw IllegalArgumentException("Missing value for flag '$key'")
            }
            tokens[key.removePrefix("--")] = args[index + 1]
            index += 2
        }

        val technology = ALSTechnology.from(tokens.require("tech"))
        val mcc = tokens.requireInt("mcc")
        val mnc = tokens.requireInt("mnc")
        val area = tokens.requireInt("area")
        val cell = tokens.requireLong("cell")
        val timeout = tokens["timeoutMs"]?.toLongOrNull()?.let(Duration::ofMillis)

        return CliConfig(
            technology = technology,
            mcc = mcc,
            mnc = mnc,
            area = area,
            cell = cell,
            timeout = timeout
        )
    }

    private fun Map<String, String>.require(key: String): String =
        this[key] ?: throw IllegalArgumentException("Missing --$key")

    private fun Map<String, String>.requireInt(key: String): Int =
        this[key]?.toIntOrNull() ?: throw IllegalArgumentException("Flag --$key expects an integer")

    private fun Map<String, String>.requireLong(key: String): Long =
        this[key]?.toLongOrNull() ?: throw IllegalArgumentException("Flag --$key expects a long value")

    private fun Map<String, String>.optionalInt(key: String): Int? =
        this[key]?.toIntOrNull()

    /**
     * Prints a help text on how to use the CLI.
     */
    private fun printUsage() {
        println(
            """
            Apple Location Service CLI
            Required flags:
              --tech <GSM|WCDMA|TD_SCDMA|LTE|NR>      Radio technology used for the origin cell.
              --mcc <int>                             Mobile country code.
              --mnc <int>                             Mobile network code.
              --area <int>                            LAC/TAC depending on technology.
              --cell <long>                           Cell identifier (eNodeB, NR cell ID, ...).

            Optional flags:
              --timeoutMs <long>
              
            Note on WCDMA:                            WCDMA uses the GSM entity internally; returned cells
                                                      are classified as WCDMA when their cell ID exceeds 16 bits.
                                                      This is a heuristic and might fail for WCDMA cells with RNC=0. 

            Example:
              --tech LTE --mcc 262 --mnc 1 --area 1492 --cell 28468992 --timeoutMs 5000
            """.trimIndent()
        )
    }


    /**
     * Configuration class for specifying parameters for cellular location identification.
     *
     * @property technology The mobile network technology standard being used.
     * @property mcc Mobile Country Code that identifies the country of the network.
     * @property mnc Mobile Network Code that identifies the specific network within the country.
     * @property area The location area code, which represents a specific geographical area within the network.
     * @property cell The cell identifier that uniquely identifies a specific cell tower within the network.
     * @property timeout The timeout duration for any operation relying on this configuration, if applicable.
     */
    private data class CliConfig(
        val technology: ALSTechnology,
        val mcc: Int,
        val mnc: Int,
        val area: Int,
        val cell: Long,
        val timeout: Duration?
    )
}
