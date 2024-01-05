import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
}

group = "com.alexgwyn.phototshelper"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.bytedeco:javacv:1.5.9")
    implementation("org.bytedeco:javacv-platform:1.5.9")
    implementation("org.bytedeco:tesseract-platform:5.3.1-1.5.9")
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "PhotoTimestampHelper"
            packageVersion = "1.0.0"
        }
    }
}
