/*
 * ALS_Kt - A Kotlin Library for Apple's Location Services
 * Copyright: Florian Kuenzig - 2026
 * Incorporates work from GrapheneOS NetworkLocation (MIT License)
 * https://github.com/GrapheneOS/platform_packages_apps_NetworkLocation
 * This file is released under the MIT License.
 */

package org.kufl.als_kt

import java.io.IOException


/**
 * Base exception type for all ALS client-related errors.
 *
 * @constructor Initializes the exception with an optional message and cause.
 * @param message An optional message describing the exception.
 * @param cause An optional cause representing the underlying issue.
 */
sealed class ALSClientException(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    class HttpStatus(val code: Int) : ALSClientException("ALS server returned HTTP $code")
    class HttpNoData(val code: Int) : ALSClientException("ALS server returned an empty payload")
    class CellIdTooLarge : ALSClientException("Cell id cannot be encoded into 32 bit fields")
    class PayloadEncoding(cause: Throwable) : ALSClientException("Failed to encode ALS protobuf payload", cause)
    class NetworkError(cause: IOException) : ALSClientException("Network error: ${cause.message}", cause)
}
