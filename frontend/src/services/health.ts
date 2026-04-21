export type HealthStatus = {
  status: "UP" | "DOWN";
  service: string;
};

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

export async function fetchHealthStatus(signal?: AbortSignal) {
  const response = await fetch(`${API_BASE_URL}/api/health`, {
    method: "GET",
    headers: {
      Accept: "application/json"
    },
    signal
  });

  if (!response.ok) {
    throw new Error(`Health check failed with ${response.status}`);
  }

  return (await response.json()) as HealthStatus;
}
