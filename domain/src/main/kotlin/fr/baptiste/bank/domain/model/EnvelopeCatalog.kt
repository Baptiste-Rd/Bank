package fr.baptiste.bank.domain.model

import fr.baptiste.bank.domain.bank.envelopeNameKey

/*
 * Catalogue de noms d'enveloppes tout faits : on choisit un nom courant plutôt que de le taper, et
 * il arrive avec la catégorie et l'usage qui lui vont (« Loyer » : charge fixe, Logement). Il sert à
 * la création comme au renommage d'une enveloppe. C'est aussi la première source de
 * [suggestCategory] : un nom du catalogue donne toujours la même catégorie.
 */

/** Usage proposé pour un nom du catalogue (les familles du formulaire d'enveloppe). */
enum class IdeaUse { DAILY, FIXED, RARE, SAVINGS }

/** Un nom d'enveloppe tout fait, son thème (pour ranger la liste), sa catégorie et son usage. */
data class EnvelopeIdea(val name: String, val theme: String, val category: String, val use: IdeaUse)

private fun theme(name: String, category: String, vararg ideas: Pair<String, IdeaUse>): List<EnvelopeIdea> =
    ideas.map { (idea, use) -> EnvelopeIdea(idea, name, category, use) }

private val D = IdeaUse.DAILY
private val F = IdeaUse.FIXED
private val R = IdeaUse.RARE
private val S = IdeaUse.SAVINGS

val envelopeCatalog: List<EnvelopeIdea> = listOf(
    theme(
        "Alimentation", "Nourriture",
        "Courses" to D, "Supermarché" to D, "Boulangerie" to D, "Marché" to D, "Boucherie" to D, "Primeur" to D,
        "Bio" to D, "Snacks" to D, "Cantine" to D, "Déjeuners au travail" to D, "Livraison de repas" to D,
        "Restaurant" to D, "Fast-food" to D, "Café" to D, "Bar" to D, "Vin et alcool" to D,
    ),
    theme(
        "Logement", "Logement",
        "Loyer" to F, "Prêt immobilier" to F, "Charges de copropriété" to F, "Électricité" to F, "Gaz" to F,
        "Eau" to F, "Assurance habitation" to F, "Taxe foncière" to R, "Taxe d'habitation" to R, "Ménage" to F,
        "Maison" to D, "Bricolage" to D, "Quincaillerie" to D, "Jardin" to D, "Décoration" to R, "Meubles" to R,
        "Électroménager" to R, "Déménagement" to R,
    ),
    theme(
        "Transports", "Transports",
        "Essence" to D, "Recharge électrique" to D, "Péage" to D, "Parking" to D, "Transports en commun" to F,
        "Navigo" to F, "Train" to R, "Taxi et VTC" to D, "Vélo" to R, "Trottinette" to D, "Entretien voiture" to R,
        "Contrôle technique" to R, "Assurance auto" to F, "Prêt auto" to F, "Lavage auto" to D, "Amendes" to R,
    ),
    theme(
        "Abonnements", "Abonnements",
        "Abonnements" to F, "Téléphone" to F, "Internet" to F, "Streaming vidéo" to F, "Musique" to F,
        "Presse" to F, "Cloud et logiciels" to F, "Jeux en ligne" to F, "Box TV" to F,
    ),
    theme(
        "Santé et soin", "Santé",
        "Pharmacie" to D, "Médecin" to R, "Dentiste" to R, "Opticien" to R, "Mutuelle" to F, "Kiné" to R,
        "Psychologue" to R, "Coiffeur" to R, "Esthétique" to R, "Hygiène et beauté" to D, "Parfum" to R,
    ),
    theme(
        "Loisirs", "Loisirs",
        "Sorties" to D, "Cinéma" to D, "Concerts" to R, "Spectacles" to R, "Musées et expos" to R,
        "Salle de sport" to F, "Sport" to D, "Club et licence" to R, "Livres" to D, "Jeux vidéo" to R,
        "Jeux de société" to R, "Photo" to R, "Musique et instruments" to R, "Hobbies" to D, "Soirées" to D,
    ),
    theme(
        "Shopping", "Shopping",
        "Vêtements" to D, "Chaussures" to R, "Accessoires" to R, "High-tech" to R, "Informatique" to R,
        "Téléphone (achat)" to R, "Papeterie" to D, "Shopping" to D,
    ),
    theme(
        "Cadeaux et fêtes", "Cadeaux",
        "Cadeaux" to R, "Anniversaires" to R, "Noël" to R, "Fête des mères et des pères" to R, "Mariages" to R,
        "Naissances" to R, "Dons" to F, "Associations" to F,
    ),
    theme(
        "Voyages", "Voyages",
        "Vacances" to R, "Week-ends" to R, "Hôtels" to R, "Billets d'avion" to R, "Location de voiture" to R,
        "Ski" to R, "Camping" to R,
    ),
    theme(
        "Enfants", "Enfants",
        "Crèche" to F, "Nounou" to F, "Cantine scolaire" to F, "Fournitures scolaires" to R,
        "Activités des enfants" to R, "Vêtements des enfants" to D, "Argent de poche" to F, "Jouets" to R,
        "Couches et soins bébé" to D,
    ),
    theme(
        "Animaux", "Animaux",
        "Nourriture des animaux" to D, "Vétérinaire" to R, "Assurance animaux" to F, "Garde des animaux" to R,
        "Toilettage" to R,
    ),
    theme(
        "Impôts et banque", "Impôts et frais",
        "Impôt sur le revenu" to F, "Frais bancaires" to F, "Assurance emprunteur" to F, "Assurance vie (frais)" to F,
        "Avocat et notaire" to R, "Retraits d'espèces" to D,
    ),
    theme(
        "Travail et études", "Travail",
        "Formation" to R, "Frais professionnels" to D, "Matériel de travail" to R, "Études" to R, "Permis de conduire" to R,
    ),
    theme(
        "Épargne et projets", "Épargne",
        "Épargne" to S, "Épargne de précaution" to S, "Livret A" to S, "Investissement" to S, "Retraite" to S,
        "Projets" to R, "Apport immobilier" to S, "Voiture (projet)" to R, "Travaux" to R, "Imprévus" to R,
    ),
).flatten()

/** Le nom du catalogue qui correspond exactement à [name] (à la casse et aux accents près), s'il existe. */
fun catalogIdea(name: String): EnvelopeIdea? {
    val key = envelopeNameKey(name)
    if (key.isBlank()) return null
    return envelopeCatalog.firstOrNull { envelopeNameKey(it.name) == key }
}

/** Les noms du catalogue qui contiennent [query] (à la casse et aux accents près) ; tout si [query] est vide. */
fun searchCatalog(query: String): List<EnvelopeIdea> {
    val key = envelopeNameKey(query)
    if (key.isBlank()) return envelopeCatalog
    return envelopeCatalog.filter { envelopeNameKey(it.name).contains(key) || envelopeNameKey(it.theme).contains(key) }
}
