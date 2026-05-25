import { del, get, post } from '../utils/request';

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '');

export const createConversation = () => post('/chat/conversations');

export const getConversationList = () => get('/chat/conversations');

export const deleteConversation = (id) => del(`/chat/conversations/${id}`);

export const getMessageList = (id) => get(`/chat/conversations/${id}/messages`);

export const buildAskUrl = (id) => `${API_BASE_URL}/chat/conversations/${id}/ask`;
