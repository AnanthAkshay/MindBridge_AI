import { apiFetch } from "./api";
import type { ChatMessage } from "./websocket";

export type ChatSession = {
  id: number;
  title: string;
  status: string;
  createdAt: string;
};

/** Create a new chat session */
export async function createChatSession(title?: string): Promise<ChatSession> {
  return apiFetch<ChatSession>("/api/chat/sessions", {
    method: "POST",
    body: title ? { title } : {},
  });
}

/** List all chat sessions for current user */
export async function listChatSessions(): Promise<ChatSession[]> {
  return apiFetch<ChatSession[]>("/api/chat/sessions");
}

/** Get decrypted message history for a session */
export async function getChatHistory(sessionId: number): Promise<ChatMessage[]> {
  return apiFetch<ChatMessage[]>(`/api/chat/sessions/${sessionId}/messages`);
}

export type MemoryInsight = {
  recentSummary: string;
  topEmotions: string[];
  triggers: string[];
  trend: string;
};

export async function fetchMemoryInsight(): Promise<MemoryInsight> {
  return apiFetch<MemoryInsight>("/api/memory");
}

export async function processSessionMemory(sessionId: number): Promise<void> {
  await apiFetch<void>(`/api/memory/session/${sessionId}/end`, { method: "POST" });
}
