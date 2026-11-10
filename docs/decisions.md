# Bank : décisions de produit

Le seul endroit où une décision de produit s'écrit. Baptiste tranche ; Claude prépare. Une
décision « proposée » ne se développe pas. Une décision « prise » se change par une nouvelle
entrée qui cite l'ancienne, jamais en la réécrivant.

Pour répondre : remplace `proposée` par `prise le AAAA-MM-JJ : option X`, et ajoute une ligne si
tu veux nuancer.

## Modèle d'une entrée

```
## Dn · La question, en une ligne
- État : proposée | prise le AAAA-MM-JJ : option X | remplacée par Dm
- Pourquoi maintenant : l'élément du backlog qui attend.
- Option A : ce qu'elle change · gain · coût.
- Option B : ce qu'elle change · gain · coût.
- Recommandation : A ou B, et la raison en une phrase.
- Comment le vérifier : la tâche ou la mesure qui dira si le choix était bon.
```

## D5 · Pour qui est l'app ?

- État : proposée
- Pourquoi maintenant : fixe la priorité de F6, F7 et E12. À trancher en premier.
- Option A, pour Baptiste seul : F6 et F7 passent en finition ; l'effort va aux chiffres et à la
  lisibilité · lots courts · l'app reste réglée sur une personne.
- Option B, pour d'autres aussi : premier lancement guidé, import de relevé sans compte
  développeur, plusieurs banques et revenus, publication signée · des usages réels · plusieurs
  mois de travail, et des obligations sur les données bancaires de tiers à faire vérifier.
- Recommandation : A maintenant. Les testeurs extérieurs sur le mode démo restent obligatoires :
  ils mesurent l'intuitivité, pas la diffusion.
- Comment le vérifier : la question se repose une fois l'app aboutie.

## D2 · Que faire d'une dépense pas encore triée ?

- État : proposée
- Pourquoi maintenant : L1, premier élément du lot B.
- Option A, la compter d'office au quotidien : elle réduit tout de suite le reste du mois, le tri
  la déplace ensuite · le chiffre n'est jamais trop optimiste · une grosse charge non triée fait
  chuter « Aujourd'hui » jusqu'au tri.
- Option B, l'afficher à côté : « 437 € · 65 € à trier non comptés » · aucun calcul à changer ·
  le grand chiffre reste faux.
- Recommandation : A, sauf pour une opération reconnue par une règle, rangée d'office.
- Comment le vérifier : tâche T1 avec trois opérations à trier ; nombre de jours où une opération
  reste à trier dans le journal d'usage.

## D3 · Quel second grand chiffre sur le Résumé ?

- État : proposée
- Pourquoi maintenant : L2.
- Option A, « Aujourd'hui 9 € » avec « sur 21 € par jour » dessous : baisse à chaque achat du
  jour · répond à la question fondatrice · peut devenir négatif en fin de journée.
- Option B, « Par jour 21 € » avec « 12 € dépensés aujourd'hui » : repère stable · le calcul du
  reste est laissé à la personne.
- Recommandation : A. Un chiffre négatif s'affiche en ambre avec « rattrapé demain : X € par
  jour », sans rouge.
- Comment le vérifier : tâche T1, cible de 10 s.

## D4 · Le quotidien : pot commun ou enveloppes strictes ?

- État : proposée
- Pourquoi maintenant : L7 ; change le sens de « Dépassé » pour une enveloppe du quotidien.
- Option A, pot commun assumé : une enveloppe du quotidien dépassée devient une information
  ambre, sans bouton de couverture · un seul signal, moins de gestes · ces enveloppes deviennent
  des repères.
- Option B, enveloppes strictes : « Aujourd'hui » se calcule sans les restes des autres
  enveloppes · chaque poste tient sa limite · plus de couvertures à faire, chiffre du jour plus bas.
- Recommandation : A. C'est déjà ce que fait le calcul, et c'est ce qui réduit la charge mentale.
- Comment le vérifier : tâche T3 ; nombre de couvertures faites par mois dans le journal d'usage.

## D1 · Quelle grandeur dans la colonne de Dépenses ?

- État : proposée
- Pourquoi maintenant : E1, premier élément du lot C. Peut attendre le tour de test 1.
- Option A, le dépensé partout : « 129 € » à droite, « sur 320 € » et la barre dessous, une puce
  si l'enveloppe dépasse · colonne additionnable, fidèle au nom de l'onglet · le reste demande un
  second coup d'œil.
- Option B, le reste partout : les branches affichent aussi un reste · répond à « combien
  encore ? » · additionne des restes de périodes différentes.
- Recommandation : A.
- Comment le vérifier : tâche T6, les deux questions posées sur chaque version à cinq testeurs.

## Décisions prises avant ce registre

Elles restent dans `docs/suivi.md` (« Lot 8 : décisions prises », « Décisions du 6 novembre »).
Une contradiction est à lever au lot D : `suivi.md` dit « Catégorie = type de dépense », alors que
`refonte-ux.md` et `produit.md` en font ce qui regroupe des enveloppes. `produit.md` l'emporte.
