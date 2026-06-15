plugins {
    application
    id("tai-e.conventions")
    id("maven-publish.conventions")
}

group = projectGroupId
description = projectArtifactId
version = projectVersion

dependencies {
    // Process options
    implementation("info.picocli:picocli:4.7.3")
    // Logger
    implementation("org.apache.logging.log4j:log4j-api:2.20.0")
    implementation("org.apache.logging.log4j:log4j-core:2.20.0")
    // Process YAML configuration files
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.0")
    // Use Soot as frontend
    implementation(files("lib/sootclasses-modified.jar"))
    "org.soot-oss:soot:4.4.1".let {
        // Disable transitive dependencies from Soot in compile classpath
        compileOnly(it) { isTransitive = false }
        testCompileOnly(it) { isTransitive = false }
        runtimeOnly(it)
    }
    // Eliminate SLF4J warning
    implementation("org.slf4j:slf4j-nop:2.0.7")
    // JSR305, for javax.annotation
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    // Use asm to read java class file
    implementation("org.ow2.asm:asm:9.4")
    implementation("org.ow2.asm:asm-commons:9.5")
    implementation("org.ow2.asm:asm-tree:9.5")
    implementation("org.ow2.asm:asm-util:9.5")
    implementation("org.eclipse.jdt:org.eclipse.jdt.core:3.28.0")
    implementation("commons-io:commons-io:2.11.0")


    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.platform:junit-platform-suite")
}

application {
    mainClass.set("pascal.taie.Main")
}

tasks.register<Jar>("fatJar", Jar::class) {
    group = "build"
    description = "Creates a legacy single jar file including Tai-e and all dependencies"
    manifest {
        attributes["Main-Class"] = "pascal.taie.Main"
    }
    archiveBaseName.set("${projectArtifactId}-all")
    from(
        configurations.runtimeClasspath.get().map { file ->
            if (file.isDirectory) file else zipTree(file).matching {
                exclude("META-INF/**/*.RSA")
            }
        }
    )
    from("COPYING", "COPYING.LESSER")
    destinationDirectory.set(rootProject.layout.buildDirectory)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    with(tasks["jar"] as CopySpec)
}

tasks.jar {
    from("COPYING", "COPYING.LESSER")
    destinationDirectory.set(rootProject.layout.buildDirectory)
    archiveBaseName.set(projectArtifactId)
}

tasks.withType<Test> {
    // Uses JUnit5
    useJUnitPlatform()
    // Increases the maximum heap memory of JUnit test process. The default is 512M.
    // (see org.gradle.process.internal.worker.DefaultWorkerProcessBuilder.build)
    maxHeapSize = "5G"
    // Sets the maximum number of test processes to start in parallel.
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1
    // Sets the default classpath for test execution.
    // (see https://docs.gradle.org/current/userguide/upgrading_version_8.html#test_task_default_classpath)
    val test by testing.suites.existing(JvmTestSuite::class)
    testClassesDirs = files(test.map { it.sources.output.classesDirs })
    classpath = files(test.map { it.sources.runtimeClasspath })
}

tasks.test {
    // Excludes test suites from the default test task
    // to avoid running some tests multiple times.
    filter {
        excludeTestsMatching("*TestSuite")
    }
}

task("testTaieTestSuite", type = Test::class) {
    group = "verification"
    description = "Runs the Tai-e test suite"
    filter {
        includeTestsMatching("TaieTestSuite")
    }
}

// Automatically agree the Gradle ToS when running gradle with '--scan' option
extensions.findByName("buildScan")?.withGroovyBuilder {
    setProperty("termsOfServiceUrl", "https://gradle.com/terms-of-service")
    setProperty("termsOfServiceAgree", "yes")
}
