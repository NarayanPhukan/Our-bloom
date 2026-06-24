import { useState, useEffect } from 'react';

export default function Toast({ message, type = 'success', onClose }) {
  const [exiting, setExiting] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => {
      setExiting(true);
      setTimeout(onClose, 400);
    }, 3000);
    return () => clearTimeout(timer);
  }, [onClose]);

  const bgColor =
    type === 'success' ? 'bg-secondary-container' : 'bg-error-container';
  const textColor =
    type === 'success' ? 'text-on-secondary-container' : 'text-on-error-container';
  const icon = type === 'success' ? 'check_circle' : 'error';

  return (
    <div
      className={`fixed bottom-6 left-1/2 transform -translate-x-1/2 z-[200] ${exiting ? 'toast-exit' : 'toast-enter'}`}
    >
      <div
        className={`${bgColor} ${textColor} px-6 py-3 rounded-full font-body-md text-body-md flex items-center gap-2 petal-shadow`}
      >
        <span className="material-symbols-outlined text-[20px]">{icon}</span>
        {message}
      </div>
    </div>
  );
}
