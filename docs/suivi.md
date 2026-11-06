# Suivi du projet

Point d'entrée d'une nouvelle conversation : lire `CLAUDE.md`, ce fichier, puis `docs/refonte-ux.md`.
Ce fichier dit où en est l'app, ce qui a été décidé et ce qui vient ensuite. Il se met à jour à la
fin de chaque lot (et quand une décision est prise).

## Où en est l'app

- `main` = build-202 (6 novembre 2026). Base de données v17 (schémas jusqu'à `17.json` dans
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
- Tests du domaine sans Gradle : `./scripts/domain-tests.sh` (212 au 6 novembre).

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

Questions encore ouvertes pour le lot 8 :
1. Garder la barre des mois dans Budget (proposition : non, la structure ne change pas d'un mois à
   l'autre ; l'historique d'une enveloppe est dans sa fiche).
2. Nom de l'onglet Budget (« Budget » ou « Enveloppes »).
3. Ce qui reste des familles dans Budget : seulement la barre du plan, ou aussi un regroupement.

Prochaine étape : présenter le plan du lot 8 et son plan de contrôle (déroulé de `CLAUDE.md`),
puis la méthode, puis le développement.

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
