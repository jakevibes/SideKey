plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    // Kotlin 2.x ships the Compose compiler as a Kotlin plugin, versioned with
    // the language rather than separately.
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
