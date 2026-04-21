import { CheckCircle2, Loader2, WifiOff } from "lucide-react";
import { useHealthStatus } from "../hooks/useHealthStatus";

export function StatusPill() {
  const health = useHealthStatus();

  if (health.state === "loading") {
    return (
      <span className="inline-flex items-center gap-2 rounded-full border border-border/70 bg-surface/70 px-3 py-2 text-sm font-semibold text-muted backdrop-blur-lg">
        <Loader2 aria-hidden className="h-4 w-4 animate-spin" />
        Connecting
      </span>
    );
  }

  if (health.state === "error") {
    return (
      <span
        title={health.error}
        className="inline-flex items-center gap-2 rounded-full border border-danger/30 bg-danger/10 px-3 py-2 text-sm font-semibold text-danger backdrop-blur-lg"
      >
        <WifiOff aria-hidden className="h-4 w-4" />
        API offline
      </span>
    );
  }

  return (
    <span className="inline-flex items-center gap-2 rounded-full border border-success/30 bg-success/10 px-3 py-2 text-sm font-semibold text-success backdrop-blur-lg">
      <CheckCircle2 aria-hidden className="h-4 w-4" />
      {health.data.service} {health.data.status}
    </span>
  );
}
