// Module domaine : Kotlin pur, sans dépendance Android.
// Il contient toutes les règles de calcul et se teste sur la JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}
