#!/bin/bash

echo "=== Clean SMS - Keystore Generator ==="
echo ""
echo "This will create a keystore for signing your release APK."
echo "You'll be asked to provide:"
echo "  - Keystore password (minimum 6 characters)"
echo "  - Key alias password (can be same as keystore password)"
echo "  - Your name, organization, city, state, country"
echo ""
echo "IMPORTANT: Save these passwords securely! You'll need them to update your app."
echo ""
read -p "Press Enter to continue..."

# Generate keystore
keytool -genkey -v -keystore ./keystore.jks \
  -alias cleansms \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000

if [ $? -eq 0 ]; then
    echo ""
    echo "✓ Keystore created successfully at: ./keystore.jks"
    echo ""
    echo "Now creating keystore.properties file..."
    echo ""
    
    # Prompt for passwords to save in properties file
    echo "Please enter the passwords you just set (they will be saved to keystore.properties):"
    read -sp "Keystore password: " STORE_PASSWORD
    echo ""
    read -sp "Key password: " KEY_PASSWORD
    echo ""
    
    # Create keystore.properties
    cat > keystore.properties << EOF
storePassword=$STORE_PASSWORD
keyPassword=$KEY_PASSWORD
keyAlias=cleansms
storeFile=keystore.jks
EOF
    
    echo ""
    echo "✓ keystore.properties created"
    echo ""
    echo "Next steps:"
    echo "1. Run: ./gradlew assembleRelease"
    echo "2. Find your APK in: app/build/outputs/apk/release/app-release.apk"
    echo ""
    echo "⚠️  IMPORTANT: Keep keystore.jks and keystore.properties safe!"
    echo "   Without them, you cannot update your app in the future."
else
    echo ""
    echo "✗ Keystore generation failed"
    exit 1
fi
