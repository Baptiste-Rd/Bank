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

## Installer et mettre à jour l'app

Chaque push sur `main` lance la CI GitHub Actions : tests, APK de debug, puis publication
d'une release `build-N` contenant `bank-debug.apk`. N est aussi le `versionCode` de l'APK.

**Première installation** : sur le téléphone, ouvre la dernière release du dépôt
(onglet **Releases**), télécharge `bank-debug.apk`, ouvre-le et autorise l'installation.

**Mises à jour** : l'app interroge la dernière release au démarrage. Si son numéro est plus
élevé que celui installé, le bouton **Installer** de l'accueil télécharge l'APK et lance
l'installation Android. La première fois, Android demande d'autoriser l'app à installer des
applications.

Les APK sont signés avec la clé `signing/debug-updates.jks`, versionnée pour que chaque build
de la CI porte la même signature (condition imposée par Android pour installer une mise à jour).
C'est une clé de debug, à ne pas réutiliser pour une version publiée.

## Styles d'interface

Deux styles cohabitent, au choix depuis le menu ⋮ :

- **Liquid Glass** (par défaut) : fond liquide animé, cartes et barres en verre qui floutent et
  réfractent ce qui passe dessous, barre d'onglets flottante dont l'indicateur se fait glisser.
  Le flou demande Android 12, la réfraction Android 13.
- **Classique** : Material 3, sans effets.

Les effets de verre viennent de la librairie [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)
(Apache 2.0). Quelques composants de son catalogue d'exemples sont repris et adaptés dans
`app/src/main/kotlin/fr/baptiste/bank/ui/glass/kit/` ; la licence est dans `third_party/`.
