/*
 * Copyright 2026 Neeme Praks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sample

import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Sample application demonstrating log site hashing with kotlin-logging + Logback,
 * without the kotlin-logging compile-time plugin.
 *
 * A lambda such as `logger.info { "Processing item $i" }` is formatted before Logback sees
 * the event, so `getMessage()` returns the formatted text and every value gives a new hash.
 * The `arguments` property of the event builder keeps the template and the values apart,
 * so the LogSiteHashFieldMapper computes one hash for all values.
 */
private val logger = KotlinLogging.logger {}

fun main() {
    // The template is lost: three different formatted messages -> three different hashes
    for (i in 0..2) {
        logger.info { "Processing item $i" }
    }

    // The template is kept: these three calls share the same template -> same hash
    for (i in 0..2) {
        logger.atInfo {
            message = "Processing item {}"
            arguments = arrayOf(i)
        }
    }

    // Different template -> different hash
    logger.atInfo { message = "Done processing" }

    // Different level, same template -> different hash
    logger.atWarn { message = "Done processing" }
}
