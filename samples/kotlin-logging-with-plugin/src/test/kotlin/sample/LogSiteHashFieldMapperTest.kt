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

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import dev.nemecec.logsitehash.logbackgelf.LogSiteHashFieldMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.logback.internal.LogbackLoggerFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class LogSiteHashFieldMapperTest {

    private val mapper = LogSiteHashFieldMapper()

    @Test
    fun `same template produces the same hash`() {
        val appender = configureLogging()
        val logger = KotlinLogging.logger {}

        logger.info { "Hello world" }
        logger.info { "Hello world" }

        val hashes = appender.events.map { mapper.hash(it) }
        assertThat(hashes).hasSize(2)
        assertThat(hashes[0]).isEqualTo(hashes[1])
    }

    @Test
    fun `different templates produce different hashes`() {
        val appender = configureLogging()
        val logger = KotlinLogging.logger {}

        logger.info { "Hello" }
        logger.info { "Goodbye" }

        val hashes = appender.events.map { mapper.hash(it) }
        assertThat(hashes).hasSize(2)
        assertThat(hashes[0]).isNotEqualTo(hashes[1])
    }

    @Test
    fun `different levels produce different hashes`() {
        val appender = configureLogging()
        val logger = KotlinLogging.logger {}

        logger.info { "Hello" }
        logger.warn { "Hello" }

        val hashes = appender.events.map { mapper.hash(it) }
        assertThat(hashes).hasSize(2)
        assertThat(hashes[0]).isNotEqualTo(hashes[1])
    }

    @Test
    fun `compiler plugin provides message template`() {
        val appender = configureLogging()
        val logger = KotlinLogging.logger {}

        val name = "world"
        logger.info { "Hello $name" }

        val event = appender.events.single()
        // The compiler plugin captures the template expression in getMessage()
        // while getFormattedMessage() has the evaluated string
        assertThat(event.formattedMessage).isEqualTo("Hello world")
        // The template should contain the source expression (with $name reference)
        assertThat(event.message).contains("name")
    }

    @Test
    fun `same template with different values produces same hash`() {
        val appender = configureLogging()
        val logger = KotlinLogging.logger {}

        for (i in 0..2) {
            logger.info { "Processing item $i" }
        }

        val hashes = appender.events.map { mapper.hash(it) }
        assertThat(hashes).hasSize(3)
        assertThat(hashes[0]).isEqualTo(hashes[1])
        assertThat(hashes[1]).isEqualTo(hashes[2])
    }

    @Test
    fun `logback xml configures the field mapper, so GELF output carries the hash and template`() {
        val loggerContext =
            LogbackLoggerFactory.getLoggerContext() ?: error("Logback logger context not found")
        val originalOut = System.out
        val captured = ByteArrayOutputStream()
        try {
            System.setOut(PrintStream(captured, true, Charsets.UTF_8))
            loggerContext.reset()
            val configurator = JoranConfigurator()
            configurator.context = loggerContext
            configurator.doConfigure(javaClass.getResource("/logback.xml"))
            val name = "world"
            KotlinLogging.logger {}.info { "Hello $name" }
        } finally {
            System.setOut(originalOut)
            loggerContext.reset()
        }

        val output = captured.toString(Charsets.UTF_8)
        assertThat(output).contains("Hello \$name")
        assertThat(output).containsPattern("\"_log_site_hash\":\"[0-9a-f-]{36}\"")
    }

    private fun configureLogging(): EventCollectingAppender {
        val loggerContext =
            LogbackLoggerFactory.getLoggerContext() ?: error("Logback logger context not found")
        loggerContext.reset()
        val appender = EventCollectingAppender()
        appender.name = "test"
        appender.context = loggerContext
        appender.start()

        val rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME)
        rootLogger.addAppender(appender)
        rootLogger.level = Level.TRACE
        return appender
    }
}

class EventCollectingAppender : AppenderBase<ILoggingEvent>() {
    val events = mutableListOf<ILoggingEvent>()

    override fun append(event: ILoggingEvent) {
        events.add(event)
    }
}
