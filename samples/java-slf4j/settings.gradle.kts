rootProject.name = "log-site-hash-java-slf4j-sample"

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
