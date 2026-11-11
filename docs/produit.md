# Bank : le produit

Ce document dit ce que l'app doit réussir, pour qui, et quand elle est aboutie. Il change rarement
et seulement sur décision de Baptiste (`docs/decisions.md`). En cas de conflit, il l'emporte sur
`docs/refonte-ux.md` (comment l'écran le montre) et sur `docs/suivi.md` (où en est le code).

## 1. La promesse

Bank répond en moins de dix secondes à une question : **combien puis-je dépenser aujourd'hui,
compte tenu de ce que j'ai décidé pour le mois et pour l'année ?**

Tout le reste sert cette réponse : la rendre juste (tri, banque, calculs), la rendre tenable
(enveloppes, provisions, épargne) et la rendre rassurante (annuler, comprendre d'où vient le
chiffre). Une fonction qui ne sert aucune de ces trois choses n'a pas sa place.

## 2. Pour qui

- **Aujourd'hui : Baptiste seul** (décision D5). Salarié, banque CIC, salaire versé
  en fin de mois pour le mois suivant, plusieurs livrets et placements, pas de formation en
  gestion de budget. Téléphone Android, une main, souvent debout.
- **L'étalon reste une personne qui n'a jamais vu l'app.** Un écran que seul son concepteur
  comprend n'est pas intuitif. Chaque écran se juge donc avec des testeurs extérieurs sur le mode
  démo (`docs/test-utilisateur.md`), même si l'app n'est pas diffusée.

## 3. Les six tâches qui définissent l'app

L'app existe pour ces six tâches. Une demande qui n'améliore aucune d'elles va dans « Idées »
(`docs/backlog.md`) et n'est pas développée avant que l'app soit aboutie.

| Tâche | Ce que la personne veut | Fréquence | Cible (4 testeurs sur 5, sans aide) |
| --- | --- | --- | --- |
| T1 | Savoir combien dépenser aujourd'hui, achats du jour déduits | Chaque jour | 10 s |
| T2 | Ranger les opérations nouvelles | Plusieurs fois par semaine | 60 s pour trois opérations |
| T3 | Voir quelle enveloppe dépasse, de combien, et la remettre à flot | Chaque semaine | 60 s |
| T4 | Créer ou modifier une enveloppe | Chaque mois | 60 s |
| T5 | Préparer une dépense future ou un objectif d'épargne | Chaque mois | 90 s |
| T6 | Savoir ce qui a été dépensé, et ce qui reste, sur un poste | Chaque semaine | 20 s |

Les cibles sont des valeurs de départ, à ajuster après le premier tour de test.

## 4. Quand l'app est aboutie

L'app est aboutie quand les huit conditions sont vraies en même temps. Avant cela, aucune fonction
nouvelle. Après cela, l'app passe en entretien : corrections seulement, sauf nouvelle décision.

1. **Constats.** Aucun constat de sévérité 3 ou 4 ouvert dans `docs/backlog.md`.
2. **Tâches.** T1 à T6 réussies par 4 testeurs extérieurs sur 5, sur deux tours de test de suite,
   avec une facilité perçue d'au moins 5,5 sur 7.
3. **Chiffres.** Les invariants du paragraphe 6 ont chacun un test qui passe.
4. **Données.** Une sauvegarde faite sur une version se restaure sur la suivante (test sur
   l'appareil). Aucune migration ne perd de donnée.
5. **Lisibilité.** Chaque écran a sa capture à 200 % sans chevauchement ; tout texte tient 4,5:1
   sur son fond réel, y compris sous la barre d'onglets ; toute cible tactile fait 48 dp.
6. **Notions.** L'écran ne montre que les mots du paragraphe 7 (`scripts/lexique.sh` le vérifie).
7. **Usage réel.** Trente jours de journal d'usage de Baptiste : au moins 27 jours où la réponse
   a été trouvée en moins de dix secondes, et aucun jour avec un chiffre faux.
8. **Dette.** Cinq « restes connus » au plus dans `docs/suivi.md`.

## 5. La méthode de budget que l'app applique

L'app est un hybride assumé de deux modèles connus. Elle doit le dire clairement, parce que les
mélanger sans le dire produit des signaux contraires à l'écran.

- **Ce qui est décidé d'avance est strict.** Charges fixes, dépenses rares et épargne sont des
  enveloppes fermées : chaque euro du salaire a une destination, l'épargne se décide en début de
  mois, une dépense irrégulière se prépare un peu chaque mois.
- **Le quotidien est un pot commun** (décision D4). Tout ce qui n'est pas décidé
  d'avance se dépense au jour le jour. Le chiffre qui pilote est « Aujourd'hui », pas le reste de
  chaque enveloppe du quotidien. Une enveloppe du quotidien est un repère : la dépasser est une
  information, pas une alerte, tant que le pot commun tient.

Règles qui en découlent, valables pour tous les mois et toutes les enveloppes :

1. Tout euro du salaire est soit dans une enveloppe, soit « à répartir », et « à répartir » fait
   partie du quotidien.
2. Une dépense compte dès qu'elle est connue, triée ou non (décision D2).
3. Un reste affiché est de l'argent réellement réuni, jamais une intention.
4. Un dépassement d'une enveloppe stricte se couvre par un geste explicite, qui dit d'où vient
   l'argent. On ne prend jamais d'office sur une enveloppe elle-même en retard.
5. Ce qui reste à la fin d'une période va à l'épargne ; un dépassement y est pris. Rien ne part
   seul dans une autre enveloppe.
6. Le passé ne se réécrit pas sans geste volontaire, et tout geste s'annule.
7. Un mois incomplet ou exceptionnel ne fausse pas les moyennes.
8. Se sentir maître de son budget compte autant que le montant épargné : l'app informe sans
   culpabiliser. Le rouge est réservé à ce qui demande un geste.

## 6. Les invariants des chiffres

Chaque invariant a un test dans `domain/`. Un lot qui en casse un ne se fusionne pas.

- **I1.** Un même nom donne le même nombre sur tous les écrans, pour le même mois.
- **I2.** Chaque dépense du mois est comptée une fois et une seule dans le dépensé du mois.
- **I3.** Reste du mois = budget du quotidien − dépensé du quotidien, opérations à trier comprises.
- **I4.** Aujourd'hui = part du jour − dépenses du jour. La part du jour ne change pas en cours de
  journée.
- **I5.** La somme des lignes du plan du mois est égale au salaire du mois.
- **I6.** Le reste affiché d'une enveloppe ne dépasse jamais l'argent réuni pour elle.
- **I7.** Une enveloppe qui a son propre montant montre son reste et son état, qu'elle contienne
  d'autres enveloppes ou non.
- **I8.** Un mois terminé ne change que par un geste daté et inscrit à l'Historique.
- **I9.** Annuler un geste rend exactement l'état d'avant.
- **I10.** Les données de démo sont produites par les mêmes fonctions que les données réelles.

## 7. Les notions autorisées à l'écran

Douze noms et six verbes. Ajouter un mot à l'écran demande d'en retirer un et une
décision de Baptiste. Le détail d'usage reste dans `docs/refonte-ux.md`, qui doit s'aligner ici.

| Nom | Sens unique |
| --- | --- |
| Aujourd'hui | Ce qui peut encore être dépensé aujourd'hui |
| Reste du mois | Ce qui reste au quotidien jusqu'à la fin du mois |
| Enveloppe | Ce que l'on budgète et où l'on range une dépense |
| Catégorie | Une enveloppe qui en contient d'autres, pour les regrouper |
| Dépensé | Ce qui est sorti, sur le mois ou sur la période de l'enveloppe |
| Reste | Ce qui est réuni et pas encore dépensé dans une enveloppe |
| Dépassé | Dépensé au-delà de ce qui était réuni |
| À trier | Opérations pas encore rangées |
| À répartir | Salaire rangé dans aucune enveloppe |
| Épargne | Ce qui est mis de côté ce mois-ci et ce qui est sur les livrets |
| Objectif | Une épargne avec un montant visé, et une date si on veut |
| Disponible sur le compte | Argent des mois passés, hors budget du mois |

Verbes : **Ajouter**, **Retirer** (de l'argent dans une enveloppe), **Ranger** (une opération dans
une enveloppe, une enveloppe dans une catégorie), **Placer** (de l'épargne sur un compte ou un
objectif), **Arrêter** (une enveloppe), **Annuler**.

Mots à retirer de l'écran et leur remplaçant :

| Retiré | Remplacé par |
| --- | --- |
| Mise de côté, Mettre de côté, Somme mise à part | Une enveloppe « financée en une fois » (option du formulaire) ; pour l'épargne, « Placer » |
| mis de côté (provision) | « réuni » : « 900 € réunis sur 1 800 € » |
| à ranger, à verser, à placer, Répartir (épargne) | « à placer », action « Placer » |
| Achat prévu | Une enveloppe « Rare ou ponctuel » avec une date |
| Remise à zéro, période | « Se renouvelle le » ; « par mois », « par an » |
| Par jour (grand chiffre) | « Aujourd'hui », avec « sur X € par jour » dessous (décision D3) |
| Ajouter de l'argent | « Ajouter », qui porte le montant quand l'enveloppe dépasse |

Provision, famille, réserve, portefeuille, allocation, couverture sont des mots du code. Ils ne
s'affichent pas.

## 8. Ce que l'app ne fait pas

Tant qu'elle n'est pas aboutie, et sauf décision écrite :

- pas de multi-utilisateur, de compte partagé ni de synchronisation entre appareils ;
- pas de conseil en placement ni de calcul fiscal au-delà du net estimé existant ;
- pas de devises, pas de crédit à suivre, pas de facture à scanner ;
- pas de graphique de plus tant qu'un graphique existant n'est pas retiré ;
- pas de réglage pour contourner un défaut : on corrige le défaut ;
- pas de nouvelle intégration de Claude (lot 9 reporté) ;
- pas de thème clair, pas de version tablette.

## 9. Les principes d'interface, en six lignes

Le détail est dans `docs/refonte-ux.md`. Ce qui prime :

1. **Une question par écran, un chiffre par carte.** Le chiffre avant la phrase.
2. **Une colonne ne porte qu'une grandeur.** Jamais un dépensé et un reste dans la même colonne.
3. **Simple ne veut pas dire dépouillé.** On retire des notions, pas des libellés utiles.
4. **Un ou deux boutons en avant par écran,** aux libellés distincts. Une action qui détruit n'est
   jamais le bouton principal.
5. **Chaque geste a une alternative visible** (un glissement, un appui long) et une annulation.
6. **L'effet visuel ne coûte jamais de lisibilité.** Le verre s'efface devant le texte.
