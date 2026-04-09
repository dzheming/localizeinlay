import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.13.1"
}

group = "com.zmabel.localizeinlay"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    intellijPlatform {
        // Use intellijIdea() for 2025.3+; intellijIdeaCommunity() is only for versions before 2025.3.
        //intellijIdea(providers.gradleProperty("platformVersion").orElse("2024.3.7"))
        intellijIdeaCommunity(providers.gradleProperty("platformVersion").orElse("2024.3.7"))
    }
}

intellijPlatform {
    buildSearchableOptions = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild").orElse("243")
            untilBuild = providers.gradleProperty("pluginUntilBuild").orElse("262.*")
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
