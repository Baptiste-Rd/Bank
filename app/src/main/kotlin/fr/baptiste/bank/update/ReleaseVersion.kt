package fr.baptiste.bank.update

/** Les releases publiées par la CI s'appellent « build-N », N étant le versionCode de l'APK. */
const val RELEASE_TAG_PREFIX = "build-"

/** Extrait le versionCode d'un tag de release, ou null si le tag ne suit pas le format attendu. */
fun versionCodeFromTag(tag: String): Int? {
    if (!tag.startsWith(RELEASE_TAG_PREFIX)) return null
    return tag.removePrefix(RELEASE_TAG_PREFIX).toIntOrNull()?.takeIf { it > 0 }
}

/** Une version publiée, prête à être téléchargée. */
data class AvailableUpdate(
    val versionCode: Int,
    val tag: String,
    val apkUrl: String,
    val apkSizeBytes: Long,
    val notes: String,
)
