import { apiFetch } from "./api";

export interface SessionHistorySummary {
  sessionId: number;
  title: string;
  startedAt: string;
  endedAt: string | null;
  dominantEmotion: string;
  emotionArc: number[];
}

export interface SessionMessage {
  senderType: "USER" | "AI" | "SYSTEM";
  content: string;
  createdAt: string;
  valence: number | null;
  arousal: number | null;
}

export interface SessionRecommendation {
  type: string;
  content: string;
  createdAt: string;
}

export interface SessionDetail {
  sessionId: number;
  title: string;
  dominantEmotion: string;
  summary: string;
  messages: SessionMessage[];
  recommendations: SessionRecommendation[];
}

export const sessionHistoryApi = {
  getHistory: (userId: number, keyword?: string) => {
    const query = keyword ? `?keyword=${encodeURIComponent(keyword)}` : "";
    return apiFetch<SessionHistorySummary[]>(
      `/api/sessions/${userId}/history${query}`
    );
  },

  getSessionDetail: (userId: number, sessionId: number) =>
    apiFetch<SessionDetail>(`/api/sessions/${userId}/history/${sessionId}`),
};
