# Refonte UX — règles de l'interface

Référence de travail pour faire évoluer l'interface. Le raisonnement complet (recherche, analyse,
carte mentale, autocritique) est dans le document de refonte partagé avec Baptiste.

## Structure : quatre onglets, une question chacun

| Onglet | Question | Contenu, dans l'ordre | Action principale |
| --- | --- | --- | --- |
| Aujourd'hui | Puis-je dépenser aujourd'hui ? | Reste du jour · Le mois (dépensé sur budget, bande de rythme) · À faire (couvrir, trier ; absent s'il n'y a rien) · Épargne prévue en une ligne | Ajouter une dépense |
| Budget | Où va mon salaire ? | Plan du mois (barre : charges fixes, provisions, épargne, quotidien) · une carte par famille | Nouvelle enveloppe (+) |
| Épargne | Combien vais-je mettre de côté ? | Épargne prévue + conseil · écarts à l'objectif et au mois précédent · six mois · mis de côté · achats prévus | Mettre de côté ; + = achat prévu |
| Opérations | Que s'est-il passé ? | À trier (si non vide) · historique par jour | Ajouter une dépense |

Le menu ⋮ garde les réglages : salaire, banque, journal des transferts, style, mises à jour.

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
- Verre et couleurs : inchangés (voir `ui/glass/Glass.kt`, `ui/theme/Theme.kt`).

## Règles de rédaction

1. Le chiffre avant la phrase.
2. Titres de un à trois mots, mot informatif en premier.
3. Une ligne d'aide au plus par carte ; le reste dans ⓘ.
4. Un écart porte son signe et sa référence (« −161 € vs sept. »).
5. Une notion, un mot, partout.
6. Un bloc qui n'a rien à dire n'apparaît pas.
