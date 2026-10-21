#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const { getFirestore, getMessaging, isInitialized } = require('../utils/firebase');

async function main() {
  const args = process.argv.slice(2);
  const fileArg = args.find(a => !a.startsWith('--'));

  let manifestPath = fileArg || path.join(__dirname, '../../admin-update.json');
  if (!fs.existsSync(manifestPath)) {
    console.error(`❌ Error: Cannot find manifest at ${manifestPath}`);
    process.exit(1);
  }

  const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
  const versionCode = parseInt(manifest.latestVersionCode || manifest.versionCode, 10);
  const versionName = String(manifest.latestVersionName || manifest.versionName);
  const downloadUrl = String(manifest.downloadUrl || manifest.apkUrl);
  const changelog = String(manifest.changelog || 'Performance improvements and bug fixes.');
  const forceUpdate = Boolean(manifest.forceUpdate);

  console.log(`🌸 Admin Release Pipeline for v${versionName} (code ${versionCode})...`);

  const db = getFirestore();
  const messaging = getMessaging();

  if (!isInitialized || !db || !messaging) {
    console.error('❌ Firebase Admin SDK not initialized');
    process.exit(1);
  }

  // 1. Update Firestore admin_config/version_info
  try {
    const docData = {
      latestVersionCode: versionCode,
      latestVersionName: versionName,
      downloadUrl,
      changelog,
      forceUpdate,
      lastUpdated: Date.now()
    };
    await db.collection('admin_config').doc('version_info').set(docData, { merge: true });
    console.log('✅ Updated Firestore admin_config/version_info successfully!');
  } catch (err) {
    console.warn('⚠️ Warning updating Firestore version_info:', err.message);
  }

  // 2. Fetch admin devices
  const adminDevicesSnap = await db.collection('admin_devices').get();
  const tokens = [];
  adminDevicesSnap.forEach(d => {
    const t = d.data()?.token || d.id;
    if (t && t.length > 10) tokens.push(t);
  });
  console.log(`✿ Found ${tokens.length} registered admin device tokens.`);

  const title = `OurBloom Admin Update Available 🚀`;
  const body = `Version ${versionName} is ready with dynamic dashboard data sync. Tap to update!`;

  // 3. Send to individual tokens
  for (const token of tokens) {
    try {
      await messaging.send({
        token,
        notification: { title, body },
        data: {
          type: 'admin_app_update',
          versionCode: String(versionCode),
          versionName: String(versionName),
          downloadUrl
        },
        android: {
          priority: 'high',
          notification: {
            channelId: 'bloom_admin_financial_alerts',
            color: '#1A365D',
            defaultSound: true,
            defaultVibrateTimings: true
          }
        }
      });
      console.log(`   ✓ Dispatched push to token: ${token.slice(0, 15)}...`);
    } catch (e) {
      console.warn(`   ✗ Failed push to token: ${e.message}`);
    }
  }

  // 4. Send to topic admin_financial_alerts
  try {
    await messaging.send({
      topic: 'admin_financial_alerts',
      notification: { title, body },
      data: {
        type: 'admin_app_update',
        versionCode: String(versionCode),
        versionName: String(versionName),
        downloadUrl
      },
      android: {
        priority: 'high',
        notification: {
          channelId: 'bloom_admin_financial_alerts',
          color: '#1A365D',
          defaultSound: true,
          defaultVibrateTimings: true
        }
      }
    });
    console.log(`✅ Dispatched topic push to admin_financial_alerts`);
  } catch (e) {
    console.warn(`⚠️ Topic push warning: ${e.message}`);
  }

  console.log('✨ Admin update broadcast complete!');
  process.exit(0);
}

main().catch(e => {
  console.error('Fatal error:', e);
  process.exit(1);
});
