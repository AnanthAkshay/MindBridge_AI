import { BrainCircuit } from "lucide-react";

export function LogoMark() {
  return (
    <div className="flex items-center gap-3">
      <div className="grid h-11 w-11 place-items-center rounded-brand bg-brand-gradient text-white shadow-glow">
        <BrainCircuit aria-hidden className="h-6 w-6" strokeWidth={2.2} />
      </div>
      <div className="min-w-0">
        <p className="truncate text-sm font-semibold uppercase text-muted">
          MindBridge
        </p>
        <p className="truncate text-lg font-bold text-foreground">AI</p>
      </div>
    </div>
  );
}
