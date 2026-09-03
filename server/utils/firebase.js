const { initializeApp, cert, getApps } = require('firebase-admin/app');
const { getMessaging } = require('firebase-admin/messaging');
const { getFirestore } = require('firebase-admin/firestore');
const path = require('path');

let isInitialized = false;
let messaging = null;
let db = null;

try {
  let serviceAccount;
  const rawServiceAccount = process.env.FIREBASE_SERVICE_ACCOUNT || process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (rawServiceAccount) {
    serviceAccount = JSON.parse(rawServiceAccount);
  } else {
    serviceAccount = require(path.join(__dirname, '../firebase-service-account.json'));
  }
  
  const app = getApps().length > 0 ? getApps()[0] : initializeApp({
    credential: cert(serviceAccount)
  });
  
  messaging = getMessaging(app);
  db = getFirestore(app);
  isInitialized = true;
  console.log('✿ Firebase Admin initialized successfully');
} catch (error) {
  console.error('✿ Firebase Admin initialization failed:', error.message);
  console.log('Push notifications will be disabled.');
}

const sendPushNotification = async (fcmToken, title, body, data = {}) => {
  if (!isInitialized || !fcmToken || !messaging) return false;
  
  try {
    const isHeartbeat = data.type === 'heartbeat';
    const message = {
      notification: {
        title,
        body
      },
      data: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, String(v)])),
      android: {
        priority: 'high',
        notification: {
          channelId: isHeartbeat ? 'ourbloom_heartbeat_channel' : 'ourbloom_fcm_channel',
          priority: 'max',
          sound: 'default',
          defaultVibrateTimings: !isHeartbeat,
          vibrateTimingsMillis: isHeartbeat ? [0, 120, 80, 240] : undefined
        }
      },
      token: fcmToken
    };
    
    const response = await messaging.send(message);
    console.log('✿ Push notification sent successfully:', response);
    return true;
  } catch (error) {
    const isUnregistered = 
      error?.code === 'messaging/registration-token-not-registered' ||
      error?.errorInfo?.code === 'messaging/registration-token-not-registered' ||
      error?.details?.some?.(d => d.errorCode === 'UNREGISTERED') ||
      error?.message?.includes('NotRegistered') ||
      error?.status === 404;

    if (isUnregistered) {
      console.warn('✿ FCM registration token is expired or unregistered.');
    } else {
      console.error('✿ Error sending push notification:', error?.message || error);
    }
    return false;
  }
};

module.exports = {
  admin: { firestore: () => db, messaging: () => messaging },
  sendPushNotification,
  isInitialized
};
