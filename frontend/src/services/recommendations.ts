import { apiFetch } from "./api";

export type InterventionCard = {
  id: string;
  type: "breathing" | "cbt" | "crisis";
  title: string;
  duration: string;
  description: string;
  steps: string[];
};

export async function suggestInterventions(
  emotion: string,
  riskScore: number,
  sessionId?: number
): Promise<InterventionCard[]> {
  return apiFetch<InterventionCard[]>("/api/recommendations/suggest", {
    method: "POST",
    body: { emotion, riskScore, sessionId },
  });
}

export async function markInterventionComplete(contentId: string): Promise<void> {
  return apiFetch<void>(`/api/recommendations/${contentId}/complete`, {
    method: "POST",
  });
}
