# Refonte UX — règles de l'interface

Référence de travail pour faire évoluer l'interface. Le raisonnement complet (recherche, analyse,
carte mentale, autocritique) est dans le document de refonte partagé avec Baptiste.

## Structure : quatre onglets, une question chacun

Onglets, dans l'ordre : **Résumé** (où j'en suis ce mois ?), **Budget** (où va mon salaire ?),
**Dépenses** (qu'ai-je dépensé ?), **Épargne** (combien j'épargne ?).

- Barre du haut, en verre : la roue des Réglages à gauche, le titre de l'onglet, le + à droite.
  Le + ouvre « Ajouter » : Dépense (en premier), Achat prévu, Enveloppe, Mise de côté.
- Barre des mois en tête de Résumé, Budget et Dépenses : un seul mois choisi pour les trois, puis
  « Année » (sheet de l'année : toucher un mois y mène). Glisser horizontalement change de mois.
  Épargne reste sur le mois en cours.
- Résumé d'un mois terminé : ce qu'il a coûté, son bilan d'épargne, le chemin vers le détail.
- Dépenses : Catégories | Opérations (les opérations du mois choisi, la file à trier en tête) ;
  un bandeau « N dates d'achat à corriger » quand il y en a.
- Bouton Claude : rond, en verre teinté, à droite de la barre d'onglets.
- Réglages (sheet) : Salaire, Banque et synchronisation, Comptes, Noms et règles, Ajouts et
  retraits, Vérifier les dates, Mises à jour ; réglages du verre et style classique après sept
  touches sur le numéro de version.

La fiche d'une enveloppe liste les dépenses de sa période ; toucher une dépense la déplace.

## Vocabulaire (une notion, un mot)

| Notion | Terme |
| --- | --- |
| Ce qui se dépense encore aujourd'hui | Reste du jour |
| Ce qui se dépense au jour le jour dans le mois | Budget du quotidien |
| Salaire rangé dans aucune enveloppe | À répartir |
| Ce qui reste dans une enveloppe | Reste |
| Dépassement constaté / prévu | Dépassé · Prévu + (jamais « Prévu » pour un budget : « Budget ») |
| Épargne de fin de mois | Épargne prévue |
| Familles d'enveloppes (onglet Budget seulement) | Au quotidien · Charges fixes · Rares et ponctuels · Épargne |
| Ce que l'on budgète et où l'on range une dépense | Enveloppe (jamais « budget ») |
| Mouvement d'argent entre enveloppes | Ajouts et retraits |
| Nom choisi pour un libellé, rangement automatique | Noms et règles |
| Défaire un rangement | Remettre à trier |
| Chiffre de synthèse / opération | sans centimes / avec centimes |
| Ce qui réunit des enveloppes pour les statistiques | Catégorie (« Autres » par défaut) ; jamais « groupe » |
| Ce qu'une provision a déjà réuni pour sa période | Mis de côté (« 900 € mis de côté sur 1 800 € ») |
| Répartir une provision sur les mois restants | « Mettre X € de côté par mois » (action) ; une provision qui n'y suffit pas est « en retard » |
| Ce qui est sur le compte courant hors budget du mois | Disponible sur le compte |
| Mois avant le début du suivi | Non renseigné |
| Valeur d'un placement après l'impôt estimé | Net (toujours précédé de « ≈ ») |

Les six types du calcul (`EnvelopeType`) restent dans les données et le formulaire ; l'écran les
regroupe en quatre familles (`ui/envelopes/Families.kt`). Famille et catégorie ne se mélangent pas :
la famille dit comment l'argent se dépense (onglet Budget), la catégorie dit à quoi il sert (onglet
Dépenses). Dans Budget, une catégorie ne regroupe des enveloppes que si elle en réunit au moins deux
dans la même famille.

## Grammaire visuelle

- Police : Inter (SIL OFL, `third_party/Inter-LICENSE.txt`), rôles dans `ui/theme/Theme.kt`.
  Chiffre clé `displayLarge` · titre d'écran `headlineLarge` · titre de carte et montant `titleMedium`
  · texte `bodyLarge`/`bodyMedium` · détail `bodySmall` · libellé `labelMedium` · légende `labelSmall`.
  Aucune taille hors de ces rôles.
- Boutons : primaire = `GlassPillButton` (un par écran) · secondaire = verre clair · dans une ligne =
  `RowActionButton` (tonal) · le reste en `TextButton`.
- Lignes : `GlassRowsCard` + `RowDivider` (filet fin décalé après la pastille).
- Statuts : `StatusChip` (point + mot + couleur), jamais la couleur seule. `status.good` (bleu) pour
  « dans le budget », `status.gain` (vert) pour un montant favorable, `status.warning` (ambre) pour
  « à surveiller », `status.over` (rouge) pour « dépassé ».
- Graphiques : barres et lignes fines, extrémités arrondies, légende dès deux séries, une seule
  échelle. Histogramme de l'année : trois couleurs vérifiées sur la carte de verre (orange, vert
  d'eau, violet), jamais les couleurs de statut.
- Explications : `InfoButton` ⓘ + `InfoSheet` ; jamais plus d'une ligne d'aide dans une carte.
- Verre et couleurs : inchangés (voir `ui/glass/Glass.kt`, `ui/theme/Theme.kt`). Les valeurs du verre
  sont dans `ui/glass/GlassTuning.kt` ; le menu développeur les modifie sans toucher au code.

## Règles de rédaction

1. Le chiffre avant la phrase.
2. Titres de un à trois mots, mot informatif en premier.
3. Une ligne d'aide au plus par carte ; le reste dans ⓘ.
4. Un écart porte son signe et sa référence (« −161 € vs sept. »).
5. Une notion, un mot, partout.
6. Un bloc qui n'a rien à dire n'apparaît pas.

## Fin de période

Une seule règle : ce qui reste dans une enveloppe à la fin de sa période va à l'épargne, un
dépassement y est pris. Le quotidien chaque mois, une charge mensuelle à la fin du mois, une
provision annuelle au 31 décembre. Rien ne part seul dans une autre enveloppe : pour remettre de
l'argent quelque part, on le prend sur l'épargne (« Ajouter » sur l'enveloppe). Le bilan d'un mois
terminé (onglet Budget, mois précédent ; rappelé sur l'accueil la première semaine) le montre.

## Autres comptes

Livrets et investissements : saisis à la main (solde daté) ou reliés à la banque (solde lu à
chaque synchronisation). Un virement vers l'un d'eux est hors budget et compté comme versé. Un
placement peut porter son total versé et son enveloppe fiscale : l'app affiche alors un net estimé
(« ≈ »), aux taux 2026.

## Disponible sur le compte, et provisions

- « Disponible sur le compte » (la réserve, dans le code) est ce qui reste sur le compte courant une fois le mois, les charges, l'épargne à virer
  et les provisions mises de côté retirés. « Ajouter » sur une enveloppe le propose en premier pour
  l'épargne et les provisions : y prendre ne change ni le budget ni l'épargne du mois.
- Une provision créée en cours de période répartit son montant sur les mois restants ; une ancienne
  garde sa part, et sa fiche propose de rattraper quand le suivi a commencé après sa période.

## Six mots visibles

L'écran se lit avec six mots : Reste, Dépensé, Enveloppe, Catégorie, Épargne, À trier. Les autres
notions vivent dans les ⓘ ou s'expriment par une action (« Mettre de côté »). Une action principale
par écran : le + en haut à droite. Le verre est réservé à la navigation ; les cartes sont un matériau
épais (flou fort, sans lentille, voile bleu nuit) pour garder 4,5:1 de contraste.

## Dates

Une dépense porte le jour de l'achat : celui que donne la banque, sinon celui du libellé (« CB 2209 »)
s'il tombe dans les 45 jours avant l'inscription pour une carte (débit différé) ou 10 jours sinon,
sinon le jour d'inscription. L'app garde aussi le jour d'inscription et celui de la banque. Une
correction de jour ne se fait jamais seule : « Vérifier les dates » (menu ⋮) liste les jours à
corriger, dit si la carte est à débit immédiat ou différé, et applique ce qui est validé.

Une enveloppe a un départ (1er du mois de sa création ; « depuis toujours » pour les anciennes, à
choisir dans sa fiche). Une dépense antérieure peut y être rangée mais ne compte pas dans son reste.

## Passé figé et salaire lu à la banque

Chaque période commencée est enregistrée (bornes et montant) : changer un montant vaut pour la
période en cours et les suivantes, jamais pour un mois terminé. Une enveloppe arrêtée garde son
budget dans les mois qu'elle a couverts (« Arrêter » ; « Supprimer définitivement » pour une erreur). Un budget peut avoir un premier montant et une remise à
zéro (« 26 € jusqu'au 1er janvier, puis 26 € tous les 3 mois »), ou être ponctuel. Un budget rare
qui garde son reste (« tous les N », coiffeur) le reporte sur sa période suivante ; les autres
envoient leur reste à l'épargne.

Le salaire n'est plus tapé : l'app repère les virements reçus chaque mois du même émetteur, on
confirme une fois lequel est le salaire. Versé après le 20, il finance le mois suivant. Tant qu'il
n'est pas arrivé, le dernier versement est « attendu ».

## Un chiffre, une fonction

Un chiffre affiché à plusieurs endroits vient d'une seule fonction du module `:domain`.

| Chiffre | Fonction | Où il s'affiche |
| --- | --- | --- |
| Reste du mois, par jour | `dailyAllowance` (`DailyBudget.kt`) | Accueil seulement ; l'Épargne parle d'un total (`spendableForTarget`) |
| Dépensé du mois | `countedSpendings` (`SpendingReport.kt`), via `monthPace` et `monthSpending` | Accueil (rythme), Analyse (total du mois) |
| Prévu + | `project` (`Projection.kt`) : rien avant le 10, puis rythme du mois mêlé à l'habitude (`dailyHabits`, 12 mois) | Enveloppes, fiche, Claude |
| Comparaison de début de mois | `MonthPace.previousSameDay` | Analyse du mois en cours |
| Bilan d'un mois terminé | `monthClosing` (`MonthClosing.kt`) : épargne du mois, virée ou encore sur le compte | Budget, mois passé |
| Moyennes et écarts de l'année | `YearSpending.complete` : mois terminés seulement | Analyse, Année |
| Montant proposé | `suggestedAmount` sur l'historique de l'enveloppe ; rien pour une nouvelle | Formulaire d'enveloppe |
| À trier | `observeUnsortedCount` moins le salaire reconnu | Accueil, onglet Dépenses |
