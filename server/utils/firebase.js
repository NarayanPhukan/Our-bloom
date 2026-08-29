const admin = require('firebase-admin');
const path = require('path');

let isInitialized = false;

try {
  let serviceAccount;
  if (process.env.FIREBASE_SERVICE_ACCOUNT) {
    serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
  } else {
    serviceAccount = require(path.join(__dirname, '../firebase-service-account.json'));
  }
  
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount)
  });
  
  isInitialized = true;
  console.log('Firebase Admin initialized successfully');
} catch (error) {
  console.error('Firebase Admin initialization failed:', error.message);
  console.log('Push notifications will be disabled.');
}

const sendPushNotification = async (fcmToken, title, body, data = {}) => {
  if (!isInitialized || !fcmToken) return false;
  
  try {
    const message = {
      notification: {
        title,
        body
      },
      data,
      token: fcmToken
    };
    
    await admin.messaging().send(message);
    return true;
  } catch (error) {
    console.error('Error sending push notification:', error);
    return false;
  }
};

module.exports = {
  admin,
  sendPushNotification,
  isInitialized
};
