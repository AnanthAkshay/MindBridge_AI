import { useEffect, useState } from "react";
import { motion } from "framer-motion";
import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer, PieChart, Pie, Cell } from "recharts";
import { Activity, HeartPulse, Clock, AlertTriangle } from "lucide-react";
import { GlassCard } from "../components/GlassCard";
import { StatCard } from "../components/StatCard";
import { analyticsApi, MoodTrend, EmotionDistribution, SessionTimeline } from "../services/analytics";
import { useAuth } from "../store/auth-store";
import { websocketService } from "../services/websocket";

const COLORS = ['#0ea5e9', '#8b5cf6', '#ec4899', '#f43f5e', '#10b981', '#f59e0b'];

export function AnalyticsDashboardPage() {
  const { state } = useAuth();
  const user = state.status === "authenticated" ? state.user : null;
  const [moodTrend, setMoodTrend] = useState<MoodTrend[]>([]);
  const [emotions, setEmotions] = useState<EmotionDistribution[]>([]);
  const [timeline, setTimeline] = useState<SessionTimeline[]>([]);
  const [liveRiskLevel, setLiveRiskLevel] = useState<string>("LOW");

  useEffect(() => {
    if (user) {
      analyticsApi.getMoodTrend(user.id).then(setMoodTrend);
      analyticsApi.getEmotionDistribution(user.id).then(setEmotions);
      analyticsApi.getSessionTimeline(user.id).then(setTimeline);

      // Subscribe to live risk updates
      websocketService.subscribeToRiskAlerts(user.id, (alert: any) => {
        setLiveRiskLevel(alert.level);
        // Refresh trend if a new session updates
        analyticsApi.getMoodTrend(user.id).then(setMoodTrend);
      });
    }
    
    return () => {
      if (user) websocketService.unsubscribeFromRiskAlerts(user.id);
    };
  }, [user]);

  const averageMood = moodTrend.length > 0 
    ? (moodTrend.reduce((acc, curr) => acc + curr.averageMood, 0) / moodTrend.length).toFixed(1)
    : "0";

  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5 }}
      className="space-y-6"
    >
      <div className="grid gap-5 md:grid-cols-3">
        <StatCard
          label="Average Mood (7d)"
          value={averageMood}
          delta="Based on recent sessions"
          icon={HeartPulse}
          tone="primary"
        />
        <StatCard
          label="Recent Sessions"
          value={timeline.length.toString()}
          delta="Last 30 days"
          icon={Clock}
          tone="secondary"
        />
        <StatCard
          label="Live Risk Status"
          value={liveRiskLevel}
          delta={liveRiskLevel !== 'LOW' ? "Elevated risk detected" : "Normal baseline"}
          icon={AlertTriangle}
          tone={liveRiskLevel === 'HIGH' || liveRiskLevel === 'MODERATE' ? 'warning' : 'primary'}
        />
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <GlassCard as="section" className="p-6">
          <div className="mb-6">
            <p className="text-sm font-semibold uppercase text-muted">Analytics</p>
            <h3 className="mt-2 text-2xl font-bold text-foreground">Mood Trend (7 Days)</h3>
          </div>
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={moodTrend}>
                <XAxis dataKey="date" stroke="#888888" fontSize={12} tickLine={false} axisLine={false} />
                <YAxis stroke="#888888" fontSize={12} tickLine={false} axisLine={false} domain={[0, 10]} />
                <Tooltip contentStyle={{ backgroundColor: 'rgba(0,0,0,0.8)', border: 'none', borderRadius: '8px' }} />
                <Line type="monotone" dataKey="averageMood" stroke="#8b5cf6" strokeWidth={3} dot={{ r: 4 }} activeDot={{ r: 8 }} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </GlassCard>

        <GlassCard as="section" className="p-6">
          <div className="mb-6">
            <p className="text-sm font-semibold uppercase text-muted">Distribution</p>
            <h3 className="mt-2 text-2xl font-bold text-foreground">Dominant Emotions</h3>
          </div>
          <div className="h-64 flex items-center justify-center">
            {emotions.length > 0 ? (
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={emotions}
                    cx="50%"
                    cy="50%"
                    innerRadius={60}
                    outerRadius={80}
                    paddingAngle={5}
                    dataKey="value"
                  >
                    {emotions.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip contentStyle={{ backgroundColor: 'rgba(0,0,0,0.8)', border: 'none', borderRadius: '8px' }} />
                </PieChart>
              </ResponsiveContainer>
            ) : (
              <p className="text-muted">No emotion data available yet.</p>
            )}
          </div>
        </GlassCard>
      </div>

      <GlassCard as="section" className="p-6">
        <div className="mb-6">
          <p className="text-sm font-semibold uppercase text-muted">History</p>
          <h3 className="mt-2 text-2xl font-bold text-foreground">Session Timeline</h3>
        </div>
        <div className="space-y-4">
          {timeline.length > 0 ? (
            timeline.map(session => (
              <div key={session.sessionId} className="flex items-center justify-between p-4 rounded-xl bg-surface/50 border border-border/50">
                <div className="flex items-center gap-4">
                  <div className="p-3 bg-secondary/10 rounded-full text-secondary">
                    <Activity size={20} />
                  </div>
                  <div>
                    <p className="font-bold">Session #{session.sessionId}</p>
                    <p className="text-sm text-muted">{new Date(session.startedAt).toLocaleString()}</p>
                  </div>
                </div>
                <div className="text-right">
                  <p className="font-semibold text-foreground">Mood: {session.moodScore}/10</p>
                  <p className="text-sm text-muted">Risk Score: {session.riskScore}</p>
                </div>
              </div>
            ))
          ) : (
            <p className="text-muted">No sessions found.</p>
          )}
        </div>
      </GlassCard>
    </motion.div>
  );
}
