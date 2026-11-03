# Format de l'export (bank-export, version 1)

Fichier JSON produit par l'app Bank (menu ⋮, « Contrôle avec Claude », « Exporter et partager »).
Il couvre douze mois glissants au plus, depuis le début du suivi.

```json
{
  "format": "bank-export",
  "version": 1,
  "export_id": "2026-11-03-a7c2",
  "created": "2026-11-03",
  "currency": "EUR",
  "period": {"from": "2026-01-01", "to": "2026-11-03"},
  "accounts": [{"id": "acc_3f1a9b0c2d", "name": "Compte courant", "type": "CURRENT"}],
  "envelopes": [{
    "id": "env_91be42aa07", "name": "Courses", "category": "Nourriture",
    "family": "quotidien", "amount": 320.0, "rhythm": "chaque mois",
    "repeats": true, "keeps_leftover": false,
    "start": "2026-08-01", "reset": null, "stopped": null,
    "periods": [{"start": "2026-10-01", "end": "2026-10-31", "budget": 320.0, "spent": 306.6}]
  }],
  "rules": [{"pattern": "CARREFOUR", "envelope": "env_91be42aa07"}],
  "operations": [{
    "id": "op_7f3a0c11de", "date": "2026-10-28", "booking_date": "2026-10-30",
    "amount": -38.7, "label": "PAIEMENT CB 2810 PARIS CARREFOUR CITY",
    "account": "acc_3f1a9b0c2d", "source": "banque", "status": "rangée",
    "envelope": "env_91be42aa07", "by_rule": true
  }]
}
```

## Champs

| Champ | Sens |
| --- | --- |
| `envelopes[].family` | `quotidien` (budget du jour), `charge` (prélèvement fixe), `rare` (budget rare ou ponctuel, avec remise à zéro), `épargne`. |
| `envelopes[].amount` | Montant par période du rythme (`rhythm` : « chaque mois », « tous les 3 mois »…). |
| `envelopes[].start` | Départ : une dépense antérieure peut y être rangée mais ne compte pas dans son reste. |
| `envelopes[].reset` | Remise à zéro du premier budget (budget rare). `repeats: false` : ponctuel. |
| `envelopes[].stopped` | Date d'arrêt (ou « oui » si arrêtée sans date connue) ; `null` si active. |
| `envelopes[].periods` | Budgets enregistrés (le passé est figé) et ce qui y a été dépensé, en euros positifs. |
| `operations[].date` | Jour de l'achat ; `booking_date` : jour d'inscription par la banque (débit différé : jusqu'à 45 jours après). |
| `operations[].amount` | Négatif pour une sortie, positif pour une rentrée. |
| `operations[].label` | Libellé nettoyé, ou le nom choisi dans l'app. Virements entre personnes : « Virement reçu · émetteur N » / « Virement émis · destinataire N » (N stable pour un même émetteur) ; le salaire reconnu : « Salaire ». |
| `operations[].source` | `banque` ou `saisie` (dépense entrée à la main). |
| `operations[].status` | `rangée`, `à trier`, `hors budget`, `vers mes comptes` (virement vers un livret ou un placement : de l'épargne, pas une dépense). |
| `operations[].by_rule` | Rangée par une règle de tri (sinon à la main ou par Claude). |

## Ce qui n'y est jamais

IBAN, numéros de carte ou de référence, suites de cinq chiffres ou plus (codes postaux compris),
soldes, identifiants internes, noms des personnes dans les virements. Les identifiants `op_`, `env_`,
`acc_` sont des empreintes : seule l'app sait les relier à ses données.
