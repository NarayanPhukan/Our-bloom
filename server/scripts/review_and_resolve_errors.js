#!/usr/bin/env node

const { getFirestore } = require('../utils/firebase');

async function resolveErrors() {
  const db = getFirestore();
  if (!db) {
    console.error('Firestore not initialized');
    process.exit(1);
  }

  const snapshot = await db.collection('error_reports').get();
  console.log(`Found ${snapshot.size} error reports.`);

  const resolutions = {
    '1f193df9-c8de-4e8e-a46d-0fc06d176418': 'Fixed in v1.82: Removed stepSize="0.05" on slider_dim_level to support continuous values and wrapped slider value assignment in safe try-catch & rounding.',
    '98e4b245-85a8-4638-9b16-185d568f5c0a': 'Fixed in v1.82: Removed stepSize="0.05" on slider_dim_level to support continuous values and wrapped slider value assignment in safe try-catch & rounding.',
    'cc77b948-14ff-4626-a3d6-eee64faef387': 'Fixed in v1.82: Removed stepSize="0.05" on slider_dim_level to support continuous values and wrapped slider value assignment in safe try-catch & rounding.',
    'a561fca2-24e1-4a56-b440-052f0de11b16': 'Resolved: Removed duplicate @DocumentId from SavingsTransaction.kt to prevent Firestore deserialization collision.',
    'f959d613-4858-4b85-bf4c-e6aa18894754': 'Resolved: Removed duplicate @DocumentId from SavingsTransaction.kt to prevent Firestore deserialization collision.',
    '5e38d00d-f1c1-481f-9823-af4426dab99e': 'Resolved: Added model persistence and Proguard keep rules for Firestore generic List types.',
    '95c6e3b3-400a-4100-a2fa-97eb308c7617': 'Resolved: Added model persistence and Proguard keep rules for Firestore generic List types.',
    'f613a21b-eddf-4fb3-9506-7ebee8ae9d6e': 'Resolved: Added model persistence and Proguard keep rules for Firestore generic List types.',
    '026bf4a8-f687-4891-a493-45552f39df73': 'Resolved: User.fcmToken nullability handled and @Keep annotation added to User model.',
    'd17ebd37-60b6-480c-8bf7-dab852c50ed1': 'Tracked: Feature request for video call PiP/minimize and notification.'
  };

  for (const doc of snapshot.docs) {
    const data = doc.data();
    const resolution = resolutions[doc.id];
    console.log(`- Doc ${doc.id}: title="${data.title}", version="${data.appVersion}", status="${data.status}"`);

    if (resolution) {
      const isFeature = doc.id === 'd17ebd37-60b6-480c-8bf7-dab852c50ed1';
      const newStatus = isFeature ? 'IN_REVIEW' : 'RESOLVED';
      await doc.ref.update({
        status: newStatus,
        resolvedAt: Date.now(),
        resolutionNote: resolution
      });
      console.log(`  -> Updated to ${newStatus}: ${resolution}`);
    }
  }

  console.log('Error reports successfully updated.');
  process.exit(0);
}

resolveErrors().catch(err => {
  console.error('Error updating reports:', err);
  process.exit(1);
});
