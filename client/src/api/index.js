import axios from 'axios';

const API = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:5000/api',
});

// Milestones
export const getMilestones = () => API.get('/milestones');
export const getMilestone = (id) => API.get(`/milestones/${id}`);
export const createMilestone = (data) => API.post('/milestones', data);
export const updateMilestone = (id, data) => API.put(`/milestones/${id}`, data);
export const deleteMilestone = (id) => API.delete(`/milestones/${id}`);

// Love Notes
export const getLoveNotes = () => API.get('/love-notes');
export const createLoveNote = (data) => API.post('/love-notes', data);
export const deleteLoveNote = (id) => API.delete(`/love-notes/${id}`);

// Memories
export const getMemories = () => API.get('/memories');
export const createMemory = (formData) => API.post('/memories', formData, {
  headers: {
    'Content-Type': 'multipart/form-data',
  },
});
export const deleteMemory = (id) => API.delete(`/memories/${id}`);

// Dream Locations
export const getDreamLocations = () => API.get('/dream-locations');
export const createDreamLocation = (data) => API.post('/dream-locations', data, {
  headers: { 'Content-Type': 'multipart/form-data' }
});
export const updateDreamLocation = (id, data) => API.put(`/dream-locations/${id}`, data, {
  headers: { 'Content-Type': 'multipart/form-data' }
});
export const deleteDreamLocation = (id) => API.delete(`/dream-locations/${id}`);

// Settings
export const getSpotifySettings = () => API.get('/settings/spotify');
export const updateSpotifySettings = (data) => API.put('/settings/spotify', data);

// Daily Note
export const getDailyLoveNote = () => API.get('/love-notes/daily');

export default API;
