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
 * Sample application demonstrating log site hashing with kotlin-logging + Logback.
 *
 * The kotlin-logging-compiler-plugin captures the message template at compile time.
 * The LogSiteHashFieldMapper then computes a hash from the template, logger name,
 * level, and marker. Messages with different parameter values but the same template
 * expression get the same hash.
 */
private val logger = KotlinLogging.logger {}

fun main() {
    // These three calls share the same template expression -> same hash
    for (i in 0..2) {
        logger.info { "Processing item $i" }
    }

    // Different template -> different hash
    logger.info { "Done processing" }

    // Different level, same template -> different hash
    logger.warn { "Done processing" }
}
