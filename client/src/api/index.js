import axios from 'axios';

const API = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:5000/api',
});

// Auth interceptor — attach JWT token to all requests
API.interceptors.request.use((config) => {
  const token = localStorage.getItem('bloom_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// ===== Auth =====
export const loginUser = (email, password) => API.post('/auth/login', { email, password });
export const registerUser = (email, password, name) => API.post('/auth/register', { email, password, name });
export const getMe = (token) => API.get('/auth/me', { headers: { Authorization: `Bearer ${token}` } });
export const updateNickname = (nicknameForPartner, token) =>
  API.put('/auth/me/nickname', { nicknameForPartner }, { headers: { Authorization: `Bearer ${token}` } });

// ===== Couples =====
export const createCouple = (data, token) =>
  API.post('/couples', data, { headers: { Authorization: `Bearer ${token}` } });
export const joinCouple = (inviteCode, token) =>
  API.post('/couples/join', { inviteCode }, { headers: { Authorization: `Bearer ${token}` } });
export const getCouple = (coupleId, token) =>
  // We need to find the couple by ID first, then by slug
  // The couples routes use slug, but for initial load we use the coupleId
  // So we add a helper endpoint, or we get it from /auth/me which gives coupleId
  // For now, we'll fetch all couples the user belongs to via a dedicated call
  API.get(`/couples/by-id/${coupleId}`, { headers: { Authorization: `Bearer ${token}` } });
export const getCoupleBySlug = (slug) => API.get(`/couples/${slug}`);
export const updateCouple = (slug, data) => API.put(`/couples/${slug}`, data);

// ===== Milestones (scoped by couple slug) =====
export const getMilestones = (slug) => API.get(`/couples/${slug}/milestones`);
export const getMilestone = (slug, id) => API.get(`/couples/${slug}/milestones/${id}`);
export const createMilestone = (slug, data) => API.post(`/couples/${slug}/milestones`, data);
export const updateMilestone = (slug, id, data) => API.put(`/couples/${slug}/milestones/${id}`, data);
export const deleteMilestone = (slug, id) => API.delete(`/couples/${slug}/milestones/${id}`);

// ===== Love Notes (scoped by couple slug) =====
export const getLoveNotes = (slug) => API.get(`/couples/${slug}/love-notes`);
export const createLoveNote = (slug, data) => API.post(`/couples/${slug}/love-notes`, data);
export const deleteLoveNote = (slug, id) => API.delete(`/couples/${slug}/love-notes/${id}`);

// ===== Memories (scoped by couple slug) =====
export const getMemories = (slug) => API.get(`/couples/${slug}/memories`);
export const createMemory = (slug, formData) => API.post(`/couples/${slug}/memories`, formData, {
  headers: { 'Content-Type': 'multipart/form-data' },
});
export const deleteMemory = (slug, id) => API.delete(`/couples/${slug}/memories/${id}`);

// ===== Dream Locations (scoped by couple slug) =====
export const getDreamLocations = (slug) => API.get(`/couples/${slug}/dream-locations`);
export const createDreamLocation = (slug, data) => API.post(`/couples/${slug}/dream-locations`, data, {
  headers: { 'Content-Type': 'multipart/form-data' }
});
export const updateDreamLocation = (slug, id, data) => API.put(`/couples/${slug}/dream-locations/${id}`, data, {
  headers: { 'Content-Type': 'multipart/form-data' }
});
export const deleteDreamLocation = (slug, id) => API.delete(`/couples/${slug}/dream-locations/${id}`);

// ===== Settings (scoped by couple slug) =====
export const getSpotifySettings = (slug) => API.get(`/couples/${slug}/settings/spotify`);
export const updateSpotifySettings = (slug, data) => API.put(`/couples/${slug}/settings/spotify`, data);
export const updateHeroImage = (slug, formData) => API.post(`/couples/${slug}/hero-image`, formData, {
  headers: { 'Content-Type': 'multipart/form-data' }
});

// ===== Daily Note =====
export const getDailyLoveNote = (slug) => API.get(`/couples/${slug}/love-notes/daily`);

export default API;
