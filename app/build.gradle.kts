plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Sur la CI, chaque build reçoit le numéro d'exécution GitHub Actions comme versionCode :
// il augmente à chaque push, ce qui permet à l'app de savoir si une version plus récente existe.
// En local, la valeur reste 1.
val ciBuildNumber: Int? = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()

android {
    namespace = "fr.baptiste.bank"
    compileSdk = 37

    defaultConfig {
        applicationId = "fr.baptiste.bank"
        minSdk = 26
        targetSdk = 37
        versionCode = ciBuildNumber ?: 1
        versionName = "0.1.${ciBuildNumber ?: 0}"

        // Dépôt GitHub dont les releases servent de source aux mises à jour intégrées.
        buildConfigField("String", "UPDATE_REPOSITORY", "\"Baptiste-Rd/bank\"")
    }

    signingConfigs {
        // Android refuse d'installer une mise à jour signée par une autre clé.
        // La clé de debug par défaut étant recréée à chaque exécution de la CI,
        // on utilise une clé dédiée, versionnée dans le dépôt.
        getByName("debug") {
            storeFile = rootProject.file("signing/debug-updates.jks")
            storePassword = "android"
            keyAlias = "bank-debug"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
    // Schéma de la base exporté en JSON : sert de référence pour les futures migrations.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
}
