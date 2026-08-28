import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { loginUser, registerUser, getMe, getCouple, updateNickname as updateNicknameApi } from '../api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [couple, setCouple] = useState(null);
  const [token, setToken] = useState(() => localStorage.getItem('bloom_token'));
  const [loading, setLoading] = useState(true);

  // Fetch current user on mount or token change
  useEffect(() => {
    const fetchUser = async () => {
      if (!token) {
        setLoading(false);
        return;
      }

      try {
        const { data: userData } = await getMe(token);
        setUser(userData);

        // If user has a couple, fetch couple profile
        if (userData.coupleId) {
          await fetchCouple(userData, token);
        }
      } catch (err) {
        console.error('Auth check failed:', err);
        // Token invalid — clear it
        localStorage.removeItem('bloom_token');
        setToken(null);
        setUser(null);
        setCouple(null);
      } finally {
        setLoading(false);
      }
    };

    fetchUser();
  }, [token]);

  const fetchCouple = async (userData, authToken) => {
    try {
      // We need to find the couple slug. Fetch from the couples endpoint
      // The user's coupleId is set, so we get the couple by looking it up
      const { data: coupleData } = await getCouple(userData.coupleId, authToken || token);
      setCouple(coupleData);
    } catch (err) {
      console.error('Failed to fetch couple:', err);
    }
  };

  const login = useCallback(async (email, password) => {
    const { data } = await loginUser(email, password);
    localStorage.setItem('bloom_token', data.token);
    setToken(data.token);
    setUser(data.user);
    return data;
  }, []);

  const register = useCallback(async (email, password, name) => {
    const { data } = await registerUser(email, password, name);
    localStorage.setItem('bloom_token', data.token);
    setToken(data.token);
    setUser(data.user);
    return data;
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem('bloom_token');
    setToken(null);
    setUser(null);
    setCouple(null);
  }, []);

  const updateNickname = useCallback(async (nickname) => {
    const { data } = await updateNicknameApi(nickname, token);
    setUser(data);
    return data;
  }, [token]);

  const refreshCouple = useCallback(async () => {
    if (user && user.coupleId) {
      await fetchCouple(user, token);
    }
  }, [user, token]);

  const value = {
    user,
    couple,
    token,
    loading,
    login,
    register,
    logout,
    updateNickname,
    refreshCouple,
    setCouple,
    setUser,
  };

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}

export default AuthContext;
