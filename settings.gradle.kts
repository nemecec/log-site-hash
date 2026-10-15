dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "log-site-hash"

include("lib:java:hashing")
include("lib:java:logback-gelf")
include("lib:java:opentelemetry")
