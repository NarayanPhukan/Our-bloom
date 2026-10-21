#!/usr/bin/env bash
set -e

# OurBloom Admin Release & Auto-Update Script
# Usage: ./scripts/release_admin.sh ["Changelog message"]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-25-openjdk}"
ADMIN_GRADLE="$ROOT_DIR/App/admin/build.gradle"
RELEASE_APK_SRC="$ROOT_DIR/App/admin/build/outputs/apk/release/admin-release.apk"
UPDATES_APK_DEST="$ROOT_DIR/updates/admin/OurBloomAdmin.apk"
ROOT_APK_DEST="$ROOT_DIR/OurBloomAdmin.apk"
CLIENT_APK_DEST="$ROOT_DIR/client/public/OurBloomAdmin.apk"
ADMIN_UPDATE_JSON="$ROOT_DIR/admin-update.json"

CHANGELOG_INPUT="$1"

echo "🌸 --- OurBloom Admin App Build & Release Pipeline --- 🌸"

CURRENT_CODE=$(grep -E 'versionCode\s+[0-9]+' "$ADMIN_GRADLE" | grep -oE '[0-9]+' | head -1)
CURRENT_NAME=$(grep -E 'versionName\s+"[^"]+"' "$ADMIN_GRADLE" | grep -oE '"[^"]+"' | tr -d '"' | head -1)

echo "Admin Version: $CURRENT_NAME (code $CURRENT_CODE)"

if [ -z "$CHANGELOG_INPUT" ]; then
    CHANGELOG_TEXT="• Fixed live dynamic dashboard data streaming (wallets, transactions, revenue & payouts)\n• Resolved Firestore PERMISSION_DENIED on admin profile, transactions, wallets, and withdrawal requests\n• Added resilient auto-reconnecting snapshot listeners\n• Integrated authorized Firebase Admin Authentication on login and biometric unlock"
else
    CHANGELOG_TEXT="$CHANGELOG_INPUT"
fi

# 1. Update admin-update.json manifest
echo "Updating admin version manifests..."
cat <<EOF > "$ADMIN_UPDATE_JSON"
{
  "latestVersionCode": $CURRENT_CODE,
  "latestVersionName": "$CURRENT_NAME",
  "versionCode": $CURRENT_CODE,
  "versionName": "$CURRENT_NAME",
  "title": "OurBloom Admin Update Available 🚀",
  "changelog": "$CHANGELOG_TEXT",
  "downloadUrl": "https://raw.githubusercontent.com/NarayanPhukan/Our-bloom/main/updates/admin/OurBloomAdmin.apk",
  "apkUrl": "https://raw.githubusercontent.com/NarayanPhukan/Our-bloom/main/updates/admin/OurBloomAdmin.apk",
  "forceUpdate": true
}
EOF

mkdir -p "$ROOT_DIR/client/public"
cp "$ADMIN_UPDATE_JSON" "$ROOT_DIR/client/public/admin-update.json"
mkdir -p "$ROOT_DIR/server/public/updates"
cp "$ADMIN_UPDATE_JSON" "$ROOT_DIR/server/public/updates/admin-update.json"

# 2. Compile Release APK
echo "Building Admin Release APK with Gradle..."
cd "$ROOT_DIR/App"
./gradlew :admin:assembleRelease
cd "$ROOT_DIR"

if [ ! -f "$RELEASE_APK_SRC" ]; then
    echo "❌ Error: Admin Release APK not generated at $RELEASE_APK_SRC"
    exit 1
fi

# 3. Copy APK to distribution destinations
echo "Copying release APK to distribution locations..."
mkdir -p "$(dirname "$UPDATES_APK_DEST")"
cp "$RELEASE_APK_SRC" "$UPDATES_APK_DEST"
cp "$RELEASE_APK_SRC" "$ROOT_APK_DEST"
cp "$RELEASE_APK_SRC" "$CLIENT_APK_DEST"

# 4. Broadcast update to Firestore and FCM
echo "Broadcasting update to Firestore and admin devices..."
node "$ROOT_DIR/server/scripts/broadcast_admin_update.js" "$ADMIN_UPDATE_JSON" || echo "⚠️ Warning: Admin update broadcast skipped or failed"

echo ""
echo "✨ --- ADMIN RELEASE & AUTO-UPDATE PIPELINE COMPLETE! --- ✨"
echo "Version: $CURRENT_NAME (code $CURRENT_CODE)"
echo "APK Size: $(du -h "$ROOT_APK_DEST" | cut -f1)"
echo "Distribution targets updated:"
echo "  • $ROOT_APK_DEST"
echo "  • $UPDATES_APK_DEST"
echo "  • $CLIENT_APK_DEST"
echo "  • Firestore admin_config/version_info"
echo ""
echo "To publish the update to GitHub and Render, run:"
echo "  git add -A"
echo "  git commit -m \"release(admin): bump to v$CURRENT_NAME (code $CURRENT_CODE)\""
echo "  git push origin main"
