rootProject.name = "log-site-hash-kotlin-logging-with-plugin-sample"

includeBuild("../../") {
    dependencySubstitution {
        substitute(module("dev.nemecec.logsitehash:lsh-logback-gelf")).using(project(":lib:java:logback-gelf"))
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
