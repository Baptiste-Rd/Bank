#!/usr/bin/env bash
# Captures d'écran de l'app en mode démo, sur l'émulateur de la CI.
# Usage : scripts/screenshots.sh <apk> <dossier de sortie>
set -euo pipefail
APK="$1"
OUT="$2"
PKG="fr.baptiste.bank"
mkdir -p "$OUT"

adb install -r "$APK"
# Pas de proposition de mise à jour ni de fenêtre système pendant les captures.
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0

shoot() {
  local style="$1" tab="$2" theme="$3"
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$PKG/.MainActivity" --ez demo true --es style "$style" --es tab "$tab" > /dev/null
  sleep 8
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
