#!/usr/bin/env bash
# Compose Play Store-ready 1080x1920 screenshots from the raw 1080x2424 captures
# in screenshots/raw/. Layout: light lavender bg, brand-purple headline,
# gray subtitle, screenshot floats centered below with a soft drop shadow.
# No phone frame.
#
# Requires ImageMagick (`brew install imagemagick`).
# Re-run after replacing any raw capture to regenerate the composited version.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$SCRIPT_DIR/screenshots/raw"
OUT_DIR="$SCRIPT_DIR/screenshots"

# Canvas
W=1080
H=1920
BG='#FAF7FF'              # very light lavender
HEADLINE_COLOR='#3D2C8D'  # deep brand purple
SUBTITLE_COLOR='#6B6B7B'  # medium gray
FONT_BOLD='/System/Library/Fonts/Supplemental/Arial Bold.ttf'
FONT_REG='/System/Library/Fonts/Supplemental/Arial.ttf'

# Screenshot region (preserves 1080:2424 source aspect)
SHOT_W=662
SHOT_H=1486
SHOT_Y=400

build() {
  local input="$1" out="$2" headline="$3" subtitle="$4"

  local hdr_png="$OUT_DIR/.hdr.png"
  local sub_png="$OUT_DIR/.sub.png"
  local shot_png="$OUT_DIR/.shot.png"

  magick -background none -fill "$HEADLINE_COLOR" \
    -font "$FONT_BOLD" -pointsize 68 -gravity center -size 960x \
    caption:"$headline" "$hdr_png"

  magick -background none -fill "$SUBTITLE_COLOR" \
    -font "$FONT_REG" -pointsize 36 -gravity center -size 920x \
    caption:"$subtitle" "$sub_png"

  magick "$input" -resize ${SHOT_W}x${SHOT_H} \
    \( +clone -background "#00000040" -shadow 60x18+0+12 \) \
    +swap -background none -layers merge +repage "$shot_png"

  magick -size ${W}x${H} "xc:$BG" \
    "$hdr_png" -gravity North -geometry +0+130 -composite \
    "$sub_png" -gravity North -geometry +0+240 -composite \
    "$shot_png" -gravity North -geometry +0+${SHOT_Y} -composite \
    "$out"

  rm -f "$hdr_png" "$sub_png" "$shot_png"
}

build "$SRC_DIR/01-otps.png"          "$OUT_DIR/01-otps.png" \
  "OTPs at your fingertips" "Codes pulled out automatically — copy with a tap"

build "$SRC_DIR/02-personal.png"      "$OUT_DIR/02-personal.png" \
  "People, not noise" "Real conversations stay front and center"

build "$SRC_DIR/03-transactions.png"  "$OUT_DIR/03-transactions.png" \
  "Banking, organized" "Debits, credits and payments — all in one tab"

build "$SRC_DIR/04-services.png"      "$OUT_DIR/04-services.png" \
  "Bills, plans, deliveries" "Everyday updates in one calm place"

build "$SRC_DIR/05-promotions.png"    "$OUT_DIR/05-promotions.png" \
  "Sales kept aside" "Promos don't bury what matters"

build "$SRC_DIR/06-government.png"    "$OUT_DIR/06-government.png" \
  "Official, set apart" "UIDAI, IT Dept, EPFO — all in one tab"

build "$SRC_DIR/07-mom-thread.png"    "$OUT_DIR/07-mom-thread.png" \
  "Conversations that flow" "Material You, contact-aware, distraction-free"

build "$SRC_DIR/08-settings.png"      "$OUT_DIR/08-settings.png" \
  "Light, dark, dynamic" "Theming that follows your phone"

build "$SRC_DIR/09-search-empty.png"  "$OUT_DIR/09-search-empty.png" \
  "Find anything in seconds" "Search every category at once"

build "$SRC_DIR/10-search-otp.png"    "$OUT_DIR/10-search-otp.png" \
  "Smart search with highlights" "See your match right inside each message"

echo "Built $(ls "$OUT_DIR"/*.png | wc -l | tr -d ' ') screenshots in $OUT_DIR"
