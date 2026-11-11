# Bank : ce qu'il reste à faire

La seule liste de travail. Le prochain lot est le premier élément ouvert, dans l'ordre. Un
élément se ferme à la clôture de son lot (`[x]`, build, date). Origine des constats : le rapport
d'analyse du 10 novembre 2026 (commit `4c898ec`) ; les suivants viendront des tours de test et du
journal d'usage.

Sévérité : 4 perte de données ou blocage · 3 gêne majeure sur une tâche · 2 gêne contournable ·
1 finition. « Décision » renvoie à `docs/decisions.md` : l'élément attend qu'elle soit tranchée.

## Lot A · Protéger les données

- [ ] **F1** (4) Sauvegarde et restauration. *Réussi quand :* « Sauvegarder » écrit un fichier
  chiffré par mot de passe dans un dossier choisi, sans la clé bancaire ; « Restaurer » rend
  enveloppes, opérations, règles, objectifs et Historique ; un test sur l'appareil restaure une
  sauvegarde du schéma précédent. *Recette :* sauvegarder, désinstaller, réinstaller, restaurer,
  retrouver le même « Reste du mois ».
- [ ] **F2** (3) Clé de signature hors du dépôt. Après F1. *Réussi quand :* la clé est un secret
  de la CI, `signing/` n'existe plus dans le dépôt, l'ancienne clé n'est plus acceptée.
  *Recette :* installer la nouvelle build, restaurer la sauvegarde.
- [ ] **F3** (2) Verrouillage facultatif. *Réussi quand :* un réglage demande l'empreinte à
  l'ouverture ; l'aperçu des apps récentes ne montre aucun montant.

## Lot B · Des chiffres justes

- [ ] **L1** (3) Opérations à trier comptées. Décision D2 prise : option A. *Réussi quand :*
  l'invariant I3 a son test ; une dépense non triée réduit le reste du mois ; la trier ne change
  pas ce reste si elle va au quotidien.
- [ ] **L2** (3) « Aujourd'hui » affiché. Décision D3 prise : option A. *Réussi quand :* I4 a son
  test ; le Résumé montre ce qui reste aujourd'hui, achats du jour déduits. *Recette :* saisir
  une dépense de 10 €, voir le chiffre baisser de 10 €.
- [ ] **L3** (3) Enveloppe parente avec son reste. *Réussi quand :* I7 a son test ; dans la démo,
  Sorties montre « Dépassé » dans Dépenses comme sur le Résumé.
- [ ] **L4** (3) Reste d'une provision = argent réuni. *Réussi quand :* I6 a son test ; la fiche
  Vacances affiche le réuni en grand et l'objectif de l'année en second.
- [ ] **L5** (2) Sources d'une couverture. *Réussi quand :* une enveloppe en retard n'est jamais
  proposée d'office ; test sur `coverSources`.
- [ ] **L9** (1) Démo calculée par les vraies fonctions. *Réussi quand :* I10 a son test ;
  l'objectif Voiture de la démo affiche la part que `goalMonthly` calcule.

## Tour de test 1

- [ ] **M1** (3) Cinq personnes extérieures, tâches T1 à T6, sur la build du lot B
  (`docs/test-utilisateur.md`). Les résultats ferment, confirment ou abaissent les constats E1 à
  E8, et ajoutent ici ce que trois testeurs ont rencontré.

## Lot C · Lisibilité

- [ ] **E1** (3) Une seule grandeur dans la colonne de Dépenses. Décision D1 prise : option A.
- [ ] **E2** (3) Fiche : « Ajouter » et « Retirer » seulement. *Réussi quand :* « Ajouter de
  l'argent » n'existe plus ; « Ajouter » porte le montant manquant quand l'enveloppe dépasse.
- [ ] **E4** (3) Barre d'onglets lisible. *Réussi quand :* aucun texte ne se lit à travers la
  barre ; le libellé d'onglet tient 4,5:1 sur les captures défilées, à 100 % et à 200 %.
- [ ] **E5** (3) Texte à 200 %. *Réussi quand :* chaque écran a sa capture à 200 % sans
  chevauchement ni texte coupé.
- [ ] **E10** (2) Cibles de 48 dp : chevron de l'arbre, barre des mois.

## Lot D · Moins de mots, moins d'écrans

- [ ] **E3** (3) Vocabulaire aligné sur `docs/produit.md`, paragraphe 7. *Réussi quand :*
  `scripts/lexique.sh` refuse chaque mot retiré ; `refonte-ux.md` et `suivi.md` ne se
  contredisent plus sur « Catégorie ».
- [ ] **E8** (2) Créer une enveloppe : le + propose « Dépense » et « Enveloppe » ; quatre usages
  dans le formulaire ; « financée en une fois » en option.
- [ ] **E6** (2) Dépenses : le filtre devient des puces, affichées au-delà de dix enveloppes.
- [ ] **E7** (2) Épargne : Épargne prévue, Objectifs, Comptes ; le reste derrière un lien.
- [ ] **L6** (2) Périmètre nommé pour chaque « budget du mois » du Résumé et du bilan.
- [ ] **L7** (2) Règle du quotidien dite à l'écran. Décision D4 prise : option A.
- [ ] **E9** (2) Historique : trente jours ou cinquante entrées ; un tri en série = une entrée ;
  accès depuis le message « Annuler ».
- [ ] **E11** (2) Raccourcis du tri classés : libellé proche, enveloppe la plus utilisée, montant
  voisin.

## Tour de test 2

- [ ] Cinq nouvelles personnes, mêmes tâches. Condition 2 de la définition d'abouti.

## Ensuite, dans cet ordre

- [ ] **L8** (2) Salaire : « finance ce mois » ou « le mois suivant », réglable par revenu.
- [ ] **F4** (2) Widget « Aujourd'hui », synchronisation de fond, deux notifications au plus.
- [ ] **F6** (1) Premier lancement en trois étapes. D5 = A : finition.
- [ ] **E12** (2) « Vérifier les dates » en une phrase ; détail derrière ⓘ.
- [ ] **F5** (2) Aide de Claude : seulement après E11.
- [ ] **M2** (2) Grille de `revue-ux` complétée (fait dans le fichier de l'agent ; à vérifier au
  premier lot qui touche l'interface).
- [ ] **E13**, **F8** (1) Finitions : avec le lot qui touche le même écran.

## Idées (gelées)

Une ligne par idée, avec sa date et la tâche qu'elle servirait. Rien ici ne se développe avant
que l'app soit aboutie. À la relecture mensuelle, une idée qui ne sert aucune tâche est supprimée.

- 2026-11-09 · Lot 9, revue du tri par Claude depuis le bilan d'un mois · T2
- 2026-11-09 · « Prévoir une dépense » · T5
- 2026-11-10 · Connexion à la banque sans profil de développeur (D5 = A) · aucune tâche
