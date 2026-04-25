import { useEffect, useState } from "react";
import { motion } from "framer-motion";
import { Search, Download, PlayCircle, Activity } from "lucide-react";
import { GlassCard } from "../components/GlassCard";
import { sessionHistoryApi, SessionHistorySummary, SessionDetail } from "../services/sessionHistory";
import { useAuth } from "../store/auth-store";
import { LineChart, Line, ResponsiveContainer, YAxis } from "recharts";

export function SessionHistoryPage() {
  const { state } = useAuth();
  const user = state.status === "authenticated" ? state.user : null;
  const [sessions, setSessions] = useState<SessionHistorySummary[]>([]);
  const [keyword, setKeyword] = useState("");
  const [selectedSession, setSelectedSession] = useState<SessionDetail | null>(null);

  useEffect(() => {
    if (user) {
      sessionHistoryApi.getHistory(user.id, keyword).then(setSessions);
    }
  }, [user, keyword]);

  const loadSessionDetail = async (sessionId: number) => {
    if (!user) return;
    const detail = await sessionHistoryApi.getSessionDetail(user.id, sessionId);
    setSelectedSession(detail);
  };

  const exportAsText = () => {
    if (!selectedSession) return;
    let content = `Session #${selectedSession.sessionId} - ${selectedSession.title}\n`;
    content += `Dominant Emotion: ${selectedSession.dominantEmotion}\n`;
    content += `Summary: ${selectedSession.summary}\n\n`;
    content += `--- Transcript ---\n`;
    selectedSession.messages.forEach(m => {
      content += `[${new Date(m.createdAt).toLocaleTimeString()}] ${m.senderType}: ${m.content}\n`;
    });
    content += `\n--- Recommendations ---\n`;
    selectedSession.recommendations.forEach(r => {
      content += `- [${r.type}] ${r.content}\n`;
    });

    const blob = new Blob([content], { type: "text/plain" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `session_${selectedSession.sessionId}_export.txt`;
    a.click();
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5 }}
      className="grid gap-6 lg:grid-cols-2"
    >
      <GlassCard as="section" className="p-6 flex flex-col h-[80vh]">
        <div className="mb-4 flex items-center justify-between">
          <div>
            <h3 className="text-2xl font-bold text-foreground">Session History</h3>
            <p className="text-sm text-muted">Browse your previous check-ins</p>
          </div>
        </div>
        
        <div className="relative mb-6">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-muted h-4 w-4" />
          <input 
            type="text" 
            placeholder="Search sessions..." 
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            className="w-full bg-surface/50 border border-border/50 rounded-lg pl-10 pr-4 py-2 focus:outline-none focus:border-brand-primary transition"
          />
        </div>

        <div className="flex-1 overflow-y-auto space-y-3 pr-2 custom-scrollbar">
          {sessions.map(s => (
            <button
              key={s.sessionId}
              onClick={() => loadSessionDetail(s.sessionId)}
              className={`w-full text-left p-4 rounded-xl border transition ${selectedSession?.sessionId === s.sessionId ? 'bg-primary/10 border-primary/50' : 'bg-surface/30 border-border/40 hover:bg-surface/60'}`}
            >
              <div className="flex justify-between items-start">
                <div>
                  <p className="font-bold text-foreground">{s.title || `Session #${s.sessionId}`}</p>
                  <p className="text-sm text-muted">{new Date(s.startedAt).toLocaleString()}</p>
                </div>
                <span className="px-2 py-1 bg-secondary/10 text-secondary text-xs rounded-full font-semibold">
                  {s.dominantEmotion}
                </span>
              </div>
              
              {s.emotionArc && s.emotionArc.length > 0 && (
                <div className="mt-4 h-12">
                  <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={s.emotionArc.map((v, i) => ({ index: i, valence: v }))}>
                      <YAxis domain={[-1, 1]} hide />
                      <Line type="monotone" dataKey="valence" stroke="#8b5cf6" strokeWidth={2} dot={false} />
                    </LineChart>
                  </ResponsiveContainer>
                </div>
              )}
            </button>
          ))}
        </div>
      </GlassCard>

      <GlassCard as="section" className="p-6 flex flex-col h-[80vh]">
        {selectedSession ? (
          <>
            <div className="flex justify-between items-start border-b border-border/50 pb-4 mb-4">
              <div>
                <h3 className="text-2xl font-bold text-foreground">{selectedSession.title}</h3>
                <p className="text-sm text-muted">Dominant: {selectedSession.dominantEmotion}</p>
              </div>
              <button 
                onClick={exportAsText}
                className="flex items-center gap-2 px-3 py-2 bg-surface/50 border border-border/50 rounded-lg text-sm hover:bg-surface/80 transition"
              >
                <Download size={16} />
                Export
              </button>
            </div>
            
            <div className="flex-1 overflow-y-auto pr-2 custom-scrollbar space-y-6">
              {selectedSession.summary && (
                <div className="p-4 bg-primary/5 border border-primary/20 rounded-xl">
                  <h4 className="font-semibold text-primary mb-2 flex items-center gap-2">
                    <Activity size={16} /> Session Summary
                  </h4>
                  <p className="text-sm text-foreground/80 leading-relaxed">{selectedSession.summary}</p>
                </div>
              )}

              <div className="space-y-4">
                <h4 className="font-semibold text-foreground flex items-center gap-2">
                  <PlayCircle size={16} /> Replay
                </h4>
                {selectedSession.messages.map((m, i) => (
                  <div key={i} className={`flex ${m.senderType === 'USER' ? 'justify-end' : 'justify-start'}`}>
                    <div className={`max-w-[80%] p-3 rounded-2xl ${m.senderType === 'USER' ? 'bg-primary/20 text-foreground rounded-br-none' : 'bg-surface/80 border border-border/50 text-foreground rounded-bl-none'}`}>
                      <p className="text-sm">{m.content}</p>
                    </div>
                  </div>
                ))}
              </div>

              {selectedSession.recommendations.length > 0 && (
                <div className="pt-4 border-t border-border/50">
                  <h4 className="font-semibold text-foreground mb-3">Recommendations Given</h4>
                  <ul className="space-y-2">
                    {selectedSession.recommendations.map((r, i) => (
                      <li key={i} className="text-sm text-muted bg-surface/30 p-3 rounded-lg border border-border/30">
                        <span className="font-semibold text-secondary mr-2">[{r.type}]</span>
                        {r.content}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          </>
        ) : (
          <div className="flex-1 flex items-center justify-center text-muted">
            <p>Select a session to view details.</p>
          </div>
        )}
      </GlassCard>
    </motion.div>
  );
}
