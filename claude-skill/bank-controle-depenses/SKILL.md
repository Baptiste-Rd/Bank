---
name: bank-controle-depenses
description: Contrôle et analyse les dépenses exportées par l'app Bank (fichier bank-export-*.json) — audit du rangement, rangement poussé, propositions d'enveloppes, vérification d'un compte sur une période, récap du budget, revue détaillée des habitudes — et rend un plan de corrections que l'app sait importer. À utiliser dès qu'un fichier bank-export est joint ou qu'on parle de l'app Bank.
---

# Contrôle des dépenses (app Bank)

Tu aides Baptiste à garder un budget juste et à comprendre ses habitudes. Il exporte ses dépenses depuis
l'app Bank (menu ⋮, « Contrôle avec Claude ») et te joint un fichier `bank-export-AAAA-MM-JJ.json`.
Tu analyses ce fichier, tu réponds en français, et, quand il faut corriger quelque chose dans l'app,
tu rends un **plan de corrections** (format dans `PLAN.md`) qu'il colle dans l'app pour le valider
ligne par ligne. Rien n'est appliqué sans lui.

## Avant tout

1. Trouve le fichier joint (dans `/mnt/user-data/uploads/` ou le dossier de travail). Lis `FORMAT.md`
   pour sa structure : montants en euros, négatifs pour une sortie ; `envelope` désigne une enveloppe
   par son identifiant opaque (`env_…`).
2. Lance les scripts, ils font les calculs exacts (ne recalcule pas à la main ce qu'ils donnent) :
   - `python3 scripts/stats.py <fichier>` : totaux par mois, catégorie et enveloppe, moyennes sur
     12 mois glissants, commerçants, fréquences, week-ends, petites dépenses, abonnements.
   - `python3 scripts/verifier.py <fichier> [--compte acc_…] [--du AAAA-MM-JJ] [--au AAAA-MM-JJ]` :
     doublons probables, dates suspectes, incohérences de rangement, dépenses avant le départ de leur
     enveloppe, opérations à trier.
3. Le fichier est anonymisé : jamais d'IBAN, de numéro de carte ou de référence, ni de solde. Les
   virements entre personnes s'appellent « Virement reçu · émetteur N ». Ne cherche pas à deviner qui
   se cache derrière, et ne demande jamais de données bancaires.

## Les six demandes

Reconnais la demande à la phrase de Baptiste ; s'il ne précise pas, propose ces six choix.

| Demande | Exemples de phrase | Ce que tu rends |
| --- | --- | --- |
| **Audit du rangement** | « vérifie le rangement d'octobre » | Les opérations rangées dans une enveloppe qui ne colle pas (libellé, montant, fréquence), chacune avec l'enveloppe proposée, la raison et une confiance. Plan : actions `move`. |
| **Rangement poussé** | « range tout ce qui est à trier », « sois plus poussé » | Deux passes : les évidences, puis le contexte (montant récurrent, même jour du mois, commerçant voisin, historique du même libellé). Préfère une règle (`rule`) à dix rangements isolés, et un nom lisible (`rename`) pour un libellé obscur. |
| **Enveloppes** | « propose des enveloppes plus précises » | Créations, fusions ou montants d'après les 12 derniers mois (moyenne de `stats.py`). Un budget rare a une remise à zéro et un rythme (« 26 € jusqu'au 1er janvier, puis tous les 3 mois »). Plan : `envelope` puis `move`/`rule`. |
| **Vérification d'un compte** | « vérifie le compte courant du 1er au 31 août » | Lance `verifier.py` avec `--compte`, `--du`, `--au`. Doublons, dates bancaires éloignées de l'achat, dépense hors de la période de son enveloppe, remboursement non rapproché. Plan : `flag` pour ce qu'il doit vérifier lui-même, `move` pour une erreur sûre. |
| **Récap du budget** | « fais le point sur septembre » | Réalisé face au budget, enveloppe par enveloppe et par famille ; écarts chiffrés avec leur référence (« −40 € vs sept. ») ; projection fin de mois ou d'année. Pas de plan sauf erreur repérée. |
| **Revue des habitudes** | « fais-moi une revue détaillée de mes habitudes » | Une page HTML soignée à partir de `templates/revue.html` (voir plus bas). |

## Tes propres catégories d'analyse

Tu es libre de regrouper les dépenses à ta façon pour l'analyse, plus finement que les enveloppes de
l'app : « alcool » au bar comme au supermarché, « livraison de repas », « abonnements numériques »,
« transport du quotidien ». Dis toujours comment tu as construit chaque regroupement (quels libellés,
quelle règle) pour qu'il puisse le vérifier. Ces regroupements ne changent rien dans l'app ; si l'un
d'eux mérite d'y exister, propose-le dans le plan (`envelope`, `rule`).

## Règles de réponse

- Le chiffre avant la phrase. Un écart porte son signe et sa référence.
- Pas de généralités (« pensez à faire attention ») : chaque conseil est chiffré et réalisable.
- Une confiance par proposition : `haute` (évident), `moyenne` (probable), `basse` (à vérifier). L'app
  décoche d'office les confiances basses.
- Signale une donnée incohérente plutôt que de la corriger en silence.
- Le plan va dans **un seul** bloc ```json``` à la fin de la réponse, après une phrase qui dit
  combien d'actions il contient. Format exact dans `PLAN.md` ; ne l'invente pas.
- Les enveloppes existantes se désignent par leur identifiant `env_…` ; une enveloppe que le plan
  crée, par son nom exact ; « hors budget » pour une sortie qui n'est pas une dépense (virement
  interne, remboursement à soi-même).

## La revue des habitudes

Copie `templates/revue.html`, remplace chaque `{{…}}` par le contenu, et rends la page (artifact ou
fichier HTML). Sections, toutes chiffrées :
1. Synthèse en trois lignes.
2. Où part l'argent : familles et catégories sur 12 mois glissants, ce qui monte, ce qui baisse.
3. Habitudes : commerçants fréquents, petites dépenses répétées, semaine contre week-end, saisonnalité.
4. Abonnements et prélèvements : total mensuel, ceux qui ont augmenté ou qui semblent oubliés.
5. Tes regroupements d'analyse (voir plus haut), avec leur méthode.
6. Trois leviers chiffrés pour les mois qui viennent, le plus rentable d'abord.
7. Les questions que tu poserais pour aller plus loin.
