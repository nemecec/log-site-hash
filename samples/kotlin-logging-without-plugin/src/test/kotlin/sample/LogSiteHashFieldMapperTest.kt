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
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import dev.nemecec.logsitehash.logbackgelf.LogSiteHashFieldMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class LogSiteHashFieldMapperTest {

    private val mapper = LogSiteHashFieldMapper()

    @Test
    fun `same template produces the same hash`() {
        val appender = configureLogging()
        val logger = KotlinLogging.logger {}

        repeat(2) { logger.atInfo { message = "Hello {}"; arguments = arrayOf("world") } }

        val hashes = appender.events.map { mapper.hash(it) }
        assertThat(hashes).hasSize(2)
        assertThat(hashes[0]).isEqualTo(hashes[1])
    }

    @Test
    fun `different levels produce different hashes`() {
        val appender = configureLogging()
        val logger = KotlinLogging.logger {}

        logger.atInfo { message = "Hello" }
        logger.atWarn { message = "Hello" }

        val hashes = appender.events.map { mapper.hash(it) }
        assertThat(hashes).hasSize(2)
        assertThat(hashes[0]).isNotEqualTo(hashes[1])
    }

    @Test
    fun `arguments keep the template, so different values give the same hash`() {
        val appender = configureLogging()
        val logger = KotlinLogging.logger {}

        for (i in 0..2) {
            logger.atInfo {
                message = "Processing item {}"
                arguments = arrayOf(i)
            }
        }

        val events = appender.events
        assertThat(events.map { it.message }).containsOnly("Processing item {}")
        assertThat(events.map { it.formattedMessage })
            .containsExactly("Processing item 0", "Processing item 1", "Processing item 2")
        assertThat(events.map { mapper.hash(it) }.toSet()).hasSize(1)
    }

    @Test
    fun `a lambda is formatted before Logback sees it, so different values give different hashes`() {
        val appender = configureLogging()
        val logger = KotlinLogging.logger {}

        for (i in 0..2) {
            logger.info { "Processing item $i" }
        }

        val events = appender.events
        assertThat(events.map { it.message })
            .containsExactly("Processing item 0", "Processing item 1", "Processing item 2")
        assertThat(events.map { mapper.hash(it) }.toSet()).hasSize(3)
    }

    @Test
    fun `logback xml configures the field mapper, so GELF output carries the hash and template`() {
        val loggerContext =
            LoggerFactory.getILoggerFactory() as LoggerContext
        val originalOut = System.out
        val captured = ByteArrayOutputStream()
        try {
            System.setOut(PrintStream(captured, true, Charsets.UTF_8))
            loggerContext.reset()
            val configurator = JoranConfigurator()
            configurator.context = loggerContext
            configurator.doConfigure(javaClass.getResource("/logback.xml"))
            val name = "world"
            KotlinLogging.logger {}.atInfo {
                message = "Hello {}"
                arguments = arrayOf(name)
            }
        } finally {
            System.setOut(originalOut)
            loggerContext.reset()
        }

        val output = captured.toString(Charsets.UTF_8)
        assertThat(output).contains("\"_msg_template\":\"Hello {}\"")
        assertThat(output).containsPattern("\"_log_site_hash\":\"[0-9a-f-]{36}\"")
    }

    private fun configureLogging(): EventCollectingAppender {
        val loggerContext =
            LoggerFactory.getILoggerFactory() as LoggerContext
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
