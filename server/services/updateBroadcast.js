const { getFirestore, getMessaging, isInitialized } = require('../utils/firebase');

/**
 * Broadcasts an app update notification to all registered FCM devices.
 * 
 * @param {Object} updateData - Version manifest data (versionCode, versionName, title, changelog, apkUrl, forceUpdate)
 * @returns {Promise<Object>} Statistics of the broadcast
 */
async function broadcastAppUpdate(updateData) {
  const db = getFirestore();
  const messaging = getMessaging();

  if (!isInitialized || !db || !messaging) {
    throw new Error('Firebase Admin SDK is not initialized. Cannot broadcast update notification.');
  }

  const versionCode = parseInt(updateData.versionCode, 10) || 0;
  const versionName = String(updateData.versionName || '1.0');
  const title = String(updateData.title || 'New Bloom Update Available! 🌸');
  const changelog = String(updateData.changelog || '• Performance improvements and bug fixes');
  const apkUrl = String(updateData.apkUrl || 'https://raw.githubusercontent.com/NarayanPhukan/Our-bloom/main/client/public/OurBloom.apk');
  const forceUpdate = Boolean(updateData.forceUpdate);
  const bodyText = `Version ${versionName} is ready to install. Tap to update!`;

  console.log(`🌸 Broadcasting update notification for v${versionName} (code ${versionCode})...`);

  // 1. Update Firestore record for real-time sync across connected clients
  try {
    const updatePayload = {
      versionCode,
      versionName,
      title,
      changelog,
      apkUrl,
      forceUpdate,
      updatedAt: new Date().toISOString(),
      timestamp: Date.now()
    };
    await db.collection('app_updates').doc('latest').set(updatePayload, { merge: true });
    await db.collection('app_updates_history').add(updatePayload);
    console.log('✿ Recorded latest release manifest in Firestore');
  } catch (err) {
    console.warn('✿ Warning: Could not record update in Firestore:', err.message);
  }

  // 2. Fetch all user documents to collect valid FCM tokens
  const usersSnapshot = await db.collection('users').get();
  const tokenMap = new Map(); // token -> array of userIds

  usersSnapshot.forEach(doc => {
    const data = doc.data();
    const token = data?.fcmToken;
    if (token && typeof token === 'string' && token.trim().length > 10) {
      const trimmed = token.trim();
      if (!tokenMap.has(trimmed)) {
        tokenMap.set(trimmed, []);
      }
      tokenMap.get(trimmed).push(doc.id);
    }
  });

  const uniqueTokens = Array.from(tokenMap.keys());
  console.log(`✿ Found ${usersSnapshot.size} users, ${uniqueTokens.length} active device FCM token(s)`);

  if (uniqueTokens.length === 0) {
    console.log('✿ No FCM tokens registered. Skipping push broadcast.');
    return {
      success: true,
      targeted: 0,
      sent: 0,
      failed: 0,
      cleanedTokens: 0
    };
  }

  // 3. Build FCM messages with both `notification` and `data` blocks + high priority
  const messages = uniqueTokens.map(token => ({
    token,
    notification: {
      title,
      body: bodyText
    },
    data: {
      type: 'app_update',
      versionCode: String(versionCode),
      versionName: String(versionName),
      title: String(title),
      body: bodyText,
      changelog: String(changelog),
      apkUrl: String(apkUrl),
      forceUpdate: String(forceUpdate)
    },
    android: {
      priority: 'high',
      notification: {
        channelId: 'ourbloom_fcm_channel',
        color: '#FF4D6D',
        clickAction: 'com.ourbloom.app.ACTION_SHOW_UPDATE',
        defaultSound: true,
        defaultVibrateTimings: true
      }
    }
  }));

  // 4. Send via messaging.sendEach
  let sentCount = 0;
  let failedCount = 0;
  let cleanedTokens = 0;

  // Process in chunks of 500 (Firebase Admin maximum batch size)
  const chunkSize = 500;
  for (let i = 0; i < messages.length; i += chunkSize) {
    const batch = messages.slice(i, i + chunkSize);
    const batchTokens = uniqueTokens.slice(i, i + chunkSize);

    try {
      const response = await messaging.sendEach(batch);
      sentCount += response.successCount;
      failedCount += response.failureCount;

      // Handle failures and remove dead/unregistered tokens
      for (let j = 0; j < response.responses.length; j++) {
        const res = response.responses[j];
        if (!res.success) {
          const err = res.error;
          const token = batchTokens[j];
          const isUnregistered = 
            err?.code === 'messaging/registration-token-not-registered' ||
            err?.errorInfo?.code === 'messaging/registration-token-not-registered' ||
            err?.details?.some?.(d => d.errorCode === 'UNREGISTERED') ||
            err?.message?.includes('NotRegistered') ||
            err?.status === 404;

          if (isUnregistered) {
            const userIds = tokenMap.get(token) || [];
            for (const uid of userIds) {
              try {
                await db.collection('users').doc(uid).update({ fcmToken: null });
                cleanedTokens++;
                console.log(`✿ Cleaned stale FCM token for user ${uid}`);
              } catch (_) {}
            }
          } else {
            console.error(`✿ Failed to send update notification to token ${token.substring(0, 10)}...:`, err?.message || err);
          }
        }
      }
    } catch (batchErr) {
      console.error('✿ Error sending batch of FCM update messages:', batchErr.message);
      failedCount += batch.length;
    }
  }

  console.log(`✨ Broadcast complete: ${sentCount} sent, ${failedCount} failed, ${cleanedTokens} stale token(s) cleaned.`);

  return {
    success: true,
    version: versionName,
    versionCode,
    targeted: uniqueTokens.length,
    sent: sentCount,
    failed: failedCount,
    cleanedTokens
  };
}

module.exports = {
  broadcastAppUpdate
};
