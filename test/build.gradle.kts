import java.time.Duration
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

description = "Unit and protocol tests for the common, proxy and spigot halves."

dependencies {
    testImplementation(project(":common"))
    testImplementation(project(":proxy"))
    testImplementation(project(":spigot"))

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Nothing to ship: this module only ever produces test results.
tasks.jar { enabled = false }

tasks.test {
    useJUnitPlatform()

    // A socket test that hangs is worse than one that fails.
    timeout = Duration.ofMinutes(10)

    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        exceptionFormat = TestExceptionFormat.FULL
        showStandardStreams = false
    }

    reports {
        junitXml.required = true
        html.required = true
    }
}
