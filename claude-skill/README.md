# Skill Claude « bank-controle-depenses »

Contrôle et analyse des dépenses exportées par l'app (menu ⋮, « Contrôle avec Claude »).

- `bank-controle-depenses/` : le skill (SKILL.md, formats de l'export et du plan, scripts, modèle de revue).
- `bank-controle-depenses.zip` : le même dossier, prêt à téléverser sur claude.ai (Paramètres,
  Fonctionnalités, Skills ; exécution de code activée).

Le fichier exporté par l'app ne contient ni IBAN, ni numéro de carte ou de référence, ni solde, ni
nom de personne dans les virements. Le plan rendu par Claude se colle dans l'app et se valide ligne
par ligne. Après une modification du skill, refaire le zip et le téléverser à nouveau.
