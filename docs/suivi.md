# Suivi du projet

Point d'entrée d'une nouvelle conversation : lire `CLAUDE.md`, ce fichier, puis `docs/refonte-ux.md`.
Ce fichier dit où en est l'app, ce qui a été décidé et ce qui vient ensuite. Il se met à jour à la
fin de chaque lot (et quand une décision est prise).

## Où en est l'app

- `main` = lot 8a (6 novembre 2026). Base de données v18 (schémas jusqu'à `18.json` dans
  `app/schemas/`).
- Derniers lots livrés :
  - **7** : catégories imbriquées, actions en glissant, déplacer et renommer, Historique.
  - **7b (1)** : un seul calcul du tableau d'un mois (`dashboardAt` : salaire de ce mois-là,
    enveloppes arrêtées gardées dans leur mois, habitudes) ; « Dépassé » de Dépenses avec les
    ajouts et retraits ; Disponible sur le compte sans le salaire du mois suivant ; « Pris sur »
    au choix dans « Ajouter de l'argent » ; règles anonymisées vers Claude.
  - **7b (2)** : Historique complet et sûr (toute écriture à la main s'annule ; « modifié depuis »
    quand une entrée plus récente touche la même chose ou utilise l'enveloppe créée) ; catégorie
    dépliable dès qu'elle contient une enveloppe ; même arbre dans le formulaire et « Déplacer » ;
    encart du bilan du mois précédent du 1er au 7 ; rattrapage d'une provision qui garde la part
    des mois passés (`catch_up_from`).
  - **8a** : socle de l'arbre, sans changement visible. Chaque catégorie est un groupe (ligne de
    `envelope`, `is_group = 1`), le parent passe par `parent_id` ; la table `category` a disparu,
    `group_name` reste vide en base et garde le nom du parent dans l'Historique. `EnvelopeTree.kt`
    (couverture, totaux de branche, déplacement) n'est pas encore branché aux calculs.
- Tests du domaine sans Gradle : `./scripts/domain-tests.sh` (222 au 6 novembre).

## Lot 8 : décisions prises (prototype testé par l'utilisateur)

Prototype cliquable : https://claude.ai/artifact/PWNak2444qfV42vg1jPmXA (source dans l'artifact).

- **Budget = le prévisionnel.** Plan du salaire, puis l'arbre des enveloppes avec leur montant
  (« 320 € par mois », « 1 800 € par an · 150 €/mois »). Pas de dépensé, pas de reste. Toucher une
  enveloppe ouvre ses réglages. Réorganiser : rester appuyé sur une ligne, elle « vole » et se pose
  sur un groupe (elle y entre), sur une enveloppe (elle se range juste avant) ou en haut.
- **Dépenses = l'état.** Le même arbre avec le reste et la barre de chaque enveloppe ; toucher
  ouvre le suivi (reste, dépenses, « Ajouter de l'argent » avec « Pris sur »). Vue Opérations
  inchangée. Le « Dépassé » se calcule une seule fois, sur la période de l'enveloppe
  (`EnvelopeLine`) : fin de `CategorySpent.over` et du faux dépassement des provisions.
- **Une seule notion : l'enveloppe.** Un groupe est une enveloppe qui en contient d'autres ; son
  montant est leur somme. Les catégories séparées disparaissent (migration : chaque catégorie
  devient un groupe). La famille (quotidien, charge, rare, épargne) devient un réglage « C'est
  pour » de chaque enveloppe ; le plan du mois reste par famille.
- **L'épargne est faite d'enveloppes.** « C'est pour : Épargne » donne « Objectif » et « D'ici
  (facultatif) » ; avec une date, la part du mois = ce qui manque ÷ mois restants, recalculée
  chaque mois. L'épargne du mois = la somme de ces parts, mise de côté en début de mois (le reste
  et le « par jour » en tiennent compte). Avant de valider, le formulaire dit l'effet sur le « par
  jour » et prévient si le plan dépasse le salaire. « X € à placer » (l'épargne du mois passé encore
  sur le compte courant) se répartit entre les objectifs. Plus de « poches » à part.
- L'utilisateur a d'abord créé une enveloppe « au quotidien » de 8 000 € par an pour épargner : le
  choix « C'est pour » doit rendre l'épargne évidente.

Décisions du 6 novembre, qui complètent ou remplacent celles du prototype :

- **Catégorie = type de dépense** : le réglage « C'est pour » (le mot remplace « famille »).
  L'arbre, lui, se fait entre enveloppes ; on peut ranger Nourriture dans Transport.
- **Une enveloppe avec son propre montant peut en contenir d'autres.** Le parent couvre ses
  enfants : budget = le plus grand du montant saisi et de la somme des enfants (A 300 € ⊃ B 200 €
  ⊃ C 400 € : A et B valent 400 €, le plan 400 €). Le montant saisi n'est jamais réécrit, le parent
  redescend seul. Un message d'information dit le changement et son effet sur le « par jour ». Les
  rythmes différents se comparent au mois. Déposer : milieu de ligne = dedans, bords = avant, après.
- **Budget** : l'onglet garde ce nom, plus de barre des mois (bilan, Année et compte courant vont
  dans Résumé ; un budget passé se corrige depuis la fiche), pas de dépense, seulement le montant
  du budget. La barre du plan reste, les catégories sont rappelées sur chaque ligne.
- **Dépenses** : un seul arbre avec le filtre `Tout | Mensuel | Ponctuel | Épargne` (Mensuel réunit
  le quotidien et les charges). Une branche affiche le dépensé du mois, recalculé à chaque niveau ;
  reste et barre seulement sur les enveloppes.
- **Épargne** : Précaution devient une enveloppe d'épargne, Total disparaît. Un écran unique
  répartit les livrets entre les objectifs (placements exclus). Les achats prévus restent des
  enveloppes « Rare ou ponctuel ».

Prochaine étape : lot 8b (brancher `EnvelopeTree` aux calculs, Budget et Dépenses, appui long),
puis 8c (objectifs d'épargne, `goal_cents`, `goal_by`, table `savings_placement`), puis 8d (doc UX,
analyse, démo, Claude, skill).

## Restes connus

- Encart du bilan : espace vide sous ses boutons.
- « mois-ci » s'affiche avec un trait d'union espacé dans « Ajouter de l'argent » (police).
- Captures qui ne montrent pas encore le lot : une ligne « modifié depuis » (Historique de la démo
  vide), une catégorie dépliée à 0 € (sous le pli), l'arbre du formulaire ouvert.
- Rattrapages acceptés avant build-202 : gardent l'ancienne répartition (décision notée dans le doc).
- À étudier : « Prévoir une dépense » ; séance de test SUS sur la vraie app (quatre tâches).

## Liens

- Doc de revue (artifact, non mis à jour depuis les encarts) :
  https://claude.ai/artifact/XKFKWEP4d968A2BxSz6BAX
- Releases : https://github.com/Baptiste-Rd/Bank/releases
- Captures de la CI : branche `screenshots`, dossier `<sha>/`.
