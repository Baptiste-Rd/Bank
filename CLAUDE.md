# Bank : règles de travail

App Android de budget personnel (Kotlin, Compose, Room), interface « verre » bleu nuit.
Référence UX : `docs/refonte-ux.md`. Toute interface s'y conforme.

## Déroulé d'un lot

1. Sur demande : résumé des prochains lots, puis le plan de contrôle de l'efficacité ; après
   validation, la méthodologie ; après validation, le développement.
2. Branche `feature/...`, puis CI (tests, APK, captures sur émulateur, tests sur l'appareil),
   fusion en avance rapide sur `main`, release automatique `build-N`.
3. Tout lot qui touche l'interface passe par l'agent `revue-ux` (`.claude/agents/revue-ux.md`) sur
   les captures de la CI, avant de montrer les écrans et avant de fusionner. On corrige ce qu'il
   relève, puis il relit.

## Attente de la CI

- Au plus 3 vérifications, espacées de 5 minutes au plus, pour un même résultat (compilation ou
  captures).
- Si le résultat n'est toujours pas là (émulateur qui ne démarre pas, file d'attente) et que la
  compilation et les tests sont passés : fusionner, et prévenir l'utilisateur de ce qui n'a pas pu
  être vérifié.
- Une erreur de compilation ou un test qui échoue se corrige avant toute fusion.
- Une seule exécution des captures à la fois : annuler les plus anciennes de la même branche.

## Constantes

- Français, sans emoji. Pas de formules creuses.
- Jamais d'envoi du fichier .pem : il s'importe directement dans l'app. Aucune clé d'API transmise.
- Le mode démo ne touche jamais les vraies données.
- Vers Claude : libellés anonymisés, montants, dates, noms d'enveloppes et de comptes seulement ;
  jamais d'IBAN, de numéro de carte ou de référence, ni de solde.
- « Pause », « ne dev pas » : ne pas coder.
- Mécanismes génériques plutôt que réponses à un cas de test.
