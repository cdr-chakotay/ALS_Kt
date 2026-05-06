/*
 * ALS_Kt - A Kotlin Library for Apple's Location Services
 * Copyright: Florian Kuenzig - 2026
 * Incorporates work from GrapheneOS NetworkLocation (MIT License)
 * https://github.com/GrapheneOS/platform_packages_apps_NetworkLocation
 * This file is released under the MIT License.
 */

package org.kufl.als_kt

import als_proto.AppleLocationServices.ALSLocationResponse
import als_proto.AppleLocationServices.WirelessAP
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

/**
 * Live integration tests for [ALSClient] against Apple's live location service.
 */
class ALSClientTest {

    private val client = ALSClient()

    // Test cell definitions: Known cells for integration testing
    private val gsmCell = ALSQueryCell(technology = ALSTechnology.GSM, country = 262, network = 2, area = 566, cellId = 4461)
    private val wcdmaCell = ALSQueryCell(technology = ALSTechnology.WCDMA, country = 260, network = 1, area = 34121, cellId = 222886383)
    private val lteCell = ALSQueryCell(technology = ALSTechnology.LTE, country = 262, network = 1, area = 1492, cellId = 28468992)
    private val nrCell = ALSQueryCell(technology = ALSTechnology.NR, country = 262, network = 2, area = 43556, cellId = 36338893011)

    // Helpers
    /**
     * Asserts that [result] is non-empty and that its first element matches all request attributes of [origin].
     * When [expectValidLocation] is true, also asserts
     * that the first cell carries a usable location fix.
     */
    private fun verifyResult(origin: ALSQueryCell, result: List<ALSQueryCell>, expectValidLocation: Boolean = true) {
        assertTrue(result.isNotEmpty(), "ALS should return at least one cell")
        val first = result.first()
        assertEquals(origin.technology, first.technology)
        assertEquals(origin.country, first.country)
        assertEquals(origin.network, first.network)
        assertEquals(origin.area, first.area)
        assertEquals(origin.cellId, first.cellId)
        if (expectValidLocation) {
            assertTrue(first.isValid(), "First returned cell should have a valid location")
        }
    }

    /**
     * Returns an [OkHttpClient] whose interceptor immediately returns a fake HTTP response
     * with the given [body] bytes and [code], bypassing any real network call.
     */
    private fun mockClient(body: ByteArray, code: Int = 200): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code in 200..299) "OK" else "Error")
                    .body(body.toResponseBody())
                    .build()
            }
            .build()

    // Integration tests for sync API. Also test a cell for each technology. (except TD_SCDMA since I am not in possession of such a Cell ID).
    /** Queries a known German GSM cell and verifies the echoed cell has a valid location.*/
    @Test
    fun testRequestGSMCell() = verifyResult(gsmCell, client.requestCells(gsmCell))

    /** Queries a known Polish UMTS cell.*/
    @Test
    fun testRequestUMTSCell() =
        verifyResult(wcdmaCell, client.requestCells(wcdmaCell))

    /** Queries a known German LTE cell and verifies the echoed cell has a valid location. */
    @Test
    fun testRequestLTECell() = verifyResult(lteCell, client.requestCells(lteCell))

    /** Queries a known German NR cell and verifies the echoed cell has a valid location. */
    @Test
    fun testRequestNRCell() = verifyResult(nrCell, client.requestCells(nrCell))

    // Test for async API
    // Only one test here since it wraps the sync methods.
    /** Same as [testRequestLTECell] but through the suspend API. */
    @Test
    fun testRequestLTECellAsync() = runBlocking {
        verifyResult(lteCell, client.requestCellsAsync(lteCell))
    }
    
    // Test of proper exceptions raising
    /** A 1 ms timeout forces a connection failure, which must surface as [ALSClientException.NetworkError]. */
    @Test
    fun testTimeoutThrowsNetworkError() {
        val shortTimeoutClient = ALSClient(timeout = Duration.ofMillis(1))
        assertThrows<ALSClientException.NetworkError> {
            shortTimeoutClient.requestCells(lteCell)
        }
    }

    /** A cell ID larger than Int32 max cannot be encoded in the protobuf request and must throw [ALSClientException.CellIdTooLarge]. */
    @Test
    fun testCellIdTooLargeThrows() {
        val oversizedCell = lteCell.copy(cellId = Int.MAX_VALUE.toLong() + 1)
        assertThrows<ALSClientException.CellIdTooLarge> {
            client.requestCells(oversizedCell)
        }
    }

    /**
     * When the server returns a body shorter than the 10-byte ALS response header,
     * [ALSClientException.HttpNoData] must be thrown.
     */
    @Test
    fun testShortBodyThrowsHttpNoData() {
        val client = ALSClient(httpClient = mockClient(body = ByteArray(5)))
        assertThrows<ALSClientException.HttpNoData> {
            client.requestCells(lteCell)
        }
    }

    /**
     * When the server returns a non-2xx status, [ALSClientException.HttpStatus] must be thrown
     * carrying the actual HTTP status code.
     */
    @Test
    fun testNon2xxThrowsHttpStatus() {
        val client = ALSClient(httpClient = mockClient(body = ByteArray(0), code = 503))
        val ex = assertThrows<ALSClientException.HttpStatus> {
            client.requestCells(lteCell)
        }
        assertEquals(503, ex.code)
    }

    // Integration test for invalid/unknown cell
    /** Queries a cell that does not exist in ALS and verifies the returned cell is not valid. */
    @Test
    fun testInvalidLTECellReturnsNotValid() {
        val invalidCell = ALSQueryCell(technology = ALSTechnology.LTE, country = 262, network = 1, area = 512, cellId = 12345)
        val result = client.requestCells(invalidCell)
        assertTrue(result.isNotEmpty(), "ALS should echo back at least one cell")
        assertTrue(!result.first().isValid(), "Unknown cell should not carry a valid location")
    }

    // Unit tests for ALSQueryCell.isValid()
    /** A cell with a location but no cell ID (cellId < 0) must not be valid. */
    @Test
    fun testCellWithLocationButNoCellIdIsNotValid() {
        val cell = ALSQueryCell(
            technology = ALSTechnology.LTE,
            country = 262, network = 1, area = 512, cellId = -1,
            location = ALSQueryLocation(latitude = 52.0, longitude = 13.0, accuracyMeters = 100)
        )
        assertTrue(!cell.isValid(), "Cell with no cell ID should not be valid even when location is present")
    }

    /** A cell with a valid cell ID but no location must not be valid. */
    @Test
    fun testCellWithCellIdButNoLocationIsNotValid() {
        val cell = ALSQueryCell(
            technology = ALSTechnology.LTE,
            country = 262, network = 1, area = 512, cellId = 12345,
            location = null
        )
        assertTrue(!cell.isValid(), "Cell without a location should not be valid even when cell ID is present")
    }

    /**
     * When the server returns a valid protobuf response that contains no cell towers
     * (only a [WirelessAP] entry, which the cell-parsing code ignores), the result
     * must be an empty list.
     */
    @Test
    fun testNoCellTowersInResponseReturnsEmptyList() {
        val protoBytes = ALSLocationResponse.newBuilder()
            .addWirelessAps(WirelessAP.newBuilder().setMacId("00:00:00:00:00:00").build())
            .build()
            .toByteArray()
        val body = ByteArray(10) + protoBytes  // 10-byte ALS header + proto payload
        val client = ALSClient(httpClient = mockClient(body))
        val result = client.requestCells(lteCell)
        assertTrue(result.isEmpty(), "Response with no cell towers should yield an empty list")
    }
}
