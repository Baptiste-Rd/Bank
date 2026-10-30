# Refonte UX — règles de l'interface

Référence de travail pour faire évoluer l'interface. Le raisonnement complet (recherche, analyse,
carte mentale, autocritique) est dans le document de refonte partagé avec Baptiste.

## Structure : quatre onglets, une question chacun

| Onglet | Question | Contenu, dans l'ordre | Action principale |
| --- | --- | --- | --- |
| Aujourd'hui | Puis-je dépenser aujourd'hui ? | Reste du jour · Le mois (dépensé sur budget, bande de rythme) · À faire (couvrir, trier ; absent s'il n'y a rien) · Épargne prévue en une ligne | Ajouter une dépense |
| Budget | Où va mon salaire ? | Bilan (mois terminé) · Plan du mois (barre : charges fixes, provisions, épargne, quotidien) · une carte par famille | Nouvelle enveloppe (+) |
| Épargne | Combien vais-je mettre de côté ? | Épargne prévue + conseil · écarts à l'objectif et au mois précédent · six mois · sur tes comptes (livrets, investissement) · mis de côté · achats prévus | Mettre de côté ; + = achat prévu |
| Dépenses | Où est parti l'argent ? Que s'est-il passé ? | Analyse \| Liste. Analyse : Mois (total réel, à trier et non comptés à part, une barre par catégorie avec le trait du budget, puis enveloppes, puis opérations) · Année (histogramme empilé, moyenne, mois non renseignés grisés, tableau par catégorie). Liste : à trier · outils du tri · historique par jour | Ajouter une dépense |

Le menu ⋮ garde les réglages : salaire, banque, journal des transferts, style, réglages du verre
(développeur : panneau en bas, app en direct au-dessus), mises à jour.

La fiche d'une enveloppe liste les dépenses de sa période ; toucher une dépense la déplace.

## Vocabulaire (une notion, un mot)

| Notion | Terme |
| --- | --- |
| Ce qui se dépense encore aujourd'hui | Reste du jour |
| Ce qui se dépense au jour le jour dans le mois | Budget du quotidien |
| Salaire rangé dans aucune enveloppe | À répartir |
| Ce qui reste dans une enveloppe | Reste |
| Dépassement constaté / prévu | Dépassé · Prévu + |
| Épargne de fin de mois | Épargne prévue |
| Familles d'enveloppes (onglet Budget seulement) | Au quotidien · Charges fixes · Provisions · Épargne |
| Ce que l'on budgète et où l'on range une dépense | Enveloppe |
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
