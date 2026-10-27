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
  for tab in HOME ENVELOPES OPERATIONS; do
    shoot GLASS "$tab" "$theme"
  done
  shoot CLASSIC HOME "$theme"
done
