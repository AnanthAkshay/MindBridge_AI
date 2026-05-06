import { apiFetch } from "./api";

export interface EscalationSummary {
  id: number;
  sessionId: number;
  userId: number | null;
  triggerRule: string;
  riskScore: number;
  createdAt: string;
}

export interface TranscriptMessage {
  senderType: string;
  content: string;
  createdAt: string;
}

export interface SessionTranscript {
  sessionId: number;
  userId: number | null;
  messages: TranscriptMessage[];
}

export const therapistApi = {
  getEscalations: () =>
    apiFetch<EscalationSummary[]>(`/api/therapist/escalations`),

  resolveEscalation: (escalationId: number) =>
    apiFetch(`/api/therapist/escalations/${escalationId}/resolve`, {
      method: "POST",
    }),

  getTranscript: (sessionId: number) =>
    apiFetch<SessionTranscript>(`/api/therapist/sessions/${sessionId}/transcript`),
};
