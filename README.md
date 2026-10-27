# Bank

Application Android personnelle de budget par enveloppes. Elle répond à une question :
combien puis-je dépenser aujourd'hui, compte tenu de ce que j'ai décidé pour le mois et pour l'année ?

Les opérations viennent de l'export CSV de la banque (CIC). Tu saisis tes budgets et,
si besoin, une dépense à la main. Les données restent sur le téléphone.

## Structure

| Module | Contenu |
| --- | --- |
| `domain` | Règles de calcul en Kotlin pur (montants, périodes, budget quotidien, projections). Aucune dépendance Android, testé sur la JVM. |
| `app` | Application Android : interface Jetpack Compose, ViewModels, base locale Room. |

## Stack

Kotlin, Jetpack Compose (Material 3), Room, Android Gradle Plugin 9 avec le Kotlin intégré.
Les versions sont centralisées dans `gradle/libs.versions.toml`.

## Commandes

```bash
./gradlew :domain:test          # tests des règles de calcul
./gradlew :app:assembleDebug    # APK de debug dans app/build/outputs/apk/debug/
```

JDK 17 requis.

## Installer l'APK sans Android Studio

Chaque push sur `main` lance la CI GitHub Actions, qui exécute les tests et construit l'APK de debug.
Dans l'onglet **Actions**, ouvre la dernière exécution réussie et télécharge l'artefact `bank-debug-apk`.
Sur le téléphone, ouvre le fichier `.apk` et autorise l'installation depuis cette source.
