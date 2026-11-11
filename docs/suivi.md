# Suivi du projet

Point d'entrée d'une nouvelle conversation : lire `CLAUDE.md`, qui donne l'ordre de lecture.
Ce fichier dit où en est le code. Il se met à jour à la fin de chaque lot.

Depuis le 10 novembre 2026, `docs/produit.md` l'emporte sur tous les autres documents, celui-ci
compris. Ce qu'il reste à faire est dans `docs/backlog.md`, les décisions de produit dans
`docs/decisions.md` ; les décisions ci-dessous sont celles d'avant ce registre.

## Où en est l'app

- `main` = lot 8e, commit `4c898ec`, release `build-245` (10 novembre 2026). Base de données v20
  (schémas jusqu'à `20.json` dans `app/schemas/`). CI verte : 250 tests du domaine, 57 sur
  l'émulateur ; relectures logique et UX passées.
- Rien en attente de fusion : aucune branche `feature/...` n'a de contenu absent de `main`
  (vérifié le 11 novembre ; le seul commit propre à `feature/schema-v7`, les schémas publiés avec
  les captures, est déjà dans `screenshots.yml`). Leur suppression est demandée.
- Cadrage du 10 novembre : produit, backlog, décisions D1 à D5 prises (option A), gel des
  fonctions. Le lot 9 est gelé.
- Lot A, F1 (sauvegarde et restauration) : développé le 12 novembre, en attente de la recette de
  Baptiste (F1 reste ouvert dans le backlog jusque-là). Décisions D6 et D7.
  - `BackupFile.kt` : le fichier (archive zip chiffrée, PBKDF2-HMAC-SHA256 puis AES-GCM), sans
    rien d'Android, testé dans `app/src/test`.
  - `Backup.kt` : `copyTables` recopie toutes les tables d'une base vers une autre, dans les deux
    sens. Restaurer ouvre d'abord le fichier avec Room (migrations), puis remplace dans une seule
    transaction ; les réglages suivent. Pas de redémarrage de l'app.
  - La connexion bancaire n'est ni sauvegardée ni touchée ; après une restauration,
    `BankSyncRepository.rewindTo` fait repartir la synchronisation d'avant la sauvegarde.
  - « Restaurer » d'une enveloppe arrêtée est devenu « Reprendre ».
- `ci.yml` ne publie pas de release quand un push sur `main` ne touche que `docs/`, `.claude/` ou
  des fichiers `.md` ; les tests tournent toujours.
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
    (couverture, totaux de branche, déplacement).
  - **8b** : Budget et Dépenses sur l'arbre (`GlassEnvelopeTree.kt`, `TreeBudget.kt`), appui long,
    filtre de Dépenses. La couverture des enfants vit dans `allocation.covered_cents` : le montant
    saisi, le report et les corrections ne sont jamais réécrits, `syncCoverage` est le seul point
    d'écriture, `budgetEnvelopes` donne aux calculs les parts propres.
  - **8c** : objectifs d'épargne (`Goals.kt`) : `goal_cents`, `goal_by`, `account_id` sur l'enveloppe,
    table `savings_placement` (mouvements, annulables). Part d'un objectif daté recalculée au début de
    chaque période puis figée. Répartition de départ des livrets, « à ranger » (virements depuis la
    répartition, signe inversé : débit du compte courant), « Versé sur » (`goalsTarget` du compte).
    Précaution reprise une fois en enveloppe d'épargne, Total retiré.
  - **8d** : somme mise à part (`EnvelopeType.LUMP`, une période de 1 200 mois). Pas de part du mois
    sauf prise sur le mois (`fundingStart`), hors du rapport du mois, de l'arbre et du disponible ;
    prise sur un objectif : mouvement négatif dans la même entrée. « Terminer » (`finishLump`) range
    le reste ou couvre le dépassement ; type et montant figés après la création.
  - **8e** : supprimer une catégorie (son contenu remonte, annulable) ; « Déplacer » sur l'arbre de
    Budget, même chemin que le glissement (`dropInTree`) ; catégorie d'un enfant d'une vraie
    enveloppe héritée du premier groupe au-dessus ; fiche d'un parent avec son budget couvert ;
    report d'un parent rattrapé sans la part de ses enfants (`missingPeriods(coveredOn)`) ; export
    Claude en parts propres (`parent`, `children_cover`) ; virements vers mes comptes nets ; zones
    de dépôt distinctes ; couleurs de catégorie hors de celles des familles ; captures du thème
    clair retirées (l'interface est toujours sombre) ; code mort de l'ancien SavingsGoals et de la
    vue année de Budget retiré ; doc UX à jour (arbre, objectifs, somme mise à part).
- Tests du domaine sans Gradle : `./scripts/domain-tests.sh` (250 au 10 novembre).

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

Plan validé le 9 novembre : 8d somme mise à part (financée d'un coup, sans part du mois, « Pris
sur » compte, objectif ou À répartir du mois, « Terminer » rend le reste), 8e arbre et cohérence
(supprimer, défauts ci-dessous, doc UX, démo, export Claude, skill, code mort de l'ancien
SavingsGoals), 9 revue du tri par Claude (propositions applicables une à une depuis le bilan).

Suite : le lot 9 (revue du tri par Claude) est gelé, voir « Idées » de `docs/backlog.md`. Le
lot A continue avec F2 (clé de signature hors du dépôt) une fois F1 recetté.

## Restes connus

- Lot A : pas de capture de « Reprendre » (la démo n'a pas d'enveloppe arrêtée) ; « Annuler » une
  restauration n'est proposé que dans le message qui la suit ; base et réglages sont remplacés
  l'un après l'autre (un arrêt de l'app entre les deux laisserait les anciens réglages) ; sur un
  nouveau téléphone, les opérations entre la sauvegarde et le début du mois se récupèrent par
  l'historique de la banque, à la main.
- Encart du bilan : espace vide sous ses boutons.
- « mois-ci » s'affiche avec un trait d'union espacé dans « Ajouter de l'argent » (police).
- Captures qui ne montrent pas encore le lot : une ligne « modifié depuis » (Historique de la démo
  vide), une catégorie dépliée à 0 € (sous le pli), l'arbre du formulaire ouvert.
- Rattrapages acceptés avant build-202 : gardent l'ancienne répartition (décision notée dans le doc).
- Reportés du 8e : dépôt à côté de l'enfant d'un parent arrêté (il entre dans le parent arrêté,
  affiché au même niveau) ; « Retirer » d'une somme mise à part vers À répartir ; bilan d'un mois
  passé qui ne cite pas ses sommes mises à part ; points gris de `glass-declasser` (couleur
  « Autres ») ; catégorie héritée cherchée dans la seule liste reçue (un enfant actif d'un parent
  arrêté retombe en « Autres » dans l'export) ; captures manquantes : trait avant/après, champ
  « Nouvelle catégorie », message long d'une enveloppe posée dans une enveloppe ; couleurs de
  secours proches de certaines catégories (nom toujours écrit).
- À étudier : « Prévoir une dépense » ; séance de test SUS sur la vraie app (quatre tâches).

## Liens

- Doc de revue (artifact, non mis à jour depuis les encarts) :
  https://claude.ai/artifact/XKFKWEP4d968A2BxSz6BAX
- Releases : https://github.com/Baptiste-Rd/Bank/releases
- Captures de la CI : branche `screenshots`, dossier `<sha>/`.
