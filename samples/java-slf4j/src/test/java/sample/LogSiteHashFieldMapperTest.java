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

package sample;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import dev.nemecec.logsitehash.logbackgelf.LogSiteHashFieldMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class LogSiteHashFieldMapperTest {

    private final LogSiteHashFieldMapper mapper = new LogSiteHashFieldMapper();

    @Test
    @DisplayName("Same template produces the same hash")
    void sameTemplate() {
        var event1 = createEvent("Hello {}", Level.INFO);
        var event2 = createEvent("Hello {}", Level.INFO);
        assertThat(mapper.hash(event1)).isEqualTo(mapper.hash(event2));
    }

    @Test
    @DisplayName("Different templates produce different hashes")
    void differentTemplate() {
        var event1 = createEvent("Hello {}", Level.INFO);
        var event2 = createEvent("Goodbye {}", Level.INFO);
        assertThat(mapper.hash(event1)).isNotEqualTo(mapper.hash(event2));
    }

    @Test
    @DisplayName("Different levels produce different hashes")
    void differentLevel() {
        var event1 = createEvent("Hello {}", Level.INFO);
        var event2 = createEvent("Hello {}", Level.WARN);
        assertThat(mapper.hash(event1)).isNotEqualTo(mapper.hash(event2));
    }

    @Test
    @DisplayName("Hash is a valid UUID")
    void hashIsUuid() {
        var event = createEvent("Hello {}", Level.INFO);
        var hash = mapper.hash(event);
        assertThat(hash).isNotNull();
        assertThat(hash.toString()).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    @DisplayName("logback.xml configures the field mapper, so GELF output carries the hash and template")
    void logbackXmlWiresTheFieldMapper() throws Exception {
        var originalOut = System.out;
        var captured = new ByteArrayOutputStream();
        var context = (LoggerContext) LoggerFactory.getILoggerFactory();
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            context.reset();
            var configurator = new JoranConfigurator();
            configurator.setContext(context);
            configurator.doConfigure(getClass().getResource("/logback.xml"));
            LoggerFactory.getLogger("sample.Other").info("Hello {}", "world");
        } finally {
            System.setOut(originalOut);
            context.reset();
        }

        var output = captured.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("\"_msg_template\":\"Hello {}\"");
        assertThat(output).containsPattern("\"_log_site_hash\":\"[0-9a-f-]{36}\"");
    }

    private ILoggingEvent createEvent(String message, Level level) {
        var context = new LoggerContext();
        var logger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        var event = new LoggingEvent("sample.SampleApp", logger, level, message, null, null);
        return event;
    }
}
