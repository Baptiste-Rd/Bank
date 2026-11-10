# Bank : règles de travail

App Android de budget personnel (Kotlin, Compose, Room). Le but n'est plus d'ajouter : il est
d'atteindre la définition d'« abouti » de `docs/produit.md`, paragraphe 4.

## Ce qu'il faut lire, dans cet ordre

1. `docs/produit.md` : la promesse, les six tâches, la définition d'abouti, la méthode de budget,
   les invariants, les mots autorisés, le hors périmètre. **Il l'emporte sur tout autre document.**
2. `docs/backlog.md` : ce qu'il reste à faire, dans l'ordre. Le prochain lot est le premier
   élément ouvert.
3. `docs/decisions.md` : ce que Baptiste a tranché, et ce qui attend sa décision.
4. `docs/suivi.md` : où en est le code. `docs/refonte-ux.md` : comment l'écran applique le produit.

Si deux documents se contredisent, ne choisis pas : signale-le à Baptiste et corrige le document
de rang inférieur dans le même lot.

## Qui décide quoi

- **Baptiste décide du produit** : ce que l'app fait, les mots à l'écran, les compromis, l'ordre
  des lots. Il valide chaque fiche de lot et fait la recette sur son téléphone.
- **Claude décide de la technique** : architecture, code, tests, migrations.
- **Devant un choix de produit, Claude ne tranche pas.** Il présente deux options au plus, avec
  pour chacune ce qu'elle change, son gain, son coût, et sa recommandation. Baptiste choisit ; la
  décision s'inscrit dans `docs/decisions.md` avant le code.
- Un choix est « de produit » s'il change un mot affiché, un chiffre affiché, le nombre d'étapes
  d'une tâche, ou ce que l'utilisateur doit comprendre.

## Gel des fonctions

Tant que l'app n'est pas aboutie :

- un lot part d'un élément de `docs/backlog.md`, jamais d'une idée ;
- une idée nouvelle, de Claude ou de Baptiste, se note dans « Idées » du backlog, en une ligne, et
  n'est pas développée ;
- un lot n'ajoute aucun mot à l'écran, aucun réglage et aucun écran, sauf si sa fiche le dit et
  nomme ce qui est retiré en échange ;
- quand un problème a deux corrections possibles, on prend celle qui retire quelque chose.

## Déroulé d'un lot

Un lot traite un à trois éléments du backlog qui touchent le même écran ou le même calcul.

1. **Fiche de lot,** quinze lignes au plus, avant tout code :
   - le problème observé et sa preuve (constat du backlog, ligne du journal d'usage, test) ;
   - la tâche servie (T1 à T6) ;
   - le critère de réussite, en deux parties : le ou les tests du domaine, et la recette, c'est-à-
     dire les gestes que Baptiste fera sur son téléphone et ce qu'il devra voir ;
   - ce qui est retiré (mots, boutons, écrans, code) ;
   - ce qui est hors du lot.
   Baptiste valide la fiche. Sans validation, pas de code.
2. **Décision,** si la fiche contient un choix de produit (voir plus haut).
3. **Prototype cliquable** seulement pour une interaction nouvelle, testé avec la tâche concernée.
4. **Développement** sur une branche `feature/...`. Pour une règle de calcul, le test s'écrit
   d'abord et échoue d'abord.
5. **Trois relectures,** dans cet ordre, avant de montrer quoi que ce soit :
   - `revue-logique` : chiffres, invariants de `docs/produit.md`, cas limites, tests ;
   - `revue-naive` : reçoit les captures et les tâches, jamais les documents du projet ; dit ce
     qu'une personne qui découvre l'écran comprend ;
   - `revue-ux` : rendu, textes, mots autorisés, règles d'Apple et de Google.
   On corrige, ils relisent. Si `revue-naive` échoue sur une tâche que le lot sert, le lot n'est
   pas fini, même si les deux autres passent.
6. **CI,** puis fusion en avance rapide sur `main` et release `build-N`.
7. **Recette par Baptiste** sur son téléphone, avec les gestes de la fiche. Le lot est terminé
   quand Baptiste l'écrit, pas avant.
8. **Clôture** : l'élément est fermé dans `docs/backlog.md`, `docs/suivi.md` est à jour, et
   `docs/refonte-ux.md` aussi si l'écran a changé.

## Règles de conduite

- **Dire ce qui n'a pas été vérifié.** Un compte rendu de lot sépare ce qui est prouvé (test,
  capture relue) de ce qui est supposé. « Fait » sans preuve s'écrit « fait, non vérifié ».
- **Deux essais, pas trois.** Si le critère de réussite échoue deux fois, on arrête de coder et on
  revient vers Baptiste avec l'analyse et deux options.
- **Pas de reste silencieux.** Ce qui est repoussé s'écrit dans « Restes connus » de `suivi.md`.
  Au-delà de cinq restes, le lot suivant est un lot de nettoyage.
- **Mécanismes génériques.** Pas de réponse à un cas de test, pas de seuil fixé dans le code pour
  un cas de Baptiste : un réglage avec une valeur par défaut, ou une règle qui vaut pour tous.
- **La démo dit vrai.** Les données de démo passent par les fonctions de l'app. Une capture qui
  montre un chiffre que l'app réelle ne produirait pas est un défaut.
- **Supprimer est un résultat.** Un lot qui retire un mot, un écran ou du code mort sans rien
  ajouter est un bon lot.

## Attente de la CI

- Au plus 3 vérifications, espacées de 5 minutes au plus, pour un même résultat.
- Si les captures n'arrivent pas alors que la compilation et les tests sont passés : fusionner, et
  dire à Baptiste ce qui n'a pas pu être vérifié. La relecture `revue-naive` reste due sur ces
  captures avant la recette.
- Une erreur de compilation ou un test qui échoue se corrige avant toute fusion.
- Une seule exécution des captures à la fois par branche.
- Toute migration garde les données et a son test ; le schéma exporté s'ajoute à `app/schemas/`.

## Tests

- Chaque règle de calcul nouvelle ou modifiée a un test dans `domain/` (`./scripts/domain-tests.sh`).
- Chaque invariant de `docs/produit.md` a un test nommé d'après lui (`I3`, `I6`...).
- Chaque écriture en base qui s'annule a un test sur l'appareil.
- Chaque écran a une capture à taille normale et une capture à 200 %.

## Constantes

- Français, sans emoji, pas de formules creuses ; ton concis et bienveillant.
- Jamais d'envoi du fichier `.pem` ; aucune clé d'API transmise. Aucun secret dans le dépôt.
- Le mode démo ne touche jamais les vraies données.
- Vers Claude : libellés anonymisés, montants, dates, noms d'enveloppes et de comptes seulement ;
  jamais d'IBAN, de numéro de carte ou de référence, de nom de personne, ni de solde.
- « Pause », « ne dev pas », « note juste » : ne pas coder.
