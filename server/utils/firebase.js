const { initializeApp, cert, getApps } = require('firebase-admin/app');
const { getMessaging } = require('firebase-admin/messaging');
const { getFirestore } = require('firebase-admin/firestore');
const { getAuth } = require('firebase-admin/auth');
const fs = require('fs');
const path = require('path');
const { getStorage } = require('firebase-admin/storage');

let isInitialized = false;
let messaging = null;
let db = null;
let auth = null;
let bucket = null;

function loadServiceAccount() {
  const rawServiceAccount = process.env.FIREBASE_SERVICE_ACCOUNT || process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (rawServiceAccount) {
    try {
      let sa = typeof rawServiceAccount === 'string' ? JSON.parse(rawServiceAccount) : rawServiceAccount;
      if (typeof sa === 'string') sa = JSON.parse(sa);
      console.log(`✿ Firebase credentials loaded from environment variable (Key ID: ${sa.private_key_id ? sa.private_key_id.substring(0, 10) + '...' : 'none'})`);
      return sa;
    } catch (e) {
      console.error('✿ Failed to parse FIREBASE_SERVICE_ACCOUNT environment variable:', e.message);
    }
  }

  // Check for Render secret file mount
  if (fs.existsSync('/etc/secrets/firebase-service-account.json')) {
    try {
      const sa = JSON.parse(fs.readFileSync('/etc/secrets/firebase-service-account.json', 'utf8'));
      console.log(`✿ Firebase credentials loaded from /etc/secrets/firebase-service-account.json (Key ID: ${sa.private_key_id ? sa.private_key_id.substring(0, 10) + '...' : 'none'})`);
      return sa;
    } catch (e) {
      console.error('✿ Failed to parse /etc/secrets/firebase-service-account.json:', e.message);
    }
  }

  // Local file fallback
  const localPath = path.join(__dirname, '../firebase-service-account.json');
  if (fs.existsSync(localPath)) {
    try {
      const sa = JSON.parse(fs.readFileSync(localPath, 'utf8'));
      console.log(`✿ Firebase credentials loaded from local file (Key ID: ${sa.private_key_id ? sa.private_key_id.substring(0, 10) + '...' : 'none'})`);
      return sa;
    } catch (e) {
      console.error('✿ Failed to parse local firebase-service-account.json:', e.message);
    }
  }

  return null;
}

try {
  const serviceAccount = loadServiceAccount();
  if (serviceAccount) {
    if (serviceAccount.private_key) {
      serviceAccount.private_key = serviceAccount.private_key.replace(/\\n/g, '\n');
    }

    const app = getApps().length > 0 ? getApps()[0] : initializeApp({
      credential: cert(serviceAccount),
      storageBucket: process.env.FIREBASE_STORAGE_BUCKET || 'our-bloom.firebasestorage.app'
    });
    
    messaging = getMessaging(app);
    db = getFirestore(app);
    auth = getAuth(app);
    bucket = getStorage(app).bucket(process.env.FIREBASE_STORAGE_BUCKET || 'our-bloom.firebasestorage.app');
    isInitialized = true;
    console.log(`✿ Firebase Admin initialized successfully (Project: ${serviceAccount.project_id}, Key ID: ${serviceAccount.private_key_id ? serviceAccount.private_key_id.substring(0, 10) + '...' : 'none'})`);
  } else {
    console.warn('✿ Firebase credentials missing. Push notifications and storage disabled.');
  }
} catch (error) {
  console.error('✿ Firebase Admin initialization failed:', error.message);
  console.log('Push notifications will be disabled.');
}

const sendPushNotification = async (fcmToken, title, body, data = {}) => {
  if (!isInitialized || !fcmToken || !messaging) return false;
  
  try {
    const isHeartbeat = data.type === 'heartbeat';
    const isChat = data.type === 'chat';
    const isVideoCall = data.type === 'video_call';
    const channelId = isHeartbeat 
      ? 'ourbloom_heartbeat_channel' 
      : (isChat ? 'ourbloom_chat_heads_up_v3' : (isVideoCall ? 'ourbloom_call_channel' : 'ourbloom_fcm_channel'));

    // Pure DATA-ONLY FCM payload (WhatsApp/Signal pattern):
    // Prevents Google Play Services from showing duplicate tray notifications in background,
    // and guarantees MyFirebaseMessagingService.onMessageReceived() executes on Android
    // even when the screen is locked/backgrounded to instantly commit delivery receipts.
    const message = {
      token: fcmToken,
      data: {
        title: String(title),
        body: String(body),
        channelId: channelId,
        ...Object.fromEntries(Object.entries(data).map(([k, v]) => [k, String(v)]))
      },
      android: {
        priority: 'high'
      }
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
  admin: { firestore: () => db, messaging: () => messaging, auth: () => auth },
  getAuth: () => auth,
  getFirestore: () => db,
  getMessaging: () => messaging,
  getBucket: () => bucket,
  sendPushNotification,
  isInitialized
};
