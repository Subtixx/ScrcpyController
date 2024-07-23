@file:Suppress("ktPropBy")

import com.adarshr.gradle.testlogger.theme.ThemeType
import com.github.benmanes.gradle.versions.reporter.PlainTextReporter
import com.github.benmanes.gradle.versions.reporter.result.Result
import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.palantir.gradle.gitversion.VersionDetails
import groovy.lang.Closure
import org.apache.commons.io.FileUtils
import org.jetbrains.changelog.Changelog
import org.w3c.dom.Document
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

val pluginXmlFile = projectDir.resolve("src/main/resources/META-INF/plugin.xml")
val pluginXmlFileBackup = projectDir.resolve("plugin.backup.xml")

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
    }
}

plugins {
    id("java")
    kotlin("jvm") version "1.9.0"
    id("org.jetbrains.intellij") version "1.16.1"
    id("org.jetbrains.changelog") version "2.2.0"
    id("com.github.ben-manes.versions") version "0.50.0"
    id("com.adarshr.test-logger") version "4.0.0"
    id("com.palantir.git-version") version "3.0.0"
    id("com.github.andygoossens.modernizer") version "1.9.0"
    signing
}

val pluginDownloadIdeaSources: String by project
val pluginVersion: String by project
val pluginJavaVersion: String by project
val testLoggerStyle: String by project
val pluginEnableDebugLogs: String by project
val pluginEnforceIdeSlowOperationsAssertion: String by project
val pluginClearSandboxedIDESystemLogsBeforeRun: String by project
val pluginIdeaVersion = detectBestIdeVersion()

version = if (pluginVersion == "auto") {
    val versionDetails: Closure<VersionDetails> by extra
    val lastTag = versionDetails().lastTag
    if (lastTag.startsWith("v", ignoreCase = true)) {
        lastTag.substring(1)
    } else {
        lastTag
    }
} else {
    pluginVersion
}

logger.quiet("Will use IDEA $pluginIdeaVersion and Java $pluginJavaVersion. Plugin version set to $version")
group = "com.codertainment"

val junitVersion = "5.10.3"
val junitPlatformLauncher = "1.10.3"
val archunitVersion = "1.2.2"
val flexmarkVersion = "0.64.8"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:$junitPlatformLauncher")
    testImplementation("com.tngtech.archunit:archunit:$archunitVersion")

    implementation("com.github.vidstige:jadb:v1.2.1")
    implementation("com.vladsch.flexmark:flexmark:$flexmarkVersion")
    implementation("com.vladsch.flexmark:flexmark-java:$flexmarkVersion")
    implementation("com.vladsch.flexmark:flexmark-ext-tables:$flexmarkVersion")
}

intellij {
    downloadSources.set(pluginDownloadIdeaSources.toBoolean() && !System.getenv().containsKey("CI"))
    instrumentCode.set(true)
    sandboxDir.set("${rootProject.projectDir}/.idea-sandbox/${shortenIdeVersion(pluginIdeaVersion)}")
    updateSinceUntilBuild.set(false)
    version.set(pluginIdeaVersion)
}

changelog {
    headerParserRegex.set("(.*)".toRegex())
    itemPrefix.set("*")
}

modernizer {
    includeTestClasses = true
    // Find exclusion names at https://github.com/gaul/modernizer-maven-plugin/blob/master/modernizer-maven-plugin/src/main/resources/modernizer.xml
    exclusions = setOf("java/util/Optional.get:()Ljava/lang/Object;")
}

testlogger {
    try {
        theme = ThemeType.valueOf(testLoggerStyle)
    } catch (e: Exception) {
        theme = ThemeType.PLAIN
        logger.warn(
            "Invalid testLoggerRichStyle value '$testLoggerStyle', " +
                    "will use PLAIN style instead. Accepted values are PLAIN, STANDARD and MOCHA."
        )
    }
    showSimpleNames = true
}

kotlin {
    jvmToolchain(pluginJavaVersion.toInt())
}

tasks {
    register("backupPluginXml") {
        doLast {
            if (pluginXmlFileBackup.exists()) {
                if (!pluginXmlFileBackup.delete()) {
                    throw GradleException("Failed to remove existing plugin xml file backup '${pluginXmlFileBackup}'")
                }
            }
            FileUtils.copyFile(pluginXmlFile, pluginXmlFileBackup)
        }
    }
    register("restorePluginXmlFromBackup") {
        doLast {
            if (!pluginXmlFileBackup.exists()) {
                throw GradleException("Plugin xml file backup '${pluginXmlFileBackup}' is missing")
            }
            if (pluginXmlFile.exists()) {
                if (!pluginXmlFile.delete()) {
                    throw GradleException("Failed to remove non-original plugin xml file backup '${pluginXmlFile}'")
                }
            }
            FileUtils.moveFile(pluginXmlFileBackup, pluginXmlFile)
        }
    }
    register("showGeneratedPlugin") {
        doLast {
            logger.quiet(
                "--------------------------------------------------\n" +
                        "Generated: " + projectDir.resolve("build/distributions/").list().contentToString()
                    .replace("[", "").replace("]", " ").trim()
                        + "\n--------------------------------------------------"
            )
        }
    }
    register("clearSandboxedIDESystemLogs") {
        doFirst {
            if (pluginClearSandboxedIDESystemLogsBeforeRun.toBoolean()) {
                val sandboxLogDir =
                    File("${rootProject.projectDir}/.idea-sandbox/${shortenIdeVersion(pluginIdeaVersion)}/system/log/")
                try {
                    if (sandboxLogDir.exists() && sandboxLogDir.isDirectory) {
                        FileUtils.deleteDirectory(sandboxLogDir)
                        logger.quiet("Deleted sandboxed IDE's log folder $sandboxLogDir")
                    }
                } catch (e: Exception) {
                    logger.warn("Failed do delete sandboxed IDE's log folder $sandboxLogDir - ignoring")
                }
            }
        }
    }
    withType<JavaCompile> {
        sourceCompatibility = pluginJavaVersion
        targetCompatibility = pluginJavaVersion
        options.compilerArgs = listOf("-Xlint:deprecation")
        options.encoding = "UTF-8"
    }
    withType<Test> {
        useJUnitPlatform()

        // avoid JBUIScale "Must be precomputed" error, because IDE is not started (LoadingState.APP_STARTED.isOccurred is false)
        jvmArgs("-Djava.awt.headless=true")

        // classpath indexing is not needed during unit tests
        // also, disabled to avoid useless 'NoSuchFileException: build/instrumented/instrumentCode/classpath.index.tmp' warnings
        systemProperties("idea.classpath.index.enabled" to false)
    }
    withType<DependencyUpdatesTask> {
        checkForGradleUpdate = true
        gradleReleaseChannel = "current"
        revision = "release"
        rejectVersionIf {
            isNonStable(candidate.version)
        }
        outputFormatter = closureOf<Result> {
            unresolved.dependencies.removeIf {
                val coordinates = "${it.group}:${it.name}"
                coordinates.startsWith("unzipped.com") || coordinates.startsWith("com.jetbrains:ideaI")
            }
            PlainTextReporter(project, revision, gradleReleaseChannel)
                .write(System.out, this)
        }
    }

    runIde {
        dependsOn("clearSandboxedIDESystemLogs")

        maxHeapSize = "1g" // https://docs.gradle.org/current/dsl/org.gradle.api.tasks.JavaExec.html
        // force detection of slow operations in EDT when playing with sandboxed IDE (SlowOperations.assertSlowOperationsAreAllowed)
        if (pluginEnforceIdeSlowOperationsAssertion.toBoolean()) {
            jvmArgs("-Dide.slow.operations.assertion=true")
        }

        if (pluginEnableDebugLogs.toBoolean()) {
            systemProperties(
                "idea.log.debug.categories" to "#$group"
            )
        }

        autoReloadPlugins.set(false)

        // If any warning or error with missing --add-opens, wait for the next gradle-intellij-plugin's update that should sync
        // with https://raw.githubusercontent.com/JetBrains/intellij-community/master/plugins/devkit/devkit-core/src/run/OpenedPackages.txt
        // or do it manually
    }
    buildSearchableOptions {
        enabled = false
    }
    buildPlugin {
        finalizedBy("proguard", "restorePluginXmlFromBackup")
    }
    patchPluginXml {
        dependsOn("backupPluginXml")
        changeNotes.set(provider {
            with(changelog) {
                renderItem(getLatest(), Changelog.OutputType.HTML)
            }
        })
        pluginDescription.set(provider {
            File(projectDir, "description.html").readText()
        })
    }
    publishPlugin {
        token.set(System.getenv("JLE_IJ_PLUGINS_PUBLISH_TOKEN"))
    }
}

fun isNonStable(version: String): Boolean {
    if (listOf("RELEASE", "FINAL", "GA").any { version.uppercase().endsWith(it) }) {
        return false
    }
    return listOf(
        "alpha",
        "Alpha",
        "ALPHA",
        "b",
        "beta",
        "Beta",
        "BETA",
        "rc",
        "RC",
        "M",
        "EA",
        "pr",
        "atlassian"
    ).any {
        "(?i).*[.-]${it}[.\\d-]*$".toRegex().matches(version)
    }
}

/** Return an IDE version string without the optional PATCH number.
 * In other words, replace IDE-MAJOR-MINOR(-PATCH) by IDE-MAJOR-MINOR. */
fun shortenIdeVersion(version: String): String {
    if (version.contains("SNAPSHOT", ignoreCase = true)) {
        return version
    }
    val matcher = Regex("[A-Za-z]+[\\-]?[0-9]+[\\.]{1}[0-9]+")
    return try {
        matcher.findAll(version).map { it.value }.toList()[0]
    } catch (e: Exception) {
        logger.warn("Failed to shorten IDE version $version: ${e.message}")
        version
    }
}

/** Find latest IntelliJ stable version from JetBrains website. Result is cached locally for 24h. */
fun findLatestStableIdeVersion(): String {
    val t1 = System.currentTimeMillis()
    val definitionsUrl = URL("https://www.jetbrains.com/updates/updates.xml")
    val cachedLatestVersionFile = File(System.getProperty("java.io.tmpdir") + "/jle-ij-latest-version.txt")
    var latestVersion: String
    try {
        if (cachedLatestVersionFile.exists()) {
            val cacheDurationMs =
                Integer.parseInt(project.findProperty("pluginIdeaVersionCacheDurationInHours") as String) * 60 * 60_000
            if (cachedLatestVersionFile.exists() && cachedLatestVersionFile.lastModified() < (System.currentTimeMillis() - cacheDurationMs)) {
                logger.quiet("Cache expired, find latest stable IDE version from $definitionsUrl then update cached file $cachedLatestVersionFile")
                latestVersion = getOnlineLatestStableIdeVersion(definitionsUrl)
                cachedLatestVersionFile.delete()
                Files.writeString(cachedLatestVersionFile.toPath(), latestVersion, Charsets.UTF_8)

            } else {
                logger.quiet("Find latest stable IDE version from cached file $cachedLatestVersionFile")
                latestVersion = Files.readString(cachedLatestVersionFile.toPath())!!
            }

        } else {
            logger.quiet("Find latest stable IDE version from $definitionsUrl")
            latestVersion = getOnlineLatestStableIdeVersion(definitionsUrl)
            Files.writeString(cachedLatestVersionFile.toPath(), latestVersion, Charsets.UTF_8)
        }

    } catch (e: Exception) {
        if (cachedLatestVersionFile.exists()) {
            logger.warn("Error: ${e.message}. Will find latest stable IDE version from cached file $cachedLatestVersionFile")
            latestVersion = Files.readString(cachedLatestVersionFile.toPath())!!
        } else {
            throw RuntimeException(e)
        }
    }
    if (logger.isDebugEnabled) {
        val t2 = System.currentTimeMillis()
        logger.debug("Operation took ${t2 - t1} ms")
    }
    return latestVersion
}

fun getIdeName(ide: String): String {
    return when (ide) {
        "IC" -> "IntelliJ IDEA"
        "IU" -> "IntelliJ IDEA"
        else -> ide
    }
}

fun getIdeIdentifier(ide: String): String {
    val regex = Regex("(\\w+?)-.*")
    val product: String? = regex.find(ide)?.groups?.get(1)?.value
    return if (product != null) {
        product
    } else {
        ide
    }
}

/** Find latest IntelliJ stable version from given url. */
fun getOnlineLatestStableIdeVersion(definitionsUrl: URL): String {
    val definitionsStr = readRemoteContent(definitionsUrl)
    val builderFactory = DocumentBuilderFactory.newInstance()
    val builder = builderFactory.newDocumentBuilder()
    val xmlDocument: Document = builder.parse(ByteArrayInputStream(definitionsStr.toByteArray()))
    val xPath = XPathFactory.newInstance().newXPath()
    val expression =
        "/products/product[@name='IntelliJ IDEA']/channel[@id='IC-IU-RELEASE-licensing-RELEASE']/build[1]/@version"
    return xPath.compile(expression).evaluate(xmlDocument, XPathConstants.STRING) as String
}

/** Read a remote file as String. */
fun readRemoteContent(url: URL): String {
    val t1 = System.currentTimeMillis()
    val content = StringBuilder()
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "GET"
    BufferedReader(InputStreamReader(conn.inputStream)).use { rd ->
        var line: String? = rd.readLine()
        while (line != null) {
            content.append(line)
            line = rd.readLine()
        }
    }
    val t2 = System.currentTimeMillis()
    logger.quiet("Download $url, took ${t2 - t1} ms (${content.length} B)")
    return content.toString()
}

/** Get IDE version from gradle.properties or, of wanted, find latest stable IDE version from JetBrains website. */
fun detectBestIdeVersion(): String {

    return when (val pluginIdeaVersionFromProps = project.findProperty("pluginIdeaVersion").toString()) {
        "IC-LATEST-STABLE" -> { // IntelliJ IDEA Community
            "IC-${findLatestStableIdeVersion()}"
        }

        "IU-LATEST-STABLE" -> { // IntelliJ IDEA Ultimate
            "IU-${findLatestStableIdeVersion()}"
        }

        else -> {
            val regex = Regex("(\\w+)-LATEST-STABLE")
            val product: String? = regex.find(pluginIdeaVersionFromProps)?.groups?.get(1)?.value

            if (product != null) {
                "$product-${findLatestStableIdeVersion()}"
            } else {
                pluginIdeaVersionFromProps
            }
        }
    }
}
