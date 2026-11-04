# Refonte UX — règles de l'interface

Référence de travail pour faire évoluer l'interface. Le raisonnement complet (recherche, analyse,
carte mentale, autocritique) est dans le document de refonte partagé avec Baptiste.

## Structure : quatre onglets, une question chacun

Onglets, dans l'ordre : **Résumé** (où j'en suis ce mois ?), **Budget** (où va mon salaire ?),
**Dépenses** (qu'ai-je dépensé ?), **Épargne** (combien j'épargne ?).

- Barre du haut plate, opaque quand le contenu défile dessous : la roue des Réglages à gauche, le
  titre au centre, la loupe et le + (bleu plein) à droite. Le verre est réservé à la barre
  d'onglets et au bouton Claude.
- Contrôle segmenté unique (mois, Catégories | Opérations, recherche) : fond opaque, coins de
  14 dp, choix en blanc à 14 %. La barre des mois montre quatre mois et « Année », à largeur égale.
  Le + ouvre « Ajouter » : Dépense (en premier), Achat prévu, Enveloppe, Mise de côté.
- Barre des mois en tête de Résumé, Budget et Dépenses : un seul mois choisi pour les trois, puis
  « Année » (sheet de l'année : toucher un mois y mène). Glisser horizontalement change de mois.
  Épargne reste sur le mois en cours.
- Résumé du mois en cours : deux grands chiffres (Reste du mois, Par jour) et l'écart à la même
  date du mois précédent ; « N à trier » ; À surveiller (trois enveloppes au plus, celles qui
  dépassent ou vont dépasser) ; Compte courant (et salaire reçu) et Autres comptes en deux lignes ;
  Dernières dépenses (trois) ; Rythme du mois.
- Résumé d'un mois terminé : ce qu'il a coûté, son bilan d'épargne, le chemin vers le détail.
- Épargne : épargne prévue et « Mettre de côté » ; Mes comptes (livrets et placements) ; Mis de
  côté ; Achats prévus (« Ajouter ») ; Essayer un plan ; Mois par mois.
- Une catégorie se repère à son point de couleur (plus d'initiale) ; une catégorie dépliable
  porte un chevron, une catégorie sans dépense du mois n'en a pas. Les en-têtes de famille n'ont
  pas de point (les couleurs des familles restent dans la barre du plan et sa légende).
- Dépenses : la barre des mois, puis Catégories | Opérations, rien d'autre en en-tête. Opérations :
  « N dates d'achat à corriger » (s'il y en a), la file À trier avec « Trier une par une », puis
  les opérations du mois. Le total du mois : le chiffre et une puce signée ; le reste dans ⓘ.
- Bouton Claude : rond, en verre teinté, à droite de la barre d'onglets.
- Réglages (sheet) : Salaire, Banque et synchronisation, Comptes, Noms et règles, Historique,
  Ajouts et retraits, Vérifier les dates, Mises à jour ; les réglages du verre après sept touches
  sur le numéro de version (menu développeur, rien d'autre).
- Claude : un seul écran (trier ce qui attend, contrôler les dépenses, coller une réponse) et une
  ligne sur ce qui est partagé.

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
| Mois qui sert aux moyennes, aux montants proposés et à l'épargne de l'année | Mois de référence ; sinon « Hors référence » (un mois incomplet l'est par défaut) |
| Dépensé du mois jusqu'à un jour donné (vue Opérations) | Cumul du mois |
| Revenir sur un changement | Annuler ; la liste des dix derniers est l'« Historique » |
| Valeur d'un placement après l'impôt estimé | Net (toujours précédé de « ≈ ») |

Les six types du calcul (`EnvelopeType`) restent dans les données et le formulaire ; l'écran les
regroupe en quatre familles (`ui/envelopes/Families.kt`). Famille et catégorie ne se mélangent pas :
la famille dit comment l'argent se dépense (onglet Budget), la catégorie dit à quoi il sert (onglet
Dépenses). Dans Budget, les enveloppes sont en lignes plates dans leur famille, avec la couleur de
leur catégorie.

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

## Encarts : le modèle du prototype

Chaque carte suit la même règle : un chiffre clé, une ligne d'aide au plus, le reste derrière ⓘ ou un repli.

- **Ligne d'enveloppe** : nom et reste (euros ronds), une barre, une ligne « X € sur Y € » ou une puce d'état. Aucun bouton dans la ligne ; les lignes sont plates dans leur famille (plus de groupe par catégorie dans Budget).
- **Fiche d'enveloppe** : le reste en grand, une ligne « dépensé sur disponible · période » (pour une provision : « mis de côté sur … », la puce « En retard » et « Mettre X € de côté par mois » en action principale), une puce si l'enveloppe dérape, « Ajouter de l'argent » seulement dans ce cas, Ajouter et Retirer, les dépenses. Le détail (Budget, Ajouts, Dépensé, Reste), les périodes passées et les ajouts et retraits sont repliés dans « Détail du budget » (« Historique » est réservé à la liste des annulations).
- **« Couvrir » devient « Ajouter de l'argent »** : la fenêtre dit combien il manque, d'où vient l'argent et ce que devient le reste du mois. Le bouton n'existe que dans la fiche.
- **Formulaire d'enveloppe** : Nom, C'est pour, Combien, Catégorie (et la remise à zéro pour un budget rare). Le départ, le rythme, la suite d'un budget rare et la protection sont dans « Plus d'options ».
- **Plan du mois** : la barre et sa légende seulement ; « Essayer un plan » vit dans Épargne.
