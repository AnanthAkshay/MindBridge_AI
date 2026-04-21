import { useEffect, useState } from "react";
import { fetchHealthStatus, type HealthStatus } from "../services/health";

type HealthState =
  | { state: "loading"; data: null; error: null }
  | { state: "ready"; data: HealthStatus; error: null }
  | { state: "error"; data: null; error: string };

export function useHealthStatus() {
  const [healthState, setHealthState] = useState<HealthState>({
    state: "loading",
    data: null,
    error: null
  });

  useEffect(() => {
    const controller = new AbortController();

    async function loadHealthStatus() {
      try {
        const data = await fetchHealthStatus(controller.signal);
        setHealthState({ state: "ready", data, error: null });
      } catch (error) {
        if (controller.signal.aborted) {
          return;
        }

        setHealthState({
          state: "error",
          data: null,
          error: error instanceof Error ? error.message : "Unknown API error"
        });
      }
    }

    loadHealthStatus();
    const intervalId = window.setInterval(loadHealthStatus, 30000);

    return () => {
      controller.abort();
      window.clearInterval(intervalId);
    };
  }, []);

  return healthState;
}
