#!/usr/bin/env bash
# Captures d'écran de l'app en mode démo, sur l'émulateur de la CI.
# Usage : scripts/screenshots.sh <apk> <dossier de sortie>
set -euo pipefail
APK="$1"
OUT="$2"
PKG="fr.baptiste.bank"
mkdir -p "$OUT"

# L'émulateur logiciel est lent au démarrage : on le laisse se stabiliser, et on masque les
# messages « l'application ne répond pas » qui finiraient sur les captures.
sleep 30
adb shell settings put global hide_error_dialogs 1 || true
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell input keyevent KEYCODE_HOME
adb install -r "$APK"

close_dialogs() {
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null 2>&1 || true
}

# Premier lancement : remplit la base de démo, puis on laisse le temps à l'écriture de finir.
adb shell am start -W -n "$PKG/.MainActivity" --ez demo true > /dev/null
sleep 40

shoot() {
  local style="$1" tab="$2" theme="$3"
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$PKG/.MainActivity" --ez demo true --es style "$style" --es tab "$tab" > /dev/null
  sleep 8
  close_dialogs
  sleep 1
  adb exec-out screencap -p > "$OUT/${style,,}-${tab,,}-${theme}.png"
  echo "capture : ${style} ${tab} ${theme}"
}

for theme in clair sombre; do
  if [ "$theme" = sombre ]; then adb shell cmd uimode night yes; else adb shell cmd uimode night no; fi
  sleep 2
  for tab in HOME BUDGET SAVINGS OPERATIONS; do
    shoot GLASS "$tab" "$theme"
  done
  shoot CLASSIC HOME "$theme"
done

# Mouvement : une courte vidéo de l'interface verre (changement d'onglet, défilement), pour
# vérifier ce qu'une capture fixe ne montre pas. Les animations sont réactivées le temps de la
# vidéo. Coordonnées pour un Pixel 7 (1080 x 2400) : centres des trois onglets de la barre.
adb shell cmd uimode night yes
adb shell settings put global animator_duration_scale 1
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.MainActivity" --ez demo true --es style GLASS --es tab HOME > /dev/null
sleep 8
close_dialogs
sleep 1
adb shell screenrecord --time-limit 12 --bit-rate 8000000 /sdcard/motion.mp4 &
REC=$!
sleep 1.5
adb shell input tap 540 2226; sleep 1.5
adb shell input tap 774 2226; sleep 1.5
adb shell input tap 306 2226; sleep 1.5
# Défilement lent : les cartes passent sous la barre d'onglets puis sous les boutons du haut.
adb shell input swipe 540 1700 540 900 1800; sleep 1
adb exec-out screencap -p > "$OUT/glass-home-defile.png"
adb shell input swipe 540 1500 540 700 1800; sleep 1.5
adb exec-out screencap -p > "$OUT/glass-home-defile2.png"
wait $REC || true
adb pull /sdcard/motion.mp4 "$OUT/glass-mouvement.mp4" || echo "pas de vidéo"
adb shell settings put global animator_duration_scale 0

# Connexion bancaire (base de démo, réglages bancaires de démo séparés).
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.MainActivity" --ez demo true --es style GLASS --es tab HOME --es dialog BANK > /dev/null
sleep 8
close_dialogs
sleep 1
adb exec-out screencap -p > "$OUT/glass-banque.png"
adb shell input swipe 540 1800 540 600 600
sleep 2
adb exec-out screencap -p > "$OUT/glass-banque-suite.png"

# Scénario épargne : l'accueil défilé jusqu'au bloc Épargne, la liste des enveloppes avec
# l'achat prévu, et le formulaire d'un nouvel achat prévu.
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.MainActivity" --ez demo true --es style GLASS --es tab HOME > /dev/null
sleep 8
close_dialogs
adb shell input swipe 540 1900 540 500 900
sleep 2
adb exec-out screencap -p > "$OUT/glass-epargne.png"
adb shell input swipe 540 1900 540 900 900
sleep 2
adb exec-out screencap -p > "$OUT/glass-epargne-suite.png"
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.MainActivity" --ez demo true --es style GLASS --es tab BUDGET > /dev/null
sleep 8
close_dialogs
adb shell input swipe 540 1900 540 400 900
sleep 2
adb exec-out screencap -p > "$OUT/glass-envelopes-suite.png"
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.MainActivity" --ez demo true --es style GLASS --es tab BUDGET --es dialog PLANNED > /dev/null
sleep 8
close_dialogs
adb exec-out screencap -p > "$OUT/glass-achat-prevu.png"

# Budget : groupes, mois précédent, vue à l'année (paramètres de lancement, pas de tap).
shot_budget() {
  local name="$1"; shift
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$PKG/.MainActivity" --ez demo true --es style GLASS "$@" > /dev/null
  sleep 8
  close_dialogs
  sleep 1
  adb exec-out screencap -p > "$OUT/$name.png"
}
shot_budget glass-budget-mois-precedent --es tab BUDGET --ei monthsBack 1
shot_budget glass-epargne-mois-precedent --es tab SAVINGS --ei monthsBack 1
shot_budget glass-budget-annee --es tab BUDGET --es budgetView YEAR
adb shell input swipe 540 1900 540 800 900
sleep 2
adb exec-out screencap -p > "$OUT/glass-budget-annee-suite.png"
