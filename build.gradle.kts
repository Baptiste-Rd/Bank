// Les plugins sont déclarés ici sans être appliqués, afin que tous les modules
// partagent la même version du plugin Kotlin (AGP 9 utilise le Kotlin intégré).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
