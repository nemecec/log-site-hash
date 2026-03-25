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

[logback-gelf](https://github.com/osiegmar/logback-gelf) supports custom field mappers via its `GelfFieldMapper` interface. This repo provides a ready-to-use implementation: [`LogSiteHashFieldMapper`](lib/java/src/main/java/dev/nemecec/logsitehash/LogSiteHashFieldMapper.java) computes a hash from the message template, logger name, level, and marker, and attaches both the hash and the template to the GELF message. It uses [`DeterministicHashing`](lib/java/src/main/java/dev/nemecec/logsitehash/DeterministicHashing.java) (Murmur3-based) to produce a stable UUID.

The core of the hash computation:

```java
return DeterministicHashing.hash(
    event.getMessage()          // message template (before formatting)
    + event.getLoggerName()     // e.g. "com.example.OrderService"
    + optionalLevel.map(Level::toInt).orElse(null)
    + optionalMarker.map(Marker::getName).orElse(null)
);
```

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
    <fieldMapper class="dev.nemecec.logsitehash.LogSiteHashFieldMapper">
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

Unlike SLF4J's `log.info("template {}", param)` pattern, Kotlin's `logger.info { "template $param" }` string template is eagerly evaluated by the compiler at compile time. By the time Logback sees the event, `getMessage()` returns the fully formatted string and the template is lost.

#### The solution: a Kotlin compiler plugin

The [kotlin-logging-compiler-plugin](https://github.com/nickvdyck/kotlin-logging-compiler-plugin) solves this by transforming logging calls at compile time. It rewrites:

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

1. Set up [kotlin-logging](https://github.com/oshai/kotlin-logging) and the [kotlin-logging-compiler-plugin](https://github.com/nemecec/kotlin-logging-compiler-plugin) following the [plugin's usage instructions](https://github.com/nemecec/kotlin-logging-compiler-plugin#usage).
2. Configure `LogSiteHashFieldMapper` in `logback.xml` as described in the [Java section above](#using-logback-gelf-for-grayloggelf-output). The compiler plugin captures the message template at compile time, and kotlin-logging passes it through to Logback, so `ILoggingEvent.getMessage()` returns the template instead of the formatted message. The field mapper then works identically to the Java case.

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

Reference implementations of the hashing and field mapper code are available in the [`lib/`](lib/) directory:

- [`lib/java/`](lib/java/) - `DeterministicHashing` and `LogSiteHashFieldMapper` for Java + Logback + logback-gelf

## Related Projects

- [logback-gelf](https://github.com/osiegmar/logback-gelf) - Logback appender for GELF (Graylog), supports custom field mappers ([issue #55](https://github.com/osiegmar/logback-gelf/issues/55) added this)
- [kotlin-logging](https://github.com/oshai/kotlin-logging) - Lightweight Kotlin logging framework ([issue #449](https://github.com/oshai/kotlin-logging/issues/449) discusses template support)
- [kotlin-logging-compiler-plugin](https://github.com/nickvdyck/kotlin-logging-compiler-plugin) - Kotlin compiler plugin that captures message templates at compile time
- [Logback](https://logback.qos.ch/) - The logging framework that provides `ILoggingEvent.getMessage()` (message template) vs `getFormattedMessage()` (formatted message)

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
