plugins {
    java
}

group = "de.illunium"
version = "0.1.0-rc.1"
val pluginVersion = version.toString()
val paperApi = "26.3.build.207-stable"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "PaperMC"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApi")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")

    testImplementation("io.papermc.paper:paper-api:$paperApi")
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.4")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.compileJava {
    options.encoding = "UTF-8"
    options.release = 25
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.processResources {
    inputs.property("version", pluginVersion)
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.jar {
    archiveBaseName = "IlluniumCustom"
    manifest {
        attributes(
            "Implementation-Title" to "IlluniumCustom",
            "Implementation-Version" to pluginVersion,
            "Built-Against-Paper-API" to paperApi,
            "Built-With-Java" to "25",
        )
    }
    // Bundle sqlite-jdbc unrelocated: relocation would break its JNI bindings.
    from(configurations.runtimeClasspath.map { classpath -> classpath.map { zipTree(it) } })
    exclude("META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/*/module-info.class", "module-info.class")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Opt-in test plugin, for a disposable Paper server only; never included in release artifacts.
tasks.register<Jar>("paperProbeJar") {
    dependsOn(tasks.jar, tasks.testClasses)
    archiveFileName = "IlluniumGroupChestProbe.jar"
    destinationDirectory = layout.buildDirectory.dir("paper-probe")
    from(tasks.jar.map { zipTree(it.archiveFile) }) { exclude("plugin.yml") }
    from(sourceSets.test.get().output) { include("**/GroupChestPaperProbe*.class") }
    val descriptor = layout.buildDirectory.file("paper-probe-plugin/plugin.yml")
    doFirst {
        descriptor.get().asFile.apply {
            parentFile.mkdirs()
            writeText("name: IlluniumGroupChestProbe\nversion: test\nmain: de.illunium.custom.groups.GroupChestPaperProbe\napi-version: '26.3'\n")
        }
    }
    from(descriptor)
}
