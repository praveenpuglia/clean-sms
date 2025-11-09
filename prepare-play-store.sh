#!/bin/bash

echo "=== Clean SMS - Play Store Assets Preparation ==="
echo ""

# Create assets directory
mkdir -p play-store-assets/{screenshots,graphics}

echo "✓ Created play-store-assets directory structure"
echo ""
echo "📋 TODO - Asset Checklist:"
echo ""
echo "REQUIRED:"
echo "  [ ] App Icon (512x512)"
echo "      - Export from: app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
echo "      - Upscale to 512x512 and save to: play-store-assets/graphics/icon-512.png"
echo ""
echo "  [ ] Feature Graphic (1024x500)"
echo "      - Create using Canva/Figma/Photoshop"
echo "      - Should showcase app's main features"
echo "      - Save to: play-store-assets/graphics/feature-graphic.png"
echo ""
echo "  [ ] Screenshots (minimum 2)"
echo "      - Use Android Studio Device Manager to take screenshots"
echo "      - Recommended shots:"
echo "        1. Message list view (MainActivity)"
echo "        2. Thread detail with messages"
echo "        3. New message composition"
echo "        4. OTP notification"
echo "      - Save to: play-store-assets/screenshots/"
echo ""
echo "OPTIONAL:"
echo "  [ ] Promo video (30s - 2min)"
echo "      - Upload to YouTube (unlisted)"
echo "      - Record screen while using the app"
echo ""
echo "  [ ] Privacy Policy"
echo "      - Create from template in PLAY_STORE_GUIDE.md"
echo "      - Host on GitHub Pages or personal website"
echo "      - Save URL for Play Console"
echo ""

# Check if app icon exists
if [ -f "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" ]; then
    echo "✓ Found app icon source file"
    echo "  To upscale: Use online tool like https://imageresizer.com/"
else
    echo "⚠ App icon not found in expected location"
fi

echo ""
echo "Next steps:"
echo "1. Complete the checklist above"
echo "2. Review PLAY_STORE_GUIDE.md for detailed instructions"
echo "3. Build release bundle: ./gradlew bundleRelease"
echo "4. Upload app-release.aab to Play Console"
echo ""
