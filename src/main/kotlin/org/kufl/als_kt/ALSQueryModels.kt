/*
 * ALS_Kt - A Kotlin Library for Apple's Location Services
 * Copyright: Florian Kuenzig - 2026
 * Incorporates work from GrapheneOS NetworkLocation (MIT License)
 * https://github.com/GrapheneOS/platform_packages_apps_NetworkLocation
 * This file is released under the MIT License.
 */

package org.kufl.als_kt

import als_proto.AppleLocationServices.ALSLocation
import als_proto.AppleLocationServices.GsmCellTower
import als_proto.AppleLocationServices.LteCellTower
import als_proto.AppleLocationServices.Nr5GCellTower
import als_proto.AppleLocationServices.ScdmaCellTower

/**
 * Represents a location object as used by ALS. Specifies the estimated position of a radio device, e.g. a cell tower.
 * Apple stores latitude longitude coordinates as integers scaled by 10^-8.
 * This data class reverses this back to double coordinates within its constructor.
 *
 * @property latitude The latitude of the location, defaulting to 0.0 if not specified.
 * @property longitude The longitude of the location, defaulting to 0.0 if not specified.
 * @property accuracyMeters The accuracy of the location in meters, defaulting to 0 if not specified.
 * @property reachMeters The reach of a cell tower / hotspot, defaulting to 0 if not specified.
 * @property score An optional score metric for the location entry, defaulting to 0 if not specified.
 */
data class ALSQueryLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracyMeters: Int = 0,
    val reachMeters: Int = 0,
    val score: Int = 0
) {
    constructor(proto: ALSLocation) : this(
        latitude = proto.latitude.toDouble() * SCALE_FACTOR,
        longitude = proto.longitude.toDouble() * SCALE_FACTOR,
        accuracyMeters = proto.accuracy,
        reachMeters = if (proto.hasReach()) proto.reach else 0,
        score = if (proto.hasScore()) proto.score else 0
    )

    /**
     * Checks if the current Location entity is valid.
     * @return true if the location coordinates are valid and the accuracy is greater 0.
     */
    fun isValid(): Boolean {
        return accuracyMeters > 0 &&
                latitude != NO_LOCATION_SENTINEL * SCALE_FACTOR &&
                longitude != NO_LOCATION_SENTINEL * SCALE_FACTOR
    }

    companion object {
        private const val SCALE_FACTOR = 1e-8
        private const val NO_LOCATION_SENTINEL = -18000000000L  // Apple uses this latitude sentinel to signal "no positioning data available"

        /**
         * Converts a protobuf location entity into a [ALSQueryLocation].
         * @return the location entity or null if the location coordinates contain sentinel values for no data.
         */
        fun fromProtoOrNull(proto: ALSLocation): ALSQueryLocation? {
            if (proto.latitude == NO_LOCATION_SENTINEL ||
                proto.longitude == NO_LOCATION_SENTINEL) return null
            return ALSQueryLocation(proto)
        }
    }
}

/**
 * Represents a cell entry used in ALS (Apple Location Services) to define cell tower-related data.
 * This object can be converted to a protobuf entity for sending to ALS and can be parsed from a protobuf response.
 * It is used for both purposes:
 * - Querying cell tower information from ALS.
 * - Parsing cell tower information from ALS responses.
 *
 * Validity of returned cell data can be checked with a call to [isValid].
 *
 * @property technology The mobile network generation / RAT of the cell, specified by [ALSTechnology] (e.g., GSM, LTE, ...).
 * @property country The Mobile Country Code (MCC) of the network operator.
 * @property network The Mobile Network Code (MNC) of the network operator.
 * @property area The local area code (LAC/TAC) associated with the cell.
 * @property cellId The unique identifier (cell ID) of the cell.
 * @property physicalCell Optional physical cell identifier (e.g., PSC/PCI).
 * @property location Optional location data associated with the cell, represented as [ALSQueryLocation].
 * @property frequency Optional frequency channel number of the cell (e.g., ARFCN/EARFCN).
 */
data class ALSQueryCell(
    val technology: ALSTechnology,
    val country: Int,
    val network: Int,
    val area: Int,
    val cellId: Long,
    val physicalCell: Int? = null,
    val location: ALSQueryLocation? = null,
    val frequency: Int? = null
) {

    /**
     * Checks if the current cell has a valid cell ID.
     * @return true if the cell ID is greater than 0 and not null.
     */
    fun hasCellId(): Boolean = cellId >= 0

    /**
     * Checks if the current cell is valid.
     * @return true if the location is valid and the cellID is not 0.
     */
    fun isValid(): Boolean = hasCellId() && (location?.isValid() ?: false)

    /** Converts the object to a queryable [GsmCellTower] entity.
     *  Is used for querying both GSM and WCDMA cell towers. */
    fun toGsmProto(): GsmCellTower =
        GsmCellTower.newBuilder()
            .setMcc(country)
            .setMnc(network)
            .setLacId(area)
            .setCellId(cellId.toCheckedInt())
            .build()

    /** Converts the object to a queryable [ScdmaCellTower] entity. */
    fun toTdScdmaProto(): ScdmaCellTower =
        ScdmaCellTower.newBuilder()
            .setMcc(country)
            .setMnc(network)
            .setLacId(area)
            .setCellId(cellId.toCheckedInt())
            .build()

    /** Converts the object to a queryable [LteCellTower] entity. */
    fun toLteProto(): LteCellTower =
        LteCellTower.newBuilder()
            .setMcc(country)
            .setMnc(network)
            .setTacId(area)
            .setCellId(cellId.toCheckedInt())
            .build()

    /** Converts the object to a queryable [Nr5GCellTower] entity. */
    fun toNrProto(): Nr5GCellTower =
        Nr5GCellTower.newBuilder()
            .setMcc(country)
            .setMnc(network)
            .setTacId(area)
            .setCellId(cellId)
            .build()

    override fun toString(): String =
        "ALSQueryCell(technology=$technology, country=$country, network=$network, " +
            "area=$area, cell=$cellId, location=$location, physicalCell=$physicalCell, frequency=$frequency)"

    companion object {
        /**
         * Parses a [GsmCellTower] proto into an [ALSQueryCell].
         *
         * WCDMA cells are queried and returned by ALS via the GSM entity. Since GSM cell IDs are
         * limited to 16 bits (max 65535), a cell ID above that threshold is treated as WCDMA.
         */
        fun fromGsmProto(proto: GsmCellTower): ALSQueryCell =
            ALSQueryCell(
                technology = if (proto.cellId > 0xFFFF) ALSTechnology.WCDMA else ALSTechnology.GSM,
                country = proto.mcc,
                network = proto.mnc,
                area = proto.lacId,
                cellId = proto.cellId.toLong(),
                physicalCell = proto.psc.takeIf { it != 0 },
                location = proto.takeIf { it.hasLocation() }?.location?.let(ALSQueryLocation::fromProtoOrNull),
                frequency = proto.arcfn.takeIf { it != 0 }
            )

        /**
         * Parses a [ScdmaCellTower] proto into an [ALSQueryCell].
         */
        fun fromTdScdmaProto(proto: ScdmaCellTower): ALSQueryCell =
            ALSQueryCell(
                technology = ALSTechnology.TD_SCDMA,
                country = proto.mcc,
                network = proto.mnc,
                area = proto.lacId,
                cellId = proto.cellId.toLong(),
                physicalCell = proto.psc.takeIf { it != 0 },
                location = proto.takeIf { it.hasLocation() }?.location?.let(ALSQueryLocation::fromProtoOrNull),
                frequency = proto.arfcn.takeIf { it != 0 }
            )

        /** Parses a [LteCellTower] proto into an [ALSQueryCell]. */
        fun fromLteProto(proto: LteCellTower): ALSQueryCell =
            ALSQueryCell(
                technology = ALSTechnology.LTE,
                country = proto.mcc,
                network = proto.mnc,
                area = proto.tacId,
                cellId = proto.cellId.toLong(),
                physicalCell = proto.pid.takeIf { it != 0 },
                location = proto.takeIf { it.hasLocation() }?.location?.let(ALSQueryLocation::fromProtoOrNull),
                frequency = proto.uarfcn.takeIf { it != 0 }
            )

        /** Parses a [Nr5GCellTower] proto into an [ALSQueryCell]. */
        fun fromNrProto(proto: Nr5GCellTower): ALSQueryCell =
            ALSQueryCell(
                technology = ALSTechnology.NR,
                country = proto.mcc,
                network = proto.mnc,
                area = proto.tacId,
                cellId = proto.cellId,
                location = proto.takeIf { it.hasLocation() }?.location?.let(ALSQueryLocation::fromProtoOrNull),
                frequency = proto.nrarfcn.takeIf { it != 0 }
            )
    }
}

/** Validates that a Long value cell ID fits into a 32-bit protobuf field.
 *  Needed for "to-protobuf" conversions of [ALSTechnology] != [ALSTechnology.NR] since only [ALSTechnology.NR] cells support a 64-bit cell ID.
 *  @return the cell ID as an Int32.
 */
private fun Long.toCheckedInt(): Int {
    if (this > Int.MAX_VALUE.toLong()) throw CellIdTooLargeException()
    return this.toInt()
}

/** Signals that the cell ID cannot be encoded into a 32-bit protobuf field. */
class CellIdTooLargeException : IllegalArgumentException("Cell id exceeds Int32 storage")
