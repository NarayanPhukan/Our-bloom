#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const { broadcastAppUpdate } = require('../services/updateBroadcast');

async function main() {
  const args = process.argv.slice(2);
  const isDryRun = args.includes('--dry-run');
  const fileArg = args.find(a => !a.startsWith('--'));

  let manifestPath = fileArg;
  if (!manifestPath) {
    const candidates = [
      path.join(__dirname, '../../app-update.json'),
      path.join(__dirname, '../public/updates/app-update.json'),
      path.join(process.cwd(), 'app-update.json')
    ];
    manifestPath = candidates.find(p => fs.existsSync(p));
  }

  if (!manifestPath || !fs.existsSync(manifestPath)) {
    console.error('❌ Error: Could not find app-update.json manifest file.');
    console.error('Usage: node broadcast_update.js [path/to/app-update.json] [--dry-run]');
    process.exit(1);
  }

  console.log(`🌸 Reading version manifest from: ${manifestPath}`);
  const rawData = fs.readFileSync(manifestPath, 'utf8');
  const manifest = JSON.parse(rawData);

  console.log('Manifest contents:');
  console.log(`  • Version:     v${manifest.versionName} (code ${manifest.versionCode})`);
  console.log(`  • Title:       ${manifest.title}`);
  console.log(`  • APK URL:     ${manifest.apkUrl}`);
  console.log(`  • Force:       ${!!manifest.forceUpdate}`);

  if (isDryRun) {
    console.log('🔍 DRY RUN mode enabled. Skipping actual push dispatch.');
    process.exit(0);
  }

  try {
    const result = await broadcastAppUpdate(manifest);
    console.log('✅ Update broadcast completed successfully!');
    console.log(`   Targeted devices: ${result.targeted}`);
    console.log(`   Delivered:        ${result.sent}`);
    console.log(`   Failed:           ${result.failed}`);
    if (result.cleanedTokens > 0) {
      console.log(`   Cleaned Tokens:   ${result.cleanedTokens}`);
    }
    process.exit(0);
  } catch (error) {
    console.error('❌ Error during update broadcast:', error.message);
    process.exit(1);
  }
}

main();
