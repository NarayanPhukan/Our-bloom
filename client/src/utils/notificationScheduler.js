import { PushNotifications } from '@capacitor/push-notifications';
import { LocalNotifications } from '@capacitor/local-notifications';
import { Capacitor } from '@capacitor/core';
import api from '../api';

export const initializeNotifications = async (user, couple) => {
  if (!Capacitor.isNativePlatform()) {
    return;
  }

  // Create Channels (Android 8.0+)
  try {
    await LocalNotifications.createChannel({
      id: 'daily-reminders',
      name: 'Daily Reminders',
      description: 'Daily prompt to write a love note',
      importance: 3,
      visibility: 1
    });
    
    await LocalNotifications.createChannel({
      id: 'anniversaries',
      name: 'Anniversaries',
      description: 'Special date reminders',
      importance: 4,
      visibility: 1
    });

    await PushNotifications.createChannel({
      id: 'partner-updates',
      name: 'Partner Updates',
      description: 'When your partner adds a memory or note',
      importance: 4,
      visibility: 1
    });
  } catch (e) {
    console.log('Error creating channels', e);
  }

  // --- LOCAL NOTIFICATIONS (Daily & Anniversary) ---
  let localPermStatus = await LocalNotifications.checkPermissions();
  if (localPermStatus.display !== 'granted') {
    localPermStatus = await LocalNotifications.requestPermissions();
  }

  if (localPermStatus.display === 'granted') {
    await LocalNotifications.cancel({ notifications: [{ id: 1 }, { id: 2 }] });

    await LocalNotifications.schedule({
      notifications: [
        {
          title: "Tend to your garden! 🌸",
          body: "What made you smile today? Drop a quick love note for your partner.",
          id: 1,
          channelId: 'daily-reminders',
          schedule: {
            on: { hour: 19, minute: 0 },
            allowWhileIdle: true,
          },
          actionTypeId: '',
          extra: { path: `/c/${couple?.slug || ''}` }
        }
      ]
    });

    if (couple?.startDate) {
      const start = new Date(couple.startDate);
      await LocalNotifications.schedule({
        notifications: [
          {
            title: "Happy Anniversary! 💖",
            body: "Open Our Bloom to celebrate your special day!",
            id: 2,
            channelId: 'anniversaries',
            schedule: {
              on: { month: start.getMonth() + 1, day: start.getDate(), hour: 9, minute: 0 },
              allowWhileIdle: true,
            },
            extra: { path: `/c/${couple?.slug || ''}` }
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

  if (pushPermStatus.receive === 'granted') {
    await PushNotifications.register();

    PushNotifications.addListener('registration', async (token) => {
      try {
        await api.put('/auth/me/fcm-token', { fcmToken: token.value });
      } catch (err) {
        console.error('Failed to save FCM token', err);
      }
    });

    PushNotifications.addListener('registrationError', (error) => {
      console.error('Error on push registration: ', error);
    });
  }

  // --- TAP HANDLERS FOR DEEP LINKING ---
  LocalNotifications.addListener('localNotificationActionPerformed', (notification) => {
    const path = notification.notification.extra?.path;
    if (path) {
      window.location.href = path;
    }
  });

  PushNotifications.addListener('pushNotificationActionPerformed', (notification) => {
    const path = notification.notification.data?.path;
    if (path) {
      window.location.href = path;
    }
  });
};
