import { useEffect, useState } from "react";
import { motion } from "framer-motion";
import { AlertTriangle, CheckCircle, Clock, ShieldAlert } from "lucide-react";
import { GlassCard } from "../components/GlassCard";
import { therapistApi, EscalationSummary, SessionTranscript } from "../services/therapist";
import { websocketService } from "../services/websocket";
import { useAuth } from "../store/auth-store";

export function TherapistDashboardPage() {
  const { state } = useAuth();
  const user = state.status === "authenticated" ? state.user : null;
  const [escalations, setEscalations] = useState<EscalationSummary[]>([]);
  const [selectedTranscript, setSelectedTranscript] = useState<SessionTranscript | null>(null);
  const [selectedEscalationId, setSelectedEscalationId] = useState<number | null>(null);

  useEffect(() => {
    loadEscalations();

    // Subscribe to new escalations
    websocketService.subscribeToEscalations((escalationId: number) => {
      console.log("New escalation received:", escalationId);
      loadEscalations(); // Reload the queue
    });

    return () => {
      websocketService.unsubscribeFromEscalations();
    };
  }, []);

  const loadEscalations = async () => {
    try {
      const data = await therapistApi.getEscalations();
      setEscalations(data);
    } catch (err) {
      console.error("Failed to load escalations", err);
    }
  };

  const loadTranscript = async (sessionId: number, escalationId: number) => {
    try {
      const data = await therapistApi.getTranscript(sessionId);
      setSelectedTranscript(data);
      setSelectedEscalationId(escalationId);
    } catch (err) {
      console.error("Failed to load transcript", err);
    }
  };

  const handleResolve = async (escalationId: number) => {
    try {
      await therapistApi.resolveEscalation(escalationId);
      setEscalations(prev => prev.filter(e => e.id !== escalationId));
      if (selectedEscalationId === escalationId) {
        setSelectedTranscript(null);
        setSelectedEscalationId(null);
      }
    } catch (err) {
      console.error("Failed to resolve escalation", err);
    }
  };

  if (!user || !user.role?.toUpperCase().includes('THERAPIST')) {
    return (
      <div className="flex h-64 items-center justify-center text-center">
        <div>
          <ShieldAlert className="mx-auto h-12 w-12 text-destructive mb-4" />
          <h2 className="text-2xl font-bold text-foreground">Access Denied</h2>
          <p className="text-muted mt-2">You do not have therapist privileges to view this dashboard.</p>
        </div>
      </div>
    );
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5 }}
      className="grid gap-6 lg:grid-cols-2"
    >
      <div className="space-y-6">
        <div className="flex items-center gap-3 mb-2">
          <AlertTriangle className="text-warning h-6 w-6" />
          <h2 className="text-2xl font-bold text-foreground">Active Escalations</h2>
          <span className="bg-warning/20 text-warning px-2 py-1 rounded-full text-xs font-bold">
            {escalations.length} Queue
          </span>
        </div>
        
        <div className="space-y-4 max-h-[75vh] overflow-y-auto pr-2 custom-scrollbar">
          {escalations.length === 0 ? (
            <GlassCard className="p-8 text-center text-muted">
              <CheckCircle className="mx-auto h-12 w-12 text-success/50 mb-3" />
              <p>Queue is clear. No active escalations.</p>
            </GlassCard>
          ) : (
            escalations.map((esc) => (
              <GlassCard 
                key={esc.id} 
                className={`p-5 border-l-4 transition ${selectedEscalationId === esc.id ? 'border-l-primary bg-primary/5' : 'border-l-warning hover:bg-surface/60'}`}
              >
                <div className="flex justify-between items-start mb-3">
                  <div>
                    <h3 className="font-bold text-lg text-foreground">User #{esc.userId || 'Anonymous'}</h3>
                    <p className="text-sm text-muted flex items-center gap-1">
                      <Clock size={14} /> {new Date(esc.createdAt).toLocaleString()}
                    </p>
                  </div>
                  <div className="text-right">
                    <span className="bg-destructive/20 text-destructive px-2 py-1 rounded font-bold text-xs">
                      Risk: {esc.riskScore}/100
                    </span>
                  </div>
                </div>
                
                <div className="bg-surface/50 p-3 rounded border border-border/40 mb-4">
                  <p className="text-sm text-muted">Trigger Rule</p>
                  <p className="font-semibold text-foreground text-sm">{esc.triggerRule}</p>
                </div>

                <div className="flex gap-3">
                  <button 
                    onClick={() => loadTranscript(esc.sessionId, esc.id)}
                    className="flex-1 py-2 bg-surface/80 border border-border/50 rounded-lg text-sm font-semibold hover:bg-surface transition"
                  >
                    View Context
                  </button>
                  <button 
                    onClick={() => handleResolve(esc.id)}
                    className="flex-1 py-2 bg-success/20 text-success border border-success/30 rounded-lg text-sm font-semibold hover:bg-success/30 transition flex items-center justify-center gap-2"
                  >
                    <CheckCircle size={16} /> Resolve
                  </button>
                </div>
              </GlassCard>
            ))
          )}
        </div>
      </div>

      <GlassCard className="p-6 h-[80vh] flex flex-col">
        {selectedTranscript ? (
          <>
            <div className="border-b border-border/50 pb-4 mb-4">
              <h3 className="text-xl font-bold text-foreground">Session Transcript</h3>
              <p className="text-sm text-muted">Session #{selectedTranscript.sessionId}</p>
            </div>
            <div className="flex-1 overflow-y-auto pr-2 custom-scrollbar space-y-4">
              {selectedTranscript.messages.map((m, i) => (
                <div key={i} className={`flex ${m.senderType === 'USER' ? 'justify-start' : 'justify-end'}`}>
                  <div className={`max-w-[85%] p-3 rounded-xl ${m.senderType === 'USER' ? 'bg-surface/80 border border-border/50 text-foreground rounded-tl-none' : 'bg-primary/20 text-foreground rounded-tr-none'}`}>
                    <p className="text-sm font-semibold mb-1 opacity-70">{m.senderType}</p>
                    <p className="text-sm leading-relaxed">{m.content}</p>
                    <p className="text-xs text-muted mt-2 opacity-50">{new Date(m.createdAt).toLocaleTimeString()}</p>
                  </div>
                </div>
              ))}
            </div>
          </>
        ) : (
          <div className="flex-1 flex flex-col items-center justify-center text-muted">
            <MessageCircle className="h-12 w-12 opacity-20 mb-3" />
            <p>Select an escalation to view the session transcript.</p>
          </div>
        )}
      </GlassCard>
    </motion.div>
  );
}

// Temporary icon definition for the empty state
function MessageCircle(props: any) {
  return (
    <svg
      {...props}
      xmlns="http://www.w3.org/2000/svg"
      width="24"
      height="24"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="m3 21 1.9-5.7a8.5 8.5 0 1 1 3.8 3.8z" />
    </svg>
  );
}
