#!/usr/bin/env bash
# Le lexique de l'app (docs/refonte-ux.md) : une notion, un mot. Ce script échoue si un mot retiré
# réapparaît dans un texte affiché (une chaîne entre guillemets des écrans).
set -euo pipefail
cd "$(dirname "$0")/.."
retired=(
  'Nouveau budget'
  'Modifier le budget'
  'Budgets à créer'
  'Déclasser'
  'Journal des transferts'
  'Transferts'
  'épargnés'
  'Règles et alias'
  'règle(s)'
  'dépenses à trier'
  '"Gérer"'
  '"Détail"'
  'Rare ou projet'
)
status=0
for word in "${retired[@]}"; do
  # Seulement les lignes qui portent une chaîne affichée, pas les commentaires.
  hits=$(grep -rnF -- "$word" app/src/main/kotlin/fr/baptiste/bank/ui | grep -v '^\S*:\s*[0-9]*:\s*\(//\|\*\)' | grep '"' || true)
  if [ -n "$hits" ]; then
    echo "Mot retiré « $word » :"
    echo "$hits"
    status=1
  fi
done
[ $status -eq 0 ] && echo "Lexique respecté."
exit $status
