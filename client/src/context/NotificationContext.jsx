import { createContext, useContext, useState, useEffect } from 'react';
import { io } from 'socket.io-client';
import { useAuth } from './AuthContext';
import Toast from '../components/Toast';

const NotificationContext = createContext(null);

export function NotificationProvider({ children }) {
  const { user, token, couple } = useAuth();
  const [notifications, setNotifications] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [currentToast, setCurrentToast] = useState(null);

  useEffect(() => {
    if (!user || !couple || !token) return;

    const socketUrl = import.meta.env.VITE_API_URL ? import.meta.env.VITE_API_URL.replace('/api', '') : 'http://localhost:5000';
    const socket = io(socketUrl, {
      auth: { token, coupleSlug: couple.slug }
    });

    socket.on('notification', (data) => {
      // Ignore our own actions
      if (data.userId === user._id) return;

      let partnerNickname = 'Your partner';
      if (couple.user1 && couple.user1._id !== user._id) {
        partnerNickname = couple.user1.nicknameForPartner || couple.user1.name;
      } else if (couple.user2 && couple.user2._id !== user._id) {
        partnerNickname = couple.user2.nicknameForPartner || couple.user2.name;
      }

      let message = '';
      if (data.type === 'memory_added') {
        message = `${partnerNickname} just added a new memory! 🌸`;
      } else if (data.type === 'note_added') {
        message = `${partnerNickname} just wrote you a love note! 💌`;
      } else {
        message = `${partnerNickname} made an update.`;
      }

      const newNotif = {
        id: Date.now(),
        message,
        type: data.type,
        time: new Date(),
        read: false
      };

      setNotifications((prev) => [newNotif, ...prev]);
      setUnreadCount((prev) => prev + 1);
      
      setCurrentToast(message);
    });

    return () => socket.disconnect();
  }, [user, couple, token]);

  const markAllAsRead = () => {
    setUnreadCount(0);
    setNotifications(prev => prev.map(n => ({ ...n, read: true })));
  };

  return (
    <NotificationContext.Provider value={{ notifications, unreadCount, markAllAsRead }}>
      {children}
      {currentToast && (
        <Toast 
          message={currentToast} 
          type="success" 
          onClose={() => setCurrentToast(null)} 
        />
      )}
    </NotificationContext.Provider>
  );
}

export function useNotifications() {
  return useContext(NotificationContext);
}
