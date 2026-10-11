import org.apache.commons.lang3.SystemUtils

plugins {
    idea
    java
    id("gg.essential.loom") version "0.10.0.+"
    id("dev.architectury.architectury-pack200") version "0.1.3"
}

val baseGroup: String by project
val mcVersion: String by project
val version: String by project
val modid: String by project

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
        // a full JDK 8 (an installed Java 8 runtime can't compile): Gradle fetches Temurin if needed
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
}

loom {
    runConfigs {
        "client" {
            if (SystemUtils.IS_OS_MAC_OSX) vmArgs.remove("-XstartOnFirstThread")
        }
        remove(getByName("server"))
    }
    forge {
        pack200Provider.set(dev.architectury.pack200.java.Pack200Adapter())
    }
}

sourceSets.main {
    output.setResourcesDir(sourceSets.main.flatMap { it.java.classesDirectory })
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:1.8.9")
    mappings("de.oceanlabs.mcp:mcp_stable:22-1.8.9")
    forge("net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9")

    testImplementation("junit:junit:4.13.2")
}

tasks.withType(JavaCompile::class) {
    options.encoding = "UTF-8"
}

tasks.withType(org.gradle.jvm.tasks.Jar::class) {
    archiveBaseName.set("XsozAutomata-1.8.9-PvP")
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("mcversion", mcVersion)
    inputs.property("modid", modid)
    filesMatching(listOf("mcmod.info")) {
        expand(inputs.properties)
    }
}

// Loom's own runClient trips Gradle 8's task checks; this starts the game the same way, through
// Loom's dev launcher and the settings it wrote (.gradle/loom-cache/launch.cfg), on Java 8.
// `./gradlew runGame` plays; XSOZ_SELFTEST=1 ./gradlew runGame runs the in-game self-test.
tasks.register<JavaExec>("runGame") {
    group = "xsoz"
    dependsOn("classes", "downloadAssets")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("net.fabricmc.devlaunchinjector.Main")
    jvmArgs(
        "-Dfabric.dli.config=" + file(".gradle/loom-cache/launch.cfg").absolutePath,
        "-Dfabric.dli.env=client",
        "-Dfabric.dli.main=net.minecraft.launchwrapper.Launch"
    )
    workingDir = file("run")
    doFirst { file("run").mkdirs() }
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(8))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    })
}
