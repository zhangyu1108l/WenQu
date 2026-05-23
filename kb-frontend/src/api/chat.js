/**
 * 对话接口：管理会话、消息历史和 SSE 问答地址拼接。
 */
import { del, get, post } from '../utils/request';

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '');

// POST /api/chat/conversations：新建会话。
export const createConversation = () => post('/chat/conversations');

// GET /api/chat/conversations：获取当前用户会话列表。
export const getConversationList = () => get('/chat/conversations');

// DELETE /api/chat/conversations/{id}：删除指定会话。
export const deleteConversation = (id) => del(`/chat/conversations/${id}`);

// GET /api/chat/conversations/{id}/messages：获取完整消息历史。
export const getMessageList = (id) => get(`/chat/conversations/${id}/messages`);

// POST /api/chat/conversations/{id}/ask：SSE 接口不走 Axios，只返回 URL 供 SseClient 使用，question 需要 encodeURIComponent。
export const buildAskUrl = (id, question) =>
  `${API_BASE_URL}/chat/conversations/${id}/ask?question=${encodeURIComponent(question ?? '')}`;
