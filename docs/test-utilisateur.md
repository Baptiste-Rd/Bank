# Bank : tester avec de vraies personnes

Deux sources de vérité extérieures au projet : des testeurs qui découvrent l'app, et le journal
d'usage de Baptiste. Les relecteurs automatiques ne les remplacent pas.

## 1. Tour de test

**Qui.** Cinq personnes par tour, qui n'ont jamais vu l'app, de préférence pas développeurs.
Jamais deux fois la même personne : au second tour, elle connaît déjà l'écran. Baptiste observe,
il ne teste pas.

**Avec quoi.** Le téléphone de Baptiste en mode démo (aucune donnée réelle), la build notée en
tête de la feuille de résultats. 45 minutes par personne.

**Consigne de départ,** à lire telle quelle : « C'est une app de budget. Je teste l'app, pas toi :
si quelque chose n'est pas clair, c'est elle qui a tort. Dis à voix haute ce que tu regardes et
ce que tu penses. Je ne pourrai pas t'aider pendant les tâches. »

**Les six tâches,** dans cet ordre, lues telles quelles :

| Tâche | Consigne | Réussie si | Cible |
| --- | --- | --- | --- |
| T1 | « Tu as acheté du pain ce matin. Combien peux-tu encore dépenser aujourd'hui ? » | Le bon montant est dit | 10 s |
| T2 | « Des achats récents ne sont pas encore rangés. Range-les. » | Les trois sont rangés au bon endroit | 60 s |
| T3 | « Un de tes budgets est dépassé. Lequel, de combien ? Arrange ça. » | Enveloppe et montant justes, puis couverte | 60 s |
| T4 | « Tu t'inscris au sport : 50 € par mois. Prévois-le. » | Enveloppe mensuelle de 50 € créée | 60 s |
| T5 | « Tu veux 2 000 € pour un voyage en juin. Prépare-le. » | Objectif ou enveloppe datée, montant juste | 90 s |
| T6 | « Combien as-tu dépensé en nourriture ce mois-ci ? Et que reste-t-il pour les vacances ? » | Les deux montants justes | 20 s |

**Pendant.**

- Chronométrer de la fin de la consigne à la réponse ou au dernier geste.
- Ne pas aider. Relances permises : « Que vois-tu ? », « Qu'attends-tu de ce bouton ? ».
- Une aide donnée compte comme un échec partiel. Abandon après trois fois la cible : échec.
- Noter chaque retour en arrière, chaque « Annuler », chaque mot lu à voix haute avec hésitation.

**Après chaque tâche :** « De 1, très difficile, à 7, très facile, c'était comment ? »

**À la fin :** « Qu'est-ce qui t'a le plus gêné ? », « Qu'est-ce que tu n'as pas compris ? »,
« Avec tes mots, que veut dire Enveloppe ? À répartir ? Aujourd'hui ? ».

**Feuille de résultats,** une par tour, dans `docs/tests/AAAA-MM-JJ.md` :

```
Build : build-N · Date : · Testeurs : P1 à P5 (âge approximatif, à l'aise ou non avec les apps)

| Tâche | P1 | P2 | P3 | P4 | P5 | Réussites | Temps médian | Facilité médiane |
| T1 | R 8 s 6 | E | A 25 s 4 | ... |
(R réussi · A réussi avec aide · E échec ; puis le temps, puis la note de 1 à 7)

Ce que trois testeurs ou plus ont rencontré :
- écran, geste, ce qu'ils attendaient, ce qui s'est passé.

Mots mal compris :
```

**Lire les résultats.**

- Quatre réussites sur cinq sont un signal, pas une mesure : cela ne prouve pas 80 % de réussite.
- Un problème rencontré par trois testeurs entre au backlog en sévérité 3.
- Un constat du backlog que trois testeurs n'ont pas rencontré descend d'un cran.
- Un mot mal défini par trois testeurs est retiré ou remplacé (`docs/produit.md`, paragraphe 7).
- Les avis (« j'aimerais un graphique ») vont dans « Idées ». Seuls les comportements observés
  créent des constats.

## 2. Journal d'usage de Baptiste

Une ligne par jour d'ouverture de l'app, dans `docs/journal.md`. Trente secondes au plus.

```
AAAA-MM-JJ · ce que je voulais savoir ou faire · trouvé en moins de 10 s ? oui / non ·
chiffre juste ? oui / non / pas sûr · gêne éventuelle, en quelques mots
```

Exemple : `2026-11-14 · combien pour le restau ce soir · oui · pas sûr (3 à trier) · rien`

- Une gêne notée trois fois entre au backlog avec sa sévérité.
- « Chiffre juste : non » entre au backlog en sévérité 3 le jour même, avec une capture.
- Trente jours de suite remplissent la condition 7 de la définition d'abouti.

## 3. Ce que les relecteurs automatiques ne peuvent pas dire

`revue-naive` donne un premier signal à chaque lot, pour quelques minutes. Il ne tient pas le
téléphone, ne se trompe pas de doigt, ne se lasse pas et lit tout. Il peut écarter un écran
confus ; il ne peut pas en déclarer un intuitif. Seul un tour de test le peut.
