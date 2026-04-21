import { useState, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { Play, CheckCircle, HeartPulse, Brain, AlertTriangle } from "lucide-react";
import type { InterventionCard } from "../services/recommendations";
import { markInterventionComplete } from "../services/recommendations";

interface Props {
  card: InterventionCard;
  onComplete?: () => void;
}

export function InterventionWidget({ card, onComplete }: Props) {
  const [expanded, setExpanded] = useState(false);
  const [completed, setCompleted] = useState(false);
  
  // Breathing state
  const [breathePhase, setBreathePhase] = useState<"inhale" | "hold1" | "exhale" | "hold2">("inhale");
  const [breathCount, setBreathCount] = useState(0);

  // CBT state
  const [cbtStep, setCbtStep] = useState(0);
  const [cbtInputs, setCbtInputs] = useState<string[]>(Array(card.steps.length).fill(""));

  // Styling based on type
  const styleMap = {
    breathing: { icon: HeartPulse, bg: "bg-blue-500/10", border: "border-blue-500/20", color: "text-blue-400" },
    cbt: { icon: Brain, bg: "bg-amber-500/10", border: "border-amber-500/20", color: "text-amber-400" },
    crisis: { icon: AlertTriangle, bg: "bg-red-500/10", border: "border-red-500/20", color: "text-red-400" },
  };
  const theme = styleMap[card.type] || styleMap.cbt;
  const Icon = theme.icon;

  const handleComplete = async () => {
    setCompleted(true);
    await markInterventionComplete(card.id);
    if (onComplete) onComplete();
  };

  // Breathing orchestration
  useEffect(() => {
    if (!expanded || card.type !== "breathing" || completed) return;
    
    // Simple 4-4-4-4 box breathing simulation for UI brevity
    const timings = { inhale: 4000, hold1: 4000, exhale: 4000, hold2: 4000 };
    
    let timer: ReturnType<typeof setTimeout>;
    if (breathePhase === "inhale") timer = setTimeout(() => setBreathePhase("hold1"), timings.inhale);
    if (breathePhase === "hold1") timer = setTimeout(() => setBreathePhase("exhale"), timings.hold1);
    if (breathePhase === "exhale") timer = setTimeout(() => setBreathePhase("hold2"), timings.exhale);
    if (breathePhase === "hold2") {
      timer = setTimeout(() => {
        setBreathePhase("inhale");
        setBreathCount(c => c + 1);
      }, timings.hold2);
    }
    
    // Allow users to pause or stop, removed the forced 3-count automatic stop so they can genuinely use it.
    return () => clearTimeout(timer);
  }, [expanded, breathePhase, card.type, completed]);

  return (
    <motion.div 
      layout
      initial={{ opacity: 0, scale: 0.95, y: 10 }}
      animate={{ opacity: 1, scale: 1, y: 0 }}
      className={`relative w-full overflow-hidden rounded-2xl border backdrop-blur-md transition-all ${theme.bg} ${theme.border}`}
    >
      {/* Header compact view */}
      <div 
        className="flex items-center justify-between px-4 py-3 cursor-pointer"
        onClick={() => !completed && setExpanded(!expanded)}
      >
        <div className="flex items-center gap-3">
          <div className={`grid h-10 w-10 shrink-0 place-items-center rounded-full bg-background/50 border ${theme.border} ${theme.color}`}>
            <Icon className="h-5 w-5" />
          </div>
          <div>
            <h4 className="text-sm font-semibold text-foreground flex items-center gap-2">
              {card.title}
              {completed && <CheckCircle className="h-3.5 w-3.5 text-green-500" />}
            </h4>
            <p className="text-xs text-muted/80">{card.duration} • {card.type.toUpperCase()}</p>
          </div>
        </div>
        {!completed && !expanded && (
          <button className={`flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-bold bg-background/50 border ${theme.border} ${theme.color} hover:bg-background/80 transition-colors`}>
            <Play className="h-3 w-3" /> Start
          </button>
        )}
      </div>

      {/* Expanded Interactive Body */}
      <AnimatePresence>
        {expanded && !completed && (
          <motion.div 
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: "auto", opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            className="border-t border-white/5"
          >
            <div className="p-4 pt-3 flex flex-col items-center">
              <p className="text-sm text-center text-muted mb-6">{card.description}</p>
              
              {/* Breathing UI */}
              {card.type === "breathing" && (
                <div className="flex flex-col items-center mb-4">
                  <div className="relative flex h-40 w-40 items-center justify-center mb-6">
                    <motion.div 
                      key={breathePhase}
                      animate={{ 
                        scale: breathePhase === "inhale" || breathePhase === "hold1" ? 1.6 : 1,
                        opacity: breathePhase === "hold1" || breathePhase === "hold2" ? 0.9 : 0.4,
                      }}
                      transition={{ duration: 4, ease: "linear" }}
                      className={`absolute inset-0 rounded-full ${theme.bg}`}
                    />
                    <motion.div className={`absolute inset-0 rounded-full border-2 border-dashed ${theme.border} opacity-50`} />
                    <span className={`z-10 font-black uppercase tracking-[0.2em] text-sm ${theme.color}`}>
                      {breathePhase.replace(/[0-9]/g, '')}
                    </span>
                  </div>
                  <button onClick={handleComplete} className={`rounded-xl px-6 py-2 text-sm font-bold ${theme.bg} ${theme.color}`}>Mark Complete</button>
                </div>
              )}

              {/* CBT UI */}
              {card.type === "cbt" && card.steps.length > 0 && (
                <div className="w-full space-y-3 mb-4">
                  <p className="text-xs font-medium text-muted uppercase tracking-wider mb-2">Step {cbtStep + 1} of {card.steps.length}</p>
                  <label className="text-sm font-semibold text-foreground mb-1 block">{card.steps[cbtStep]}</label>
                  <textarea 
                    autoFocus
                    placeholder="Reflect here..."
                    value={cbtInputs[cbtStep] || ""}
                    onChange={(e) => {
                      const newInputs = [...cbtInputs];
                      newInputs[cbtStep] = e.target.value;
                      setCbtInputs(newInputs);
                    }}
                    className="w-full rounded-xl bg-background/50 border border-white/10 px-4 py-3 text-sm text-foreground focus:outline-none focus:ring-1 focus:ring-amber-500/50 resize-none h-24 shadow-inner"
                  />
                  <div className="flex justify-between mt-4">
                    <button 
                      onClick={() => setCbtStep(s => Math.max(0, s - 1))}
                      disabled={cbtStep === 0}
                      className="rounded-lg bg-white/5 px-4 py-1.5 text-xs font-bold text-muted disabled:opacity-30 transition-colors"
                    >
                      Back
                    </button>
                    <button 
                      onClick={() => cbtStep < card.steps.length - 1 ? setCbtStep(s => s + 1) : handleComplete()}
                      className="rounded-lg bg-amber-500/20 px-6 py-1.5 text-xs font-bold text-amber-500 hover:bg-amber-500/30 transition-colors"
                    >
                      {cbtStep < card.steps.length - 1 ? "Next Step" : "Complete"}
                    </button>
                  </div>
                </div>
              )}

              {/* Crisis UI */}
              {card.type === "crisis" && (
                <div className="w-full space-y-3 mb-2 flex flex-col items-center">
                   <button onClick={handleComplete} className="w-full rounded-xl bg-red-500 text-white px-4 py-3 text-sm font-bold shadow-lg shadow-red-500/20">Call 988 Lifeline</button>
                   <button onClick={handleComplete} className="w-full rounded-xl bg-background/50 border border-red-500/30 text-red-400 px-4 py-3 text-sm font-bold">Text HOME to 741741</button>
                </div>
              )}

            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  );
}
