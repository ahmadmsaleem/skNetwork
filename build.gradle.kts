import java.util.concurrent.Callable

plugins {
    java
    id("com.gradleup.shadow") version "9.3.1"
}

val testServer = file("${rootDir.parent}/skNetwork-test-server")

allprojects {
    group = rootProject.property("group") as String
    version = rootProject.property("version") as String
}

subprojects {
    apply(plugin = "java")

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion = JavaLanguageVersion.of(25)
    }

    // so the two halves can never disagree about the version
    tasks.withType<ProcessResources>().configureEach {
        val props = mapOf("version" to project.version.toString())
        inputs.properties(props)
        filesMatching(listOf("plugin.yml", "bungee.yml", "velocity-plugin.json")) { expand(props) }

    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 25
        options.compilerArgs.add("-Xlint:deprecation")
    }
}

// The platform halves are compileOnly against their own APIs, so nothing
// external leaks into the jar.
val bundle: Configuration by configurations.creating

dependencies {
    bundle(project(":common"))
    bundle(project(":proxy"))
    bundle(project(":spigot"))
}

// bStats is the only thing outside the three modules that ends up in the jar. The
// platform APIs are all compileOnly, so nothing else can leak in.

// Paper reads plugin.yml, BungeeCord reads bungee.yml, so each side picks its
// own main class and never loads the other's classes.
tasks.register<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("universalJar") {
    group = "build"
    description = "Builds the single jar that goes on the proxy and every backend."
    archiveBaseName = "skNetwork"
    archiveClassifier = ""

    configurations = listOf(bundle)

    // Required, not a preference. Two plugins carrying an unrelocated org.bstats
    // clash, and whichever loaded first wins.
    relocate("org.bstats", "sknetwork.metrics")

    exclude("META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.RSA")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named("build") { dependsOn("universalJar") }

tasks.register<Copy>("deploy") {
    group = "skNetwork"
    description = "Copies the universal jar into every plugin folder of the test network."
    dependsOn("universalJar")

    from(tasks.named<Jar>("universalJar"))
    into(layout.buildDirectory.dir("deploy-staging"))

    doLast {
        if (!testServer.isDirectory) {
            throw GradleException("Test network not found at $testServer")
        }
        val jar = tasks.named<Jar>("universalJar").get().archiveFile.get().asFile
        listOf("bungeecord", "lobby", "lobby2", "survival").forEach { server ->
            val plugins = File(testServer, "$server/plugins")
            if (!plugins.isDirectory) return@forEach
            jar.copyTo(File(plugins, jar.name), overwrite = true)
            logger.lifecycle("deployed -> $server/plugins/${jar.name}")
        }
    }
}
