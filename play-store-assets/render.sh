#!/usr/bin/env bash
# Render frames.html into the Play Store images (1080x1920 screenshots + 1024x500 feature graphic).
# Needs Google Chrome. Re-run after capture.sh or after editing frames.html.
set -euo pipefail
cd "$(dirname "$0")"
CHROME="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
mkdir -p screenshots

render() { # id width height out
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars --allow-file-access-from-files \
    --force-device-scale-factor=1 --window-size="$2,$3" --virtual-time-budget=3000 \
    --screenshot="$PWD/$4" "file://$PWD/frames.html#$1" 2>/dev/null
}

i=1
for id in otps notification sorted logos thread search dark privacy; do
  render "$id" 1080 1920 "screenshots/0$i-$id.png"; i=$((i + 1))
done
render feature 1024 500 feature-graphic.png
magick mogrify -alpha off screenshots/*.png feature-graphic.png # Play requires no alpha channel
ls -1 screenshots feature-graphic.png
