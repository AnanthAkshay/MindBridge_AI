import { apiFetch } from "./api";

export interface MoodTrend {
  date: string;
  averageMood: number;
}

export interface EmotionDistribution {
  name: string;
  value: number;
}

export interface SessionTimeline {
  sessionId: number;
  startedAt: string;
  endedAt: string | null;
  moodScore: number;
  riskScore: number;
}

export const analyticsApi = {
  getMoodTrend: (userId: number) =>
    apiFetch<MoodTrend[]>(`/api/analytics/${userId}/mood-trend`),

  getEmotionDistribution: (userId: number) =>
    apiFetch<EmotionDistribution[]>(
      `/api/analytics/${userId}/emotion-distribution`
    ),

  getSessionTimeline: (userId: number) =>
    apiFetch<SessionTimeline[]>(`/api/analytics/${userId}/session-timeline`),
};
