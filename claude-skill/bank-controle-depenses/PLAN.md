# Format du plan de corrections (bank-plan, version 1)

Le plan est un objet JSON, dans **un seul** bloc ```json``` à la fin de ta réponse. Baptiste le colle
dans l'app (« Contrôle avec Claude », « Coller la réponse de Claude ») ; l'app affiche chaque action
avec une case à cocher. N'invente pas d'autre champ ni d'autre type.

```json
{
  "format": "bank-plan",
  "version": 1,
  "export_id": "2026-11-03-a7c2",
  "actions": [
    {"type": "envelope", "name": "Coiffeur", "family": "rare", "amount": 26, "every": 3, "unit": "mois",
     "reset": "2027-01-01", "repeats": true, "category": "Santé",
     "reason": "4 passages en 12 mois, 26 € en moyenne", "confidence": "haute"},
    {"type": "move", "operation": "op_7f3a0c11de", "envelope": "Coiffeur",
     "reason": "SALON MARIE, 26 € tous les 3 mois", "confidence": "haute"},
    {"type": "move", "operation": "op_0b44de9a12", "envelope": "env_91be42aa07", "name": "Boulangerie Paul",
     "keyword": "PAUL", "reason": "rangé en Sorties, c'est une boulangerie", "confidence": "moyenne"},
    {"type": "move", "operation": "op_55aa01f3c0", "envelope": "hors budget",
     "reason": "virement vers soi-même", "confidence": "moyenne"},
    {"type": "rule", "pattern": "BOULANGERIE", "envelope": "env_91be42aa07",
     "reason": "12 achats rangés à la main dans Courses", "confidence": "haute"},
    {"type": "rename", "pattern": "SAS LDC", "name": "Salle de sport",
     "reason": "prélèvement mensuel de 29,90 €", "confidence": "moyenne"},
    {"type": "flag", "operation": "op_9c00ab12ef", "issue": "doublon probable",
     "note": "même montant, même commerçant, même jour que op_9c00ab12f0"}
  ]
}
```

## Les types

| Type | Champs obligatoires | Effet dans l'app |
| --- | --- | --- |
| `envelope` | `name`, `family` (`quotidien`, `charge`, `rare`, `épargne`), `amount` (euros) | Crée le budget (départ : le 1er du mois). Facultatif : `category`, `every` + `unit` (`mois`, `an`, `semaine` ; absent : chaque mois), `reset` (AAAA-MM-JJ, budget rare), `repeats` (faux : ponctuel). Créé seulement si le nom n'existe pas déjà. |
| `move` | `operation` (`op_…`), `envelope` (`env_…`, nom d'une enveloppe créée par le plan, ou `hors budget`) | Range l'opération. Facultatif : `name` (nom affiché), `keyword` (texte qui rangera aussi les suivantes). |
| `rule` | `pattern`, `envelope` | Tout libellé qui contient `pattern` ira dans l'enveloppe ; appliquée aussitôt à ce qui est à trier. |
| `rename` | `pattern`, `name` | Tout libellé qui contient `pattern` s'affiche `name`. |
| `flag` | `issue` | Rien n'est appliqué : Baptiste vérifie lui-même. Facultatif : `operation`, `note`. |

Chaque action peut porter `reason` (une phrase, chiffrée si possible) et `confidence` (`haute`,
`moyenne`, `basse`). L'app applique dans cet ordre : créations, rangements, règles, noms.
