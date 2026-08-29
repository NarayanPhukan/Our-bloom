import { PushNotifications } from '@capacitor/push-notifications';
import { LocalNotifications } from '@capacitor/local-notifications';
import { Capacitor } from '@capacitor/core';
import api from '../api'; // Your axios instance

export const initializeNotifications = async (user, couple) => {
  if (!Capacitor.isNativePlatform()) {
    console.log('Not running on a native platform, skipping native notifications.');
    return;
  }

  // --- LOCAL NOTIFICATIONS (Daily & Anniversary) ---
  
  // Request permissions
  let localPermStatus = await LocalNotifications.checkPermissions();
  if (localPermStatus.display !== 'granted') {
    localPermStatus = await LocalNotifications.requestPermissions();
  }

  if (localPermStatus.display === 'granted') {
    // Clear any previously scheduled notifications to avoid duplicates
    await LocalNotifications.cancel({ notifications: [{ id: 1 }, { id: 2 }] });

    // 1. Schedule Daily Prompt at 7:00 PM
    await LocalNotifications.schedule({
      notifications: [
        {
          title: "Tend to your garden! 🌸",
          body: "What made you smile today? Drop a quick love note for your partner.",
          id: 1,
          schedule: {
            on: {
              hour: 19,
              minute: 0,
            },
            allowWhileIdle: true,
          }
        }
      ]
    });

    // 2. Schedule Anniversary Alert
    if (couple?.startDate) {
      const start = new Date(couple.startDate);
      
      await LocalNotifications.schedule({
        notifications: [
          {
            title: "Happy Anniversary! 💖",
            body: "Open Our Bloom to celebrate your special day!",
            id: 2,
            schedule: {
              on: {
                month: start.getMonth() + 1, // Capacitor months are 1-indexed for scheduling? Wait, no, standard JS date is 0-indexed. Capacitor schedule `on: { month }` expects 1-12.
                day: start.getDate(),
                hour: 9, // 9 AM
                minute: 0,
              },
              allowWhileIdle: true,
            }
          }
        ]
      });
    }
  }

  // --- PUSH NOTIFICATIONS (Partner Updates) ---
  
  let pushPermStatus = await PushNotifications.checkPermissions();
  if (pushPermStatus.receive === 'prompt') {
    pushPermStatus = await PushNotifications.requestPermissions();
  }

  if (pushPermStatus.receive !== 'granted') {
    console.log('User denied push notification permissions');
    return;
  }

  // Register with Apple / Google to receive push via APNS/FCM
  await PushNotifications.register();

  // On success, we should be able to receive notifications
  PushNotifications.addListener('registration', async (token) => {
    console.log('Push registration success, token: ' + token.value);
    // Send the token to the backend to save on the User model
    try {
      await api.put('/auth/me/fcm-token', { fcmToken: token.value });
    } catch (err) {
      console.error('Failed to save FCM token to backend', err);
    }
  });

  // Some issue with our setup and push will not work
  PushNotifications.addListener('registrationError', (error) => {
    console.error('Error on registration: ' + JSON.stringify(error));
  });
};
