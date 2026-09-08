const { initializeApp, cert, getApps } = require('firebase-admin/app');
const { getMessaging } = require('firebase-admin/messaging');
const { getFirestore } = require('firebase-admin/firestore');
const { getAuth } = require('firebase-admin/auth');
const path = require('path');

let isInitialized = false;
let messaging = null;
let db = null;
let auth = null;

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
  auth = getAuth(app);
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
    const isChat = data.type === 'chat';
    const isVideoCall = data.type === 'video_call';
    const channelId = isHeartbeat 
      ? 'ourbloom_heartbeat_channel' 
      : (isChat ? 'ourbloom_chat_heads_up_v3' : (isVideoCall ? 'ourbloom_call_channel' : 'ourbloom_fcm_channel'));

    const message = {
      data: {
        title: String(title),
        body: String(body),
        ...Object.fromEntries(Object.entries(data).map(([k, v]) => [k, String(v)]))
      },
      android: {
        priority: 'high'
      },
      token: fcmToken
    };

    if (!isVideoCall) {
      message.notification = {
        title: String(title),
        body: String(body)
      };
      message.android.notification = {
        channelId: channelId,
        priority: 'max',
        visibility: 'public',
        defaultSound: true,
        defaultVibrateTimings: true
      };
    }
    
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
  sendPushNotification,
  isInitialized
};
