# Log Site Hashing

**Log site hashing** is a technique for grouping log messages that originate from the same place in source code, even when the messages contain different runtime parameter values.

## The Problem

When logging with parameterized messages:

```java
log.info("Order {} processed for customer {}", orderId, customerId);  // Java/SLF4J
```
```python
logger.info("Order %s processed for customer %s", order_id, customer_id)  # Python
```

The log system receives fully formatted messages like:
- `Order 12345 processed for customer alice`
- `Order 67890 processed for customer bob`

These are clearly the same *type* of message, but searching and grouping them in a log management system (Graylog, Coralogix, Elasticsearch, etc.) requires pattern matching or manual queries. In high-throughput systems, this makes it hard to:

- **Count** how many times a specific log statement fires
- **Group** related messages for alerting or dashboards
- **Find** all occurrences of a particular log site across services
- **Track** changes in log frequency over time (anomaly detection)

## The Solution

Compute a deterministic hash from **stable attributes of the log site** (such as source location, message template, log level) and attach it as a structured field to every log message.

For the example above, both messages would carry the same hash because they originate from the same log statement in the source code.

### What goes into the hash?

The goal is to combine enough information to uniquely identify a log statement in code (a site). The exact inputs depend on your tech stack, but there are two broad approaches:

**Approach 1: Hash the log site identity directly**

If your platform makes call-site information readily available (source file, line number), you can hash that directly:

| Input                      | Purpose                                                |
|----------------------------|--------------------------------------------------------|
| **Source file** + **line** | Uniquely identifies the log statement in source code   |
| **Log level**              | Same location at INFO vs WARN is a different statement |

Whether this is practical depends on how easily and cheaply the logging framework can attach the correct call-site information (the location of the log statement in application code, not inside the logging library):

- **Rust** (`log`, `tracing`) -- `file!()` / `line!()` are compile-time macros that expand at the call site, so the log record always carries the correct location at zero runtime cost. Note: if you wrap log calls in a *function* (not a macro), the recorded location will be inside the wrapper.
- **Go** (`log/slog`) -- `runtime.Callers()` captures a single program counter at low cost. Opt-in via `HandlerOptions{AddSource: true}`. The framework skips its own frames to report the application code's call site. Wrapper functions require manually adjusting the frame skip count.
- **Python** (`logging`) -- `findCaller()` walks the stack via `sys._getframe()` and skips logging internals to find the application code's location. The call site is always captured on the `LogRecord` (moderate cost). Wrapper functions break this; fix with `stacklevel=N` (Python 3.8+).

Note that in some environments (e.g. Node.js, JVM), obtaining file/line requires capturing a full stack trace, making this approach impractical. In .NET, the `[CallerFilePath]`/`[CallerLineNumber]` compiler attributes are zero-cost, but none of the major logging frameworks (Microsoft.Extensions.Logging, Serilog, NLog) use them in their API signatures by default.

**Approach 2: Hash the message template**

When call-site information is expensive to obtain at runtime (e.g. on the JVM, where it requires capturing a stack trace), you can hash the message template together with contextual attributes instead:

| Input                 | Purpose                                                                                         |
|-----------------------|-------------------------------------------------------------------------------------------------|
| **Message template**  | The core identity of the log statement (e.g. `"Order {} processed for customer {}"`)            |
| **Logger name**       | Distinguishes the same template used in different classes (Java convention - use FQCN of class) |
| **Log level**         | Same template at INFO vs WARN is a different statement                                          |
| **Marker** (optional) | Framework-specific tag (e.g. SLF4J markers) that further distinguishes log statements           |

This approach works well with SLF4J/Logback (Java) and kotlin-logging (Kotlin), where the message template is available to the logging framework before parameter substitution.

**General principles:**
- Use a fast, deterministic hash algorithm (e.g. Murmur3, xxHash) and represent the result as a UUID for easy storage and querying
- Include the **log level** -- the same message at different severity levels represents different statements
- Choose inputs that are **stable across deployments** -- avoid runtime-specific data like thread IDs or timestamps
- The hash should be the same regardless of the parameter values passed to the log statement

**Hashes are comparable only within one integration.** The inputs differ per stack: Logback uses `Level.toInt()` (INFO is `20000`), OpenTelemetry uses the severity number (INFO is `9`), Python uses `levelno` (INFO is `20`), and the Rust samples use the numeric value of the `log` level or the OpenTelemetry severity. The Java libraries write the 128-bit hash into a UUID with the two 64-bit halves in the opposite order to the Rust and Python samples. The same log statement therefore has different hashes in different integrations, and each integration keeps its own hash values.

### What gets added to each log message?

The essential field is the hash itself:

| Field           | Example value                          | Description                                 |
|-----------------|----------------------------------------|---------------------------------------------|
| `log_site_hash` | `49cd2439-b38c-543a-5832-8233abc4a875` | Deterministic UUID identifying the log site |

In addition, it is useful to attach the individual inputs that went into the hash as separate fields. This makes the hash human-readable in a log management UI and allows filtering or grouping by each input independently. For example, when using approach 2 (message template based):

| Field          | Example value                        | Description                                       |
|----------------|--------------------------------------|---------------------------------------------------|
| `msg_template` | `Order {} processed for customer {}` | The raw message template before formatting        |
| `logger_name`  | `com.example.OrderService`           | Logger name (often already present in log events) |
| `log_level`    | `INFO`                               | Log level (often already present in log events)   |
| `marker`       | `AUDIT`                              | SLF4J marker, if used (often already present)     |

Many of these (logger name, level, marker) are typically already part of the log event, so only the message template is truly new. The template lets you immediately relate the hash to a specific log statement in the source code.

## Implementation Guide

The implementation depends on your language and logging stack.

### Java + SLF4J + Logback

With SLF4J and Logback, the message template is already available. When you write:

```java
log.info("Hello {}", name);
```

Logback's `ILoggingEvent` provides both:
- `getMessage()` → `"Hello {}"` (the template)
- `getFormattedMessage()` → `"Hello world"` (the formatted message)

This means you can implement log site hashing entirely at the **appender/encoder level** without changing any application code.

#### Using logback-gelf (for Graylog/GELF output)

[logback-gelf](https://github.com/osiegmar/logback-gelf) supports custom field mappers via its `GelfFieldMapper` interface. This repo provides a ready-to-use implementation: [`LogSiteHashFieldMapper`](lib/java/logback-gelf/src/main/java/dev/nemecec/logsitehash/logbackgelf/LogSiteHashFieldMapper.java) computes a hash from the message template, logger name, level, and marker, and attaches both the hash and the template to the GELF message. It uses [`LogSiteHash`](lib/java/hashing/src/main/java/dev/nemecec/logsitehash/hashing/LogSiteHash.java) and [`DeterministicHashing`](lib/java/hashing/src/main/java/dev/nemecec/logsitehash/hashing/DeterministicHashing.java) (Murmur3-based) to produce a stable UUID.

The core of the hash computation:

```java
return LogSiteHash.of(
    event.getMessage(),         // message template (before formatting)
    event.getLoggerName(),      // e.g. "com.example.OrderService"
    optionalLevel.map(Level::toInt).orElse(null),
    optionalMarker.map(Marker::getName).orElse(null)
);
```

`LogSiteHash.of` joins the four parts as text and hashes the result with `DeterministicHashing.hash`.

To use it, configure the field mapper in `logback.xml`:

```xml
<appender name="GELF" class="de.siegmar.logbackgelf.GelfTcpAppender">
  <graylogHost>${LOG_SERVER}</graylogHost>
  <graylogPort>${LOG_SERVER_PORT}</graylogPort>
  <encoder class="de.siegmar.logbackgelf.GelfEncoder">
    <includeMarker>true</includeMarker>
    <includeMdcData>true</includeMdcData>
    <includeRootCauseData>true</includeRootCauseData>

    <!-- Add log site hash fields to every GELF message -->
    <fieldMapper class="dev.nemecec.logsitehash.logbackgelf.LogSiteHashFieldMapper">
      <logSiteHashFieldName>log_site_hash</logSiteHashFieldName>
      <messageTemplateFieldName>msg_template</messageTemplateFieldName>
    </fieldMapper>
  </encoder>
</appender>
```

No changes to application logging code are needed.

### Kotlin + kotlin-logging + Logback

With [kotlin-logging](https://github.com/oshai/kotlin-logging), log messages are typically constructed using lambdas with string interpolation:

```kotlin
logger.info { "Order $orderId processed for customer $customerId" }
```

The lambda is evaluated at runtime, producing only the formatted message. **There is no message template** available to the logging framework by default.

#### The challenge

Unlike SLF4J's `log.info("template {}", param)` pattern, Kotlin's `logger.info { "template $param" }` is a string template. The compiler turns it into code that builds the formatted string when the lambda runs. By the time Logback sees the event, `getMessage()` returns the fully formatted string and the template is lost.

#### The solution: a Kotlin compiler plugin

The [kotlin-logging-compiler-plugin](https://github.com/nemecec/kotlin-logging-compile-time-plugin) solves this by transforming logging calls at compile time. It rewrites:

```kotlin
logger.info { "Hello, $name" }
```

into:

```kotlin
logger.at(Level.INFO) {
    message = "Hello, $name"
    internalCompilerData = KLoggingEventBuilder.InternalCompilerData(
        messageTemplate = "\"Hello, \$name\"",
        className = "com.example.MyClass",
        methodName = "myMethod",
        fileName = "MyClass.kt",
        lineNumber = 42
    )
}
```

This makes the message template, file name, line number, class name, and method name available at runtime -- all captured at compile time with zero runtime cost. The Logback backend can then use these for hashing (and for caller data, which would otherwise require an expensive stack trace on the JVM).

#### Setup

1. Set up [kotlin-logging](https://github.com/oshai/kotlin-logging) and the [kotlin-logging-compiler-plugin](https://github.com/nemecec/kotlin-logging-compile-time-plugin) following the [plugin's usage instructions](https://github.com/nemecec/kotlin-logging-compile-time-plugin#usage).
2. Configure `LogSiteHashFieldMapper` in `logback.xml` as described in the [Java section above](#using-logback-gelf-for-grayloggelf-output). The compiler plugin captures the message template at compile time, and kotlin-logging passes it through to Logback, so `ILoggingEvent.getMessage()` returns the template instead of the formatted message. The field mapper then works identically to the Java case.

The sample [`samples/kotlin-logging-with-plugin`](samples/kotlin-logging-with-plugin/) shows this setup.

#### Without the compiler plugin

Without the plugin, a lambda such as `logger.info { "Processing item $i" }` gives a new hash for every value of `i`, because Logback receives the formatted text. kotlin-logging can keep the template and the values apart when the call uses the `arguments` property of the event builder:

```kotlin
logger.atInfo {
    message = "Processing item {}"
    arguments = arrayOf(i)
}
```

Use the SLF4J backend of kotlin-logging for this form. The direct Logback backend (`kotlin-logging-to-logback=true`) does not substitute the arguments into the formatted message. The sample [`samples/kotlin-logging-without-plugin`](samples/kotlin-logging-without-plugin/) shows both forms side by side and tests that the lambda form gives different hashes and the `arguments` form gives one hash.

### Rust + `log` / `tracing`

In Rust, call-site information (`file!()`, `line!()`) is captured at compile time via macros at zero runtime cost. Both the [`log`](https://crates.io/crates/log) and [`tracing`](https://crates.io/crates/tracing) crates make this information available on log records via `Record::file()` / `Record::line()` and `Metadata::file()` / `Metadata::line()` respectively.

This means Rust naturally uses **approach 1** (hash the source location directly) rather than the template-based approach used on the JVM. No special framework changes are needed to access the hash inputs.

See the [Rust + OpenTelemetry](#rust--opentelemetry) section below for how to compute and attach the hash in an OTel pipeline.

### Python + `logging`

Python's standard `logging` module captures call-site information (`pathname`, `lineno`, `funcName`) automatically on every `LogRecord` via `sys._getframe()`. This means Python also uses **approach 1** (hash the source location directly).

The simplest way to compute and attach the hash is a `logging.Filter` on each handler. Filters are explicitly allowed to modify records in-place, and a handler runs its filters before it formats the record:

```python
import logging
import uuid

import mmh3

def murmur3_uuid(text: str) -> uuid.UUID:
    lo, hi = mmh3.mmh3_x64_128_utupledigest(text.encode("utf-8"), 0)
    return uuid.UUID(int=(hi << 64) | lo)

class LogSiteHashFilter(logging.Filter):
    def filter(self, record):
        site_key = f"{record.pathname}:{record.lineno}:{record.levelno}"
        record.log_site_hash = str(murmur3_uuid(site_key))
        return True

# Attach to the handler -- it sees records from every logger
handler = logging.StreamHandler()
handler.addFilter(LogSiteHashFilter())
logging.getLogger().addHandler(handler)
```

> **Note:** Do not attach the filter to the root logger. A filter on a logger runs only for records that this logger creates, so records from `logging.getLogger(__name__)` would not get the hash.

> **Note:** `record.pathname` is an absolute path. The hash changes when the code runs from another directory or from another Python installation. Use `record.module` or `record.name` instead of the path if the hash must stay the same between deployments.

No changes to application logging code are needed. GELF handlers (e.g. `pygelf`, `graypy`) that serialize `record.__dict__` will automatically include `log_site_hash` as an additional field.

See the [Python + OpenTelemetry](#python--opentelemetry) section below for OTel integration.

## OpenTelemetry

OpenTelemetry's log data model supports custom attributes on log records, making it a natural fit for log site hashing.

### Relevant semantic conventions

OpenTelemetry already defines standard attributes for code location:

| Attribute            | Description                     |
|----------------------|---------------------------------|
| `code.file.path`    | Source file path                |
| `code.line.number`  | Line number in the source file  |
| `code.function.name`| Fully-qualified function name   |

The names above are the current ones. Older SDK versions and some bridge crates use other names, for example `code.filepath` and `code.lineno`. Additionally, the OpenTelemetry Java instrumentation uses `log.body.template` for the message template (not yet part of the official semantic conventions registry).

These existing attributes cover the inputs for both hashing approaches described above. The `log_site_hash` itself would be a custom attribute.

### Java + Logback + OpenTelemetry

The OpenTelemetry Logback appender (`io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0`) already supports capturing the relevant inputs:

```xml
<appender name="otel" class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender">
  <!-- Captures ILoggingEvent.getMessage() as "log.body.template" attribute -->
  <captureTemplate>true</captureTemplate>
  <!-- Captures code.file.path, code.line.number, code.function.name -->
  <captureCodeAttributes>true</captureCodeAttributes>
</appender>
```

The appender sets `log.body.template` only for messages that have arguments. A call such as `log.info("Done")` has no template attribute. kotlin-logging lambdas have none either, because they pass no arguments to Logback, with or without the compiler plugin. Enable `captureCodeAttributes` for them. With the plugin, the code attributes are free. Without the plugin, use the `arguments` form so that the template is captured.

> **Note:** `captureCodeAttributes` has a performance cost with Java since it captures a stack trace. When using the [kotlin-logging-compiler-plugin](https://github.com/nemecec/kotlin-logging-compile-time-plugin), the caller data is available at zero cost.

Unlike logback-gelf, the OTel Logback appender does not have a field mapper extension point. To compute and attach the `log_site_hash`, this repo provides [`LogSiteHashLogRecordProcessor`](lib/java/opentelemetry/src/main/java/dev/nemecec/logsitehash/opentelemetry/LogSiteHashLogRecordProcessor.java) -- a custom `LogRecordProcessor` that reads the template, severity, and logger name from the log record, computes the hash, and sets the `log_site_hash` attribute.

The processor identifies the log site with the first of these that it finds: the `log.body.template` attribute, then `code.file.path` and `code.line.number`, then the log record body. For a message without arguments, the body is the template.

Add the dependency `dev.nemecec.logsitehash:lsh-opentelemetry` and register the processor on the `SdkLoggerProvider` before the exporter processor, so the hash is present when logs are exported:

```java
SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
    .addLogRecordProcessor(new LogSiteHashLogRecordProcessor())
    .addLogRecordProcessor(BatchLogRecordProcessor.builder(exporter).build())
    .build();
OpenTelemetryAppender.install(OpenTelemetrySdk.builder().setLoggerProvider(loggerProvider).build());
```

With the OpenTelemetry Java agent or SDK autoconfiguration, register the processor through an `AutoConfigurationCustomizerProvider`.

### Rust + OpenTelemetry

The OTel Rust SDK (`opentelemetry-sdk`) provides a `LogProcessor` trait with a mutable reference to the log record:

```rust
fn emit(&self, data: &mut SdkLogRecord, instrumentation: &InstrumentationScope);
```

This means you can read existing attributes and add new ones (like a computed hash) -- the same pattern as Java's `LogRecordProcessor`.

#### Bridge crates

Two crates connect Rust logging to OTel:

| Source | Bridge crate |
|---|---|
| `log` crate | `opentelemetry-appender-log` |
| `tracing` crate | `opentelemetry-appender-tracing` |

Both support an `experimental_metadata_attributes` feature flag that forwards compile-time call-site information as OTel attributes. The attribute names differ per crate:

| Bridge crate | File | Line |
|---|---|---|
| `opentelemetry-appender-log` | `code.file.path` | `code.line.number` |
| `opentelemetry-appender-tracing` | `code.filepath` | `code.lineno` |

#### Approach: custom `LogProcessor`

1. Enable `experimental_metadata_attributes` on the appender crate so the file and line attributes are populated
2. Write a `LogProcessor` that reads those attributes, computes the hash, and adds it:

```rust
impl LogProcessor for LogSiteHashProcessor {
    fn emit(&self, data: &mut SdkLogRecord, _scope: &InstrumentationScope) {
        let mut file: Option<String> = None;
        let mut line: Option<i64> = None;
        for (key, value) in data.attributes_iter() {
            match (key.as_str(), value) {
                ("code.file.path" | "code.filepath", AnyValue::String(s)) => file = Some(s.to_string()),
                ("code.line.number" | "code.lineno", AnyValue::Int(n)) => line = Some(*n),
                _ => {}
            }
        }
        let severity = data.severity_number().unwrap_or(Severity::Info) as i32;
        let input = format!("{}:{}:{}", file.as_deref().unwrap_or(""), line.unwrap_or(0), severity);
        let hash = compute_hash(&input); // your hashing function
        data.add_attribute(
            Key::from_static_str("log_site_hash"),
            AnyValue::String(hash.into()),
        );
    }
    // ... other trait methods ...
}
```

3. Chain it before the exporter processor in the `LoggerProvider`

> **Note:** `file!()` of your own crate is relative to the crate root, so the hash is stable. `file!()` of a dependency crate is an absolute path in the Cargo registry, so the hash depends on the machine.

> **Note:** The `experimental_metadata_attributes` feature flag is still experimental (the OTel Rust SDK is pre-1.0). If it stabilises, this becomes a clean, supported solution.

### Python + OpenTelemetry

The OTel Python SDK (`opentelemetry-sdk`) provides `LoggingHandler` -- a `logging.Handler` subclass that bridges Python's `logging` module into the OTel pipeline. It automatically forwards `pathname`, `lineno`, and `funcName` from the Python `LogRecord` as OTel attributes (`code.file.path`, `code.line.number`, `code.function.name`).

**The simplest approach** is to use the same `logging.Filter` from the [Python section above](#python--logging), attached to the `LoggingHandler`. The `LoggingHandler` forwards all non-reserved attributes from `record.__dict__` as OTel log attributes, so `record.log_site_hash` set by the filter will automatically appear in the OTel log record. No OTel-specific code needed.

**Alternative: custom `LogRecordProcessor`**

The Python SDK also has a `LogRecordProcessor` with an `on_emit(log_record)` method that receives a mutable `ReadWriteLogRecord`. You can read the code location attributes and add the hash:

```python
from opentelemetry.sdk._logs import LogRecordProcessor

class LogSiteHashProcessor(LogRecordProcessor):
    def on_emit(self, log_record):
        attrs = log_record.log_record.attributes
        file_path = attrs.get("code.file.path", "")
        line_number = attrs.get("code.line.number", 0)
        site_key = f"{file_path}:{line_number}:{log_record.log_record.severity_number}"
        attrs["log_site_hash"] = str(murmur3_uuid(site_key))  # murmur3_uuid from the Python section above

    def shutdown(self): pass
    def force_flush(self, timeout_millis=30000): return True
```

The `logging.Filter` approach is generally preferred because it works for both GELF and OTel simultaneously with a single piece of code.

## Log Management Backends

Once your log messages carry `log_site_hash` and `msg_template` fields, you can use them in your log management system.

### Graylog

- **Group by hash:** Create dashboard widgets that aggregate by the `log_site_hash` field
- **Alert on frequency:** Set up alerts when a specific hash exceeds a count threshold in a time window
- **Quick filter:** Click on a `log_site_hash` value to find all messages from the same log site
- **Template view:** Use `msg_template` to see the log statement pattern without parameter noise

### Coralogix

- Use `log_site_hash` as a grouping key in Explore, dashboards, and alerting rules
- Build log-frequency anomaly detection on top of hash-based aggregations

### Elasticsearch / OpenSearch

- Use `log_site_hash` as a keyword field for efficient `terms` aggregations
- Build visualizations in Kibana/OpenSearch Dashboards grouped by hash

## Supporting Libraries

Java libraries are available in the [`lib/java/`](lib/java/) directory as Gradle subprojects:

- [`lib/java/hashing/`](lib/java/hashing/) (`dev.nemecec.logsitehash:lsh-hashing`) - `DeterministicHashing` and `LogSiteHash` -- shared Murmur3-based hashing utility and hash input composition
- [`lib/java/logback-gelf/`](lib/java/logback-gelf/) (`dev.nemecec.logsitehash:lsh-logback-gelf`) - `LogSiteHashFieldMapper` -- logback-gelf field mapper (depends on hashing)
- [`lib/java/opentelemetry/`](lib/java/opentelemetry/) (`dev.nemecec.logsitehash:lsh-opentelemetry`) - `LogSiteHashLogRecordProcessor` -- OpenTelemetry log record processor (depends on hashing)

## Related Projects

- [logback-gelf](https://github.com/osiegmar/logback-gelf) - Logback appender for GELF (Graylog), supports custom field mappers ([issue #55](https://github.com/osiegmar/logback-gelf/issues/55) added this)
- [kotlin-logging](https://github.com/oshai/kotlin-logging) - Lightweight Kotlin logging framework ([issue #449](https://github.com/oshai/kotlin-logging/issues/449) discusses template support)
- [kotlin-logging-compiler-plugin](https://github.com/nemecec/kotlin-logging-compile-time-plugin) - Kotlin compiler plugin that captures message templates at compile time
- [Logback](https://logback.qos.ch/) - The logging framework that provides `ILoggingEvent.getMessage()` (message template) vs `getFormattedMessage()` (formatted message)

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
