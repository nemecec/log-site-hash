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

package dev.nemecec.logsitehash.opentelemetry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.UnaryOperator;

import static dev.nemecec.logsitehash.opentelemetry.LogSiteHashLogRecordProcessor.CODE_FILE_PATH;
import static dev.nemecec.logsitehash.opentelemetry.LogSiteHashLogRecordProcessor.CODE_LINE_NUMBER;
import static dev.nemecec.logsitehash.opentelemetry.LogSiteHashLogRecordProcessor.LOG_BODY_TEMPLATE;
import static dev.nemecec.logsitehash.opentelemetry.LogSiteHashLogRecordProcessor.LOG_SITE_HASH;
import static org.assertj.core.api.Assertions.assertThat;

class LogSiteHashLogRecordProcessorTest {

    private InMemoryLogRecordExporter exporter;
    private SdkLoggerProvider loggerProvider;

    @BeforeEach
    void setUp() {
        exporter = InMemoryLogRecordExporter.create();
        loggerProvider = SdkLoggerProvider.builder()
                .addLogRecordProcessor(new LogSiteHashLogRecordProcessor())
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
                .build();
    }

    @AfterEach
    void tearDown() {
        loggerProvider.close();
    }

    @Test
    @DisplayName("Same inputs produce the same hash")
    void sameInputs() {
        var hash1 = emit("com.example.MyClass", Severity.INFO, b -> b.setBody("Hello {}")
                .setAttribute(LOG_BODY_TEMPLATE, "Hello {}"));
        var hash2 = emit("com.example.MyClass", Severity.INFO, b -> b.setBody("Hello Bob")
                .setAttribute(LOG_BODY_TEMPLATE, "Hello {}"));

        assertThat(hash1).isNotNull().isEqualTo(hash2);
    }

    @Test
    @DisplayName("Different templates produce different hashes")
    void differentTemplates() {
        var hash1 = emit("com.example.MyClass", Severity.INFO, b -> b.setAttribute(LOG_BODY_TEMPLATE, "Hello {}"));
        var hash2 = emit("com.example.MyClass", Severity.INFO, b -> b.setAttribute(LOG_BODY_TEMPLATE, "Goodbye {}"));

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("Different severity levels produce different hashes")
    void differentSeverity() {
        var hash1 = emit("com.example.MyClass", Severity.INFO, b -> b.setAttribute(LOG_BODY_TEMPLATE, "Hello {}"));
        var hash2 = emit("com.example.MyClass", Severity.WARN, b -> b.setAttribute(LOG_BODY_TEMPLATE, "Hello {}"));

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("Different logger names produce different hashes")
    void differentLoggerNames() {
        var hash1 = emit("com.example.ClassA", Severity.INFO, b -> b.setAttribute(LOG_BODY_TEMPLATE, "Hello {}"));
        var hash2 = emit("com.example.ClassB", Severity.INFO, b -> b.setAttribute(LOG_BODY_TEMPLATE, "Hello {}"));

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("Messages without arguments have no template, so the body identifies the log site")
    void bodyIsUsedWithoutTemplate() {
        var hash1 = emit("com.example.MyClass", Severity.INFO, b -> b.setBody("Started"));
        var hash2 = emit("com.example.MyClass", Severity.INFO, b -> b.setBody("Done"));
        var hash3 = emit("com.example.MyClass", Severity.INFO, b -> b.setBody("Done"));

        assertThat(hash1).isNotEqualTo(hash2);
        assertThat(hash2).isEqualTo(hash3);
    }

    @Test
    @DisplayName("Code attributes identify the log site when there is no template, so a formatted body does not change the hash")
    void codeAttributesAreUsedWithoutTemplate() {
        var hash1 = emit("com.example.MyClass", Severity.INFO, b -> b.setBody("Hello Bob")
                .setAttribute(CODE_FILE_PATH, "MyClass.kt").setAttribute(CODE_LINE_NUMBER, 12L));
        var hash2 = emit("com.example.MyClass", Severity.INFO, b -> b.setBody("Hello Alice")
                .setAttribute(CODE_FILE_PATH, "MyClass.kt").setAttribute(CODE_LINE_NUMBER, 12L));
        var hash3 = emit("com.example.MyClass", Severity.INFO, b -> b.setBody("Hello Alice")
                .setAttribute(CODE_FILE_PATH, "MyClass.kt").setAttribute(CODE_LINE_NUMBER, 13L));

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash2).isNotEqualTo(hash3);
    }

    @Test
    @DisplayName("Record without template, code attributes and body produces a valid hash")
    void nothingToIdentifyTheSite() {
        assertThat(emit("com.example.MyClass", Severity.INFO, b -> b)).isNotNull();
    }

    private String emit(String loggerName, Severity severity, UnaryOperator<LogRecordBuilder> customizer) {
        exporter.reset();
        customizer.apply(loggerProvider.get(loggerName).logRecordBuilder().setSeverity(severity)).emit();
        var records = exporter.getFinishedLogRecordItems();
        assertThat(records).hasSize(1);
        return records.get(0).getAttributes().get(AttributeKey.stringKey(LOG_SITE_HASH.getKey()));
    }
}
