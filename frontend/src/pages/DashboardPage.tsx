import {
  Brain,
  HeartPulse,
  LineChart,
  LockKeyhole,
  MessageCircle,
  ShieldCheck,
  TimerReset,
  WandSparkles
} from "lucide-react";
import { motion } from "framer-motion";
import { Link } from "react-router-dom";
import { GlassCard } from "../components/GlassCard";
import { SignalWave } from "../components/SignalWave";
import { StatCard } from "../components/StatCard";

const sessions = [
  {
    title: "Grounding check-in",
    status: "Active",
    time: "11 min",
    tone: "text-success"
  },
  {
    title: "Cognitive reframing",
    status: "Queued",
    time: "Today",
    tone: "text-warning"
  },
  {
    title: "Sleep recovery plan",
    status: "Ready",
    time: "8:30 PM",
    tone: "text-secondary"
  }
];

const chatPreview = [
  {
    role: "User",
    copy: "I keep looping on the same work thought and my chest feels tight."
  },
  {
    role: "MindBridge",
    copy: "Let's slow the loop, name the pressure, and choose one next action."
  }
];

export function DashboardPage() {
  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5 }}
      className="grid gap-5 xl:grid-cols-[minmax(0,1.55fr)_minmax(340px,0.85fr)]"
    >
      <section className="space-y-5">
        <GlassCard
          as="section"
          className="relative overflow-hidden p-6 sm:p-7 lg:p-8"
        >
          <div className="absolute inset-x-0 top-0 h-1 bg-brand-gradient" />
          <div className="grid gap-7 lg:grid-cols-[minmax(0,1fr)_260px] lg:items-center">
            <div className="min-w-0">
              <div className="inline-flex items-center gap-2 rounded-full border border-secondary/25 bg-secondary/10 px-3 py-2 text-sm font-bold text-secondary">
                <ShieldCheck aria-hidden className="h-4 w-4" />
                Privacy-first care intelligence
              </div>
              <h2 className="mt-6 max-w-3xl text-balance text-4xl font-black leading-tight text-foreground sm:text-5xl">
                A calmer bridge between people, clinicians, and AI support.
              </h2>
              <p className="mt-5 max-w-2xl text-lg leading-8 text-muted">
                Live session readiness, sentiment trends, and safety-aware
                conversation workflows in one focused operating canvas.
              </p>
              <div className="mt-7 flex flex-wrap gap-3">
                <Link to="/chat" className="inline-flex h-12 items-center gap-2 rounded-brand bg-brand-gradient px-5 text-sm font-bold text-white shadow-glow transition hover:translate-y-[-1px]">
                  <MessageCircle aria-hidden className="h-5 w-5" />
                  Start session
                </Link>
                <button className="inline-flex h-12 items-center gap-2 rounded-brand border border-border/70 bg-surface/70 px-5 text-sm font-bold text-foreground shadow-soft backdrop-blur-lg transition hover:border-secondary/60">
                  <LockKeyhole aria-hidden className="h-5 w-5" />
                  Review safeguards
                </button>
              </div>
            </div>

            <div className="relative mx-auto aspect-square w-full max-w-[260px]">
              <div className="absolute inset-0 rounded-[2rem] bg-brand-gradient opacity-90 shadow-glow" />
              <div className="absolute inset-3 rounded-[1.6rem] border border-white/40 bg-white/18 backdrop-blur-lg" />
              <div className="absolute inset-0 grid place-items-center">
                <div className="grid h-32 w-32 place-items-center rounded-full border border-white/45 bg-white/20 text-white shadow-soft backdrop-blur-lg">
                  <Brain aria-hidden className="h-16 w-16" strokeWidth={1.7} />
                </div>
              </div>
              <div className="absolute bottom-5 left-5 right-5 rounded-brand border border-white/30 bg-slate-950/24 px-4 py-3 text-white backdrop-blur-lg">
                <p className="text-sm font-semibold">Care signal</p>
                <p className="mt-1 text-2xl font-black">92%</p>
              </div>
            </div>
          </div>
        </GlassCard>

        <div className="grid gap-5 md:grid-cols-3">
          <StatCard
            label="Mood stability"
            value="84%"
            delta="+12% from last week"
            icon={HeartPulse}
            tone="primary"
          />
          <StatCard
            label="Session latency"
            value="126ms"
            delta="Redis cache prepared"
            icon={TimerReset}
            tone="secondary"
          />
          <StatCard
            label="Risk triage"
            value="Low"
            delta="Escalation rules clear"
            icon={LineChart}
            tone="warning"
          />
        </div>

        <GlassCard as="section" className="p-6">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div>
              <p className="text-sm font-semibold uppercase text-muted">
                Active bridge session
              </p>
              <h3 className="mt-2 text-2xl font-bold text-foreground">
                Anxiety support pathway
              </h3>
            </div>
            <span className="rounded-full border border-success/30 bg-success/10 px-3 py-2 text-sm font-bold text-success">
              supervised
            </span>
          </div>
          <div className="mt-6 grid gap-4 lg:grid-cols-2">
            {chatPreview.map((message) => (
              <div
                key={message.role}
                className="rounded-panel border border-border/60 bg-surface/48 p-5 backdrop-blur-lg"
              >
                <p className="text-sm font-bold text-secondary">
                  {message.role}
                </p>
                <p className="mt-3 text-base leading-7 text-foreground">
                  {message.copy}
                </p>
              </div>
            ))}
          </div>
        </GlassCard>
      </section>

      <aside className="space-y-5">
        <GlassCard as="section" className="p-6">
          <div className="flex items-start justify-between gap-4">
            <div>
              <p className="text-sm font-semibold uppercase text-muted">
                Wellness signal
              </p>
              <h3 className="mt-2 text-2xl font-bold text-foreground">
                Emotional trend
              </h3>
            </div>
            <div className="grid h-12 w-12 place-items-center rounded-brand bg-brand-gradient text-white shadow-glow">
              <WandSparkles aria-hidden className="h-5 w-5" />
            </div>
          </div>
          <div className="mt-6">
            <SignalWave />
          </div>
          <p className="mt-5 text-sm leading-6 text-muted">
            Signal quality is strongest after a structured check-in and two
            journal samples.
          </p>
        </GlassCard>

        <GlassCard as="section" className="p-6">
          <div className="flex items-center justify-between gap-4">
            <div>
              <p className="text-sm font-semibold uppercase text-muted">
                Session queue
              </p>
              <h3 className="mt-2 text-2xl font-bold text-foreground">
                Today
              </h3>
            </div>
          </div>
          <div className="mt-6 space-y-3">
            {sessions.map((session) => (
              <div
                key={session.title}
                className="flex items-center justify-between gap-3 rounded-brand border border-border/60 bg-surface/46 p-4 backdrop-blur-lg"
              >
                <div className="min-w-0">
                  <p className="truncate font-bold text-foreground">
                    {session.title}
                  </p>
                  <p className={`mt-1 text-sm font-semibold ${session.tone}`}>
                    {session.status}
                  </p>
                </div>
                <span className="shrink-0 rounded-full bg-background/70 px-3 py-1 text-sm font-bold text-muted">
                  {session.time}
                </span>
              </div>
            ))}
          </div>
        </GlassCard>

        <GlassCard as="section" className="p-6">
          <p className="text-sm font-semibold uppercase text-muted">
            Safety baseline
          </p>
          <div className="mt-5 space-y-4">
            {["CORS scoped", "Secrets in environment", "Validation enabled"].map(
              (item) => (
                <div key={item} className="flex items-center gap-3">
                  <span className="grid h-8 w-8 place-items-center rounded-full bg-secondary/12 text-secondary">
                    <ShieldCheck aria-hidden className="h-4 w-4" />
                  </span>
                  <span className="font-semibold text-foreground">{item}</span>
                </div>
              )
            )}
          </div>
        </GlassCard>
      </aside>
    </motion.div>
  );
}
