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

package dev.nemecec.logsitehash.logbackgelf;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MarkerFactory;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class LogSiteHashFieldMapperTest {

    private static final String EXPECTED_HASH_FIELD_NAME = "log_site_hash";
    private static final String EXPECTED_MESSAGE_TEMPLATE_FIELD_NAME = "msg_template";
    private static final String TEST_MESSAGE_TEMPLATE = "this is a test message {}";
    private static final String DIFFERENT_TEST_MESSAGE_TEMPLATE = "Some other message!";
    private static final String LOGGER_NAME = "some.Class";
    private static final String DIFFERENT_LOGGER_NAME = "come.other.Class";
    private static final String MARKER = "SOME_MARKER";
    private static final String DIFFERENT_MARKER = "DIFFERENT_MARKER";

    @Mock private BiConsumer<String, String> keyValueConsumer;
    @Captor private ArgumentCaptor<String> keyCaptor;
    @Captor private ArgumentCaptor<String> valueCaptor;

    private final LogSiteHashFieldMapper mapper = new LogSiteHashFieldMapper();

    @Test
    @DisplayName("Same log events produce the same hash")
    void sameMessages() {
        var event1 = givenLoggingEvent(TEST_MESSAGE_TEMPLATE, LOGGER_NAME, Level.INFO, MARKER);
        var event2 = givenLoggingEvent(
            nonInterned(TEST_MESSAGE_TEMPLATE), nonInterned(LOGGER_NAME), Level.INFO, MARKER);

        mapper.mapField(event1, keyValueConsumer);
        mapper.mapField(event2, keyValueConsumer);

        assertKeysAndValues(
            "49cd2439-b38c-543a-5832-8233abc4a875", TEST_MESSAGE_TEMPLATE,
            "49cd2439-b38c-543a-5832-8233abc4a875", TEST_MESSAGE_TEMPLATE
        );
    }

    @Test
    @DisplayName("Different message templates produce different hashes")
    void differentMessages() {
        var event1 = givenLoggingEvent(TEST_MESSAGE_TEMPLATE, LOGGER_NAME, Level.INFO, MARKER);
        var event2 = givenLoggingEvent(DIFFERENT_TEST_MESSAGE_TEMPLATE, LOGGER_NAME, Level.INFO, MARKER);

        mapper.mapField(event1, keyValueConsumer);
        mapper.mapField(event2, keyValueConsumer);

        assertKeysAndValues(
            "49cd2439-b38c-543a-5832-8233abc4a875", TEST_MESSAGE_TEMPLATE,
            "00223f17-8ca0-caef-316b-d59e9017ff20", DIFFERENT_TEST_MESSAGE_TEMPLATE
        );
    }

    @Test
    @DisplayName("Different logger names produce different hashes")
    void differentLoggerName() {
        var event1 = givenLoggingEvent(TEST_MESSAGE_TEMPLATE, LOGGER_NAME, Level.INFO, MARKER);
        var event2 = givenLoggingEvent(TEST_MESSAGE_TEMPLATE, DIFFERENT_LOGGER_NAME, Level.INFO, MARKER);

        mapper.mapField(event1, keyValueConsumer);
        mapper.mapField(event2, keyValueConsumer);

        assertKeysAndValues(
            "49cd2439-b38c-543a-5832-8233abc4a875", TEST_MESSAGE_TEMPLATE,
            "b6f2d068-3ea5-f494-7bf3-c52afd61cf04", TEST_MESSAGE_TEMPLATE
        );
    }

    @Test
    @DisplayName("Different log levels produce different hashes")
    void differentLogLevel() {
        var event1 = givenLoggingEvent(TEST_MESSAGE_TEMPLATE, LOGGER_NAME, Level.INFO, MARKER);
        var event2 = givenLoggingEvent(TEST_MESSAGE_TEMPLATE, LOGGER_NAME, Level.DEBUG, MARKER);

        mapper.mapField(event1, keyValueConsumer);
        mapper.mapField(event2, keyValueConsumer);

        assertKeysAndValues(
            "49cd2439-b38c-543a-5832-8233abc4a875", TEST_MESSAGE_TEMPLATE,
            "30b0dba0-5160-028e-a369-586ca56ca7e6", TEST_MESSAGE_TEMPLATE
        );
    }

    @Test
    @DisplayName("Different markers produce different hashes")
    void differentMarker() {
        var event1 = givenLoggingEvent(TEST_MESSAGE_TEMPLATE, LOGGER_NAME, Level.INFO, MARKER);
        var event2 = givenLoggingEvent(TEST_MESSAGE_TEMPLATE, LOGGER_NAME, Level.INFO, DIFFERENT_MARKER);

        mapper.mapField(event1, keyValueConsumer);
        mapper.mapField(event2, keyValueConsumer);

        assertKeysAndValues(
            "49cd2439-b38c-543a-5832-8233abc4a875", TEST_MESSAGE_TEMPLATE,
            "657e4a49-90cb-5aa0-5aff-8a52e64bf3cf", TEST_MESSAGE_TEMPLATE
        );
    }

    @Test
    @DisplayName("Null fields produce a valid hash")
    void nullFields() {
        var event = givenLoggingEvent(null, null, null, null);

        mapper.mapField(event, keyValueConsumer);

        assertKeysAndValues("05a796a1-1b7f-3e8d-0f57-de83f3f0586e", "null");
    }

    private ILoggingEvent givenLoggingEvent(String messageTemplate, String loggerName, Level level, String markerString) {
        var event = mock(ILoggingEvent.class);
        given(event.getMessage()).willReturn(messageTemplate);
        given(event.getLoggerName()).willReturn(loggerName);
        given(event.getLevel()).willReturn(level);
        var marker = markerString != null ? MarkerFactory.getMarker(markerString) : null;
        given(event.getMarker()).willReturn(marker);
        return event;
    }

    private String nonInterned(String from) {
        var builder = new StringBuilder();
        for (var c : from.toCharArray()) {
            builder.append(c);
        }
        return builder.toString();
    }

    private void assertKeysAndValues(String... expectedValues) {
        then(keyValueConsumer).should(times(expectedValues.length)).accept(keyCaptor.capture(), valueCaptor.capture());
        assertKeys(expectedValues.length / 2);
        assertThat(valueCaptor.getAllValues()).containsExactly(expectedValues);
    }

    private void assertKeys(int numberOfExecutions) {
        var expectedKeys = Stream.generate(() -> Stream.of(EXPECTED_HASH_FIELD_NAME, EXPECTED_MESSAGE_TEMPLATE_FIELD_NAME))
            .limit(numberOfExecutions)
            .flatMap(Function.identity())
            .toArray(String[]::new);
        assertThat(keyCaptor.getAllValues()).containsExactly(expectedKeys);
    }
}
