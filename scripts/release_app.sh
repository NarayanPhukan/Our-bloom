#!/usr/bin/env bash
set -e

# OurBloom Release & Auto-Update Script
# Usage: ./scripts/release_app.sh "What's new in this release"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
APP_GRADLE="$ROOT_DIR/App/app/build.gradle"
UPDATE_JSON="$ROOT_DIR/app-update.json"
CLIENT_UPDATE_JSON="$ROOT_DIR/client/public/app-update.json"
SERVER_UPDATE_JSON="$ROOT_DIR/server/public/updates/app-update.json"
RELEASE_APK_SRC="$ROOT_DIR/App/app/build/outputs/apk/release/app-release.apk"
CLIENT_APK_DEST="$ROOT_DIR/client/public/OurBloom.apk"

CHANGELOG_INPUT="$1"

echo "🌸 --- OurBloom App Release & Update Pipeline --- 🌸"

# 1. Read current versionCode & versionName
CURRENT_CODE=$(grep -E 'versionCode\s+[0-9]+' "$APP_GRADLE" | grep -oE '[0-9]+' | head -1)
CURRENT_NAME=$(grep -E 'versionName\s+"[^"]+"' "$APP_GRADLE" | grep -oE '"[^"]+"' | tr -d '"' | head -1)

NEW_CODE=$((CURRENT_CODE + 1))

# Extract major and minor from versionName (e.g., 1.2 -> 1.3)
if [[ "$CURRENT_NAME" =~ ^([0-9]+)\.([0-9]+)$ ]]; then
    MAJOR="${BASH_REMATCH[1]}"
    MINOR="${BASH_REMATCH[2]}"
    NEW_MINOR=$((MINOR + 1))
    NEW_NAME="${MAJOR}.${NEW_MINOR}"
else
    NEW_NAME="${CURRENT_NAME}.1"
fi

echo "Current version: $CURRENT_NAME ($CURRENT_CODE)"
echo "New version:     $NEW_NAME ($NEW_CODE)"

# Default changelog if not provided
if [ -z "$CHANGELOG_INPUT" ]; then
    CHANGELOG_TEXT="• Performance improvements and bug fixes\n• Real-time updates and notifications"
else
    CHANGELOG_TEXT="$CHANGELOG_INPUT"
fi

# 2. Update App/app/build.gradle
echo "Updating $APP_GRADLE..."
sed -i -E "s/versionCode [0-9]+/versionCode $NEW_CODE/" "$APP_GRADLE"
sed -i -E "s/versionName \"[^\"]+\"/versionName \"$NEW_NAME\"/" "$APP_GRADLE"

# 3. Update app-update.json
echo "Updating version manifests..."
cat <<EOF > "$UPDATE_JSON"
{
  "versionCode": $NEW_CODE,
  "versionName": "$NEW_NAME",
  "title": "New Bloom Update Available! 🌸",
  "changelog": "$CHANGELOG_TEXT",
  "apkUrl": "https://raw.githubusercontent.com/NarayanPhukan/Our-bloom/main/client/public/OurBloom.apk",
  "forceUpdate": false
}
EOF

mkdir -p "$(dirname "$CLIENT_UPDATE_JSON")"
cp "$UPDATE_JSON" "$CLIENT_UPDATE_JSON"
mkdir -p "$(dirname "$SERVER_UPDATE_JSON")"
cp "$UPDATE_JSON" "$SERVER_UPDATE_JSON"

# 4. Compile Release APK
echo "Building Release APK with Gradle..."
cd "$ROOT_DIR/App"
./gradlew assembleRelease
cd "$ROOT_DIR"

if [ ! -f "$RELEASE_APK_SRC" ]; then
    echo "❌ Error: Release APK not generated at $RELEASE_APK_SRC"
    exit 1
fi

# 5. Copy APK to client/public/OurBloom.apk
echo "Copying release APK to $CLIENT_APK_DEST..."
cp "$RELEASE_APK_SRC" "$CLIENT_APK_DEST"

# 6. Rebuild web client
echo "Rebuilding web client..."
npm --prefix "$ROOT_DIR/client" run build

echo ""
echo "✨ --- RELEASE BUILD SUCCESSFUL! --- ✨"
echo "Version: $NEW_NAME (code $NEW_CODE)"
echo "APK Size: $(du -h "$CLIENT_APK_DEST" | cut -f1)"
echo ""
echo "To publish this update so all users automatically receive it on app launch, run:"
echo "  git add -A"
echo "  git commit -m \"release: bump to v$NEW_NAME (code $NEW_CODE)\""
echo "  git push origin main"
