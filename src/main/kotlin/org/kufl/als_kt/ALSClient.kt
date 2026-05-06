/*
 * ALS_Kt - A Kotlin Library for Apple's Location Services
 * Copyright: Florian Kuenzig - 2026
 * Incorporates work from GrapheneOS NetworkLocation (MIT License)
 * https://github.com/GrapheneOS/platform_packages_apps_NetworkLocation
 * This file is released under the MIT License.
 */

package org.kufl.als_kt

import als_proto.AppleLocationServices.ALSLocationRequest
import als_proto.AppleLocationServices.ALSLocationResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Kotlin client to request Apple Location Service (ALS) API.
 *
 * Supports GSM, UMTS, LTE, and NR (5G) cell lookups.
 *
 * @param timeout optional timeout applied to the http requests.
 * @param httpClient optional pre-configured OkHttpClient. When provided the timeout parameter is ignored.
 */
class ALSClient @JvmOverloads constructor(
    timeout: Duration? = null,
    httpClient: OkHttpClient? = null
) {
    val numResponseHeaderBytes = 10
    private val httpClient: OkHttpClient = httpClient ?: defaultHttpClient(timeout)
    private val endpoint: HttpUrl = "https://gs-loc.apple.com/clls/wloc".toHttpUrl()

    // China has its own endpoint due to regulations. Will add support later
    // private val endpointChina: HttpUrl = "https://gs-loc-cn.apple.com/clls/wloc".toHttpUrl()

    // Fake Apple Header
    private val headers = mapOf(
        "User-Agent" to "locationd/2960.0.57 CFNetwork/3826.500.111.1.1 Darwin/24.4.0",
        "Accept" to "*/*",
        "Accept-Language" to "en-US,en;q=0.9",
        "Content-Type" to "application/x-www-form-urlencoded"
    )
    private val serviceIdentifier = "com.apple.locationd"
    private val osVersion = "15.4.24E248"
    private val locale = "en-US_US"

    private val macosMeta: ALSLocationRequest.ALSMeta = ALSLocationRequest.ALSMeta.newBuilder()
        .setSoftwareBuild("macOS15.4/24E248")
        .setProductId("arm64")
        .build()

    /**
     * Queries ALS for the given [origin] cell.
     * Response contains one or more cells together with their reach and location data if mapped by ALS.
     *
     * This is a blocking call that encodes the origin cell into a Protobuf payload, sends it to the ALS endpoint, and parses the response into a list of nearby cells
     * across all supported technologies (GSM, WCDMA, TD_SCDMA, LTE, NR/5G).
     *
     * @param origin The cell tower to use as the query origin.
     * @return A list of cell towers returned by ALS. May contain cells without location data (`location = null`) indicating an invalid cell.
     * Callers should use [ALSQueryCell.isValid] to check whether a returned cell is usable.
     *
     * @throws ALSClientException.CellIdTooLarge If the origin cell's ID exceeds the allowed range.
     * @throws ALSClientException.PayloadEncoding If the Protobuf payload cannot be constructed.
     * @throws ALSClientException.HttpStatus If the ALS server returns a non-2xx HTTP status.
     * @throws ALSClientException.HttpNoData If the ALS server returns an empty response body.
     * @throws ALSClientException.NetworkError If a network-level error occurs (timeout, DNS, connection refused, etc.).
     */
    @Throws(ALSClientException::class)
    fun requestCells(origin: ALSQueryCell): List<ALSQueryCell> {
        val protoPayload = try {
            buildRequestPayload(origin)
        } catch (_: CellIdTooLargeException) {
            throw ALSClientException.CellIdTooLarge()
        } catch (ex: IllegalArgumentException) {
            throw ALSClientException.PayloadEncoding(ex)
        }

        val responseData = sendHttpRequest(protoPayload)
        val protoResponse = ALSLocationResponse.parseFrom(responseData)
        val cells = buildList {
            addAll(protoResponse.gsmCellTowersList.map { ALSQueryCell.fromGsmProto(it) })
            addAll(protoResponse.scdmaCellTowersList.map { ALSQueryCell.fromTdScdmaProto(it) })
            addAll(protoResponse.lteCellTowersList.map { ALSQueryCell.fromLteProto(it) })
            addAll(protoResponse.nr5GCellTowersList.map { ALSQueryCell.fromNrProto(it) })
        }

        return cells
    }

    /**
     * Suspend version of [requestCells] that offloads the blocking HTTP call to [Dispatchers.IO].
     *
     * @param origin The cell tower to use as the query origin.
     * @return A list of cell towers returned by ALS. May contain cells without location data. See [requestCells] for more details.
     * @throws ALSClientException.CellIdTooLarge If the origin cell's ID exceeds the allowed range.
     * @throws ALSClientException.PayloadEncoding If the Protobuf payload cannot be constructed.
     * @throws ALSClientException.HttpStatus If the ALS server returns a non-2xx HTTP status.
     * @throws ALSClientException.HttpNoData If the ALS server returns an empty response body.
     * @throws ALSClientException.NetworkError If a network-level error occurs (timeout, DNS, connection refused, etc.).
     * @see requestCells
     */
    suspend fun requestCellsAsync(origin: ALSQueryCell): List<ALSQueryCell> =
        withContext(Dispatchers.IO) { requestCells(origin) }

    /**
     * Builds and returns a Protobuf-encoded request payload for querying ALS based on the given origin cell.
     *
     * @param origin The cell tower to use as the query origin.
     * @return A `ByteArray` containing the Protobuf-encoded request payload that can be sent to the ALS server.
     */
    private fun buildRequestPayload(origin: ALSQueryCell): ByteArray {
        val request = ALSLocationRequest.newBuilder().apply {
            when (origin.technology) {
                ALSTechnology.GSM -> addGsmCellTowers(origin.toGsmProto())
                ALSTechnology.WCDMA -> addGsmCellTowers(origin.toGsmProto())
                ALSTechnology.TD_SCDMA -> addScdmaCellTowers(origin.toTdScdmaProto())
                ALSTechnology.LTE -> addLteCellTowers(origin.toLteProto())
                ALSTechnology.NR -> addNr5GCellTowers(origin.toNrProto())
            }
            numberOfSurroundingGsmCells = 0
            numberOfSurroundingWifis = 1
            addSurroundingWifiBands(ALSLocationRequest.WifiBand.K2DOT4GHZ)
            meta = macosMeta
        }.build()

        return request.toByteArray()
    }

    @Throws(ALSClientException::class)
    private fun sendHttpRequest(protoData: ByteArray): ByteArray {
        val body = try {
            ByteArrayOutputStream().apply {
                write(buildRequestHeader())
                write(packInt(protoData.size))
                write(protoData)
            }.toByteArray()
        } catch (ex: IllegalArgumentException) {
            throw ALSClientException.PayloadEncoding(ex)
        }

        val request = Request.Builder()
            .url(endpoint)
            .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .apply {
                headers.forEach { (key, value) -> addHeader(key, value) }
            }
            .build()

        val response = try {
            httpClient.newCall(request).execute()
        } catch (ex: java.io.IOException) {
            throw ALSClientException.NetworkError(ex)
        }

        response.use {
            if (!it.isSuccessful) {
                throw ALSClientException.HttpStatus(it.code)
            }

            val responseBytes = try {
                it.body.bytes()
            } catch (ex: java.io.IOException) {
                throw ALSClientException.NetworkError(ex)
            }

            if (responseBytes.isEmpty() || responseBytes.size <= numResponseHeaderBytes) {
                throw ALSClientException.HttpNoData(it.code)
            }

            return responseBytes.copyOfRange(numResponseHeaderBytes, responseBytes.size)
        }
    }

    /**
     * Build the binary header Apple expects before the protobuf payload.
     * We are pretending to be an Apple device here.
     */
    private fun buildRequestHeader(): ByteArray {
        val localeBytes = locale.toByteArray(Charsets.UTF_8)
        val identifierBytes = serviceIdentifier.toByteArray(Charsets.UTF_8)
        val versionBytes = osVersion.toByteArray(Charsets.UTF_8)

        return ByteArrayOutputStream().apply {
            writeShort(1) // hardcoded marker
            writeShort(localeBytes.size)
            write(localeBytes)
            writeShort(identifierBytes.size)
            write(identifierBytes)
            writeShort(versionBytes.size)
            write(versionBytes)
            writeInt(1) // request code
        }.toByteArray()
    }

    // --Encoding helpers
    /**
     * Writes a 16-bit short integer in big-endian order to the current `ByteArrayOutputStream`.
     *
     * @param value The integer value to be written as a 16-bit short.
     */
    private fun ByteArrayOutputStream.writeShort(value: Int) {
        write(ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(value.toShort()).array())
    }

    /**
     * Writes a 32-bit integer in big-endian order to the current `ByteArrayOutputStream`.
     *
     * @param value The integer value to be written as a 32-bit integer.
     */
    private fun ByteArrayOutputStream.writeInt(value: Int) {
        write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array())
    }

    /**
     * Encodes a full integer value into a 4-byte `ByteArray` in big-endian order.
     *
     * @param length The integer value to be encoded.
     * @return A [ByteArray] containing the 4-byte big-endian representation of the input integer.
     */
    private fun packInt(length: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(length).array()

    /** Returns a default OkHttpClient with the given timeout.
     * @return An [OkHttpClient] with the given timeout. */
    private fun defaultHttpClient(timeout: Duration?): OkHttpClient {
        val builder = OkHttpClient.Builder()
        timeout?.takeIf { !it.isZero && !it.isNegative }?.let { duration ->
            val millis = duration.toMillis()
            builder.callTimeout(millis, TimeUnit.MILLISECONDS)
            builder.connectTimeout(millis, TimeUnit.MILLISECONDS)
            builder.readTimeout(millis, TimeUnit.MILLISECONDS)
            builder.writeTimeout(millis, TimeUnit.MILLISECONDS)
        }
        return builder.build()
    }
}
