import axios from 'axios';

const API = axios.create({
  baseURL: 'http://localhost:5000/api',
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

export default API;
