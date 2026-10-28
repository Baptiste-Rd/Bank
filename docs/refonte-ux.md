# Refonte UX — règles de l'interface

Référence de travail pour faire évoluer l'interface. Le raisonnement complet (recherche, analyse,
carte mentale, autocritique) est dans le document de refonte partagé avec Baptiste.

## Structure : quatre onglets, une question chacun

| Onglet | Question | Contenu, dans l'ordre | Action principale |
| --- | --- | --- | --- |
| Aujourd'hui | Puis-je dépenser aujourd'hui ? | Reste du jour · Le mois (dépensé sur budget, bande de rythme) · À faire (couvrir, trier ; absent s'il n'y a rien) · Épargne prévue en une ligne | Ajouter une dépense |
| Budget | Où va mon salaire ? | Bilan (mois terminé) · Plan du mois (barre : charges fixes, provisions, épargne, quotidien) · une carte par famille | Nouvelle enveloppe (+) |
| Épargne | Combien vais-je mettre de côté ? | Épargne prévue + conseil · écarts à l'objectif et au mois précédent · six mois · sur tes comptes (livrets, investissement) · mis de côté · achats prévus | Mettre de côté ; + = achat prévu |
| Opérations | Que s'est-il passé ? | À trier (si non vide, avec l'enveloppe proposée) · outils du tri (règles et alias, déclasser) · historique par jour | Ajouter une dépense |

Le menu ⋮ garde les réglages : salaire, banque, journal des transferts, style, réglages du verre
(développeur : panneau en bas, app en direct au-dessus), mises à jour.

La fiche d'une enveloppe liste les dépenses de sa période ; toucher une dépense la déplace.

## Vocabulaire (une notion, un mot)

| Notion | Terme |
| --- | --- |
| Ce qui se dépense encore aujourd'hui | Reste du jour |
| Ce qui se dépense au jour le jour dans le mois | Budget du quotidien |
| Salaire rangé dans aucune enveloppe | Non réparti |
| Ce qui reste dans une enveloppe | Reste |
| Dépassement constaté / prévu | Dépassé · Prévu + |
| Épargne de fin de mois | Épargne prévue |
| Familles d'enveloppes | Au quotidien · Charges fixes · Provisions · Épargne |

Les six types du calcul (`EnvelopeType`) restent dans les données et le formulaire ; l'écran les
regroupe en quatre familles (`ui/envelopes/Families.kt`).

## Grammaire visuelle

- Police : Inter (SIL OFL, `third_party/Inter-LICENSE.txt`), rôles dans `ui/theme/Theme.kt`.
  Chiffre clé `displayLarge` · titre d'écran `headlineLarge` · titre de carte et montant `titleMedium`
  · texte `bodyLarge`/`bodyMedium` · détail `bodySmall` · libellé `labelMedium` · légende `labelSmall`.
  Aucune taille hors de ces rôles.
- Boutons : primaire = `GlassPillButton` (un par écran) · secondaire = verre clair · dans une ligne =
  `RowActionButton` (tonal) · le reste en `TextButton`.
- Lignes : `GlassRowsCard` + `RowDivider` (filet fin décalé après la pastille).
- Statuts : `StatusChip` (point + mot + couleur), jamais la couleur seule.
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
chaque synchronisation). Un virement vers l'un d'eux est hors budget et compté comme versé.
