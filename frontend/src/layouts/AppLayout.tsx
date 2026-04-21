import type { PropsWithChildren } from "react";
import {
  Activity,
  CalendarClock,
  Compass,
  LogOut,
  Menu,
  MessageCircle,
  ShieldCheck,
  Sparkles,
  X,
} from "lucide-react";
import { motion } from "framer-motion";
import { Link, useLocation } from "react-router-dom";
import { LogoMark } from "../components/LogoMark";
import { StatusPill } from "../components/StatusPill";
import { ThemeToggle } from "../components/ThemeToggle";
import { useState } from "react";
import { useAuth } from "../store/auth-store";

const navigation = [
  { label: "Overview", icon: Compass, href: "/" },
  { label: "Sessions", icon: MessageCircle, href: "/chat" },
  { label: "Care Plan", icon: CalendarClock, href: "/care-plan" },
  { label: "Signals", icon: Activity, href: "/signals" },
  { label: "Privacy", icon: ShieldCheck, href: "/privacy" }
];

export function AppLayout({ children }: PropsWithChildren) {
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const { state, logout } = useAuth();
  const location = useLocation();
  const userName = state.status === "authenticated" ? state.user.fullName : "";

  return (
    <div className="min-h-screen overflow-hidden bg-canvas-light text-foreground dark:bg-canvas-dark">
      <div className="mx-auto flex min-h-screen w-full max-w-[1500px] flex-col px-4 py-4 sm:px-6 lg:flex-row lg:gap-6 lg:px-8">
        {/* Mobile Sidebar Overlay */}
        {isMobileMenuOpen && (
          <div 
            className="fixed inset-0 z-40 bg-background/80 backdrop-blur-sm lg:hidden"
            onClick={() => setIsMobileMenuOpen(false)}
          />
        )}

        <aside className={`fixed inset-y-0 left-0 z-50 w-72 transform transition-transform duration-300 lg:static lg:block lg:translate-x-0 ${isMobileMenuOpen ? "translate-x-0" : "-translate-x-full"} py-4 pl-4`}>
          <motion.div
            initial={{ opacity: 0, x: -18 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.45 }}
            className="glass-panel sticky top-6 flex h-[calc(100vh-3rem)] flex-col rounded-panel p-5"
          >
            <LogoMark />
            <nav className="mt-10 space-y-2">
              {navigation.map((item) => {
                const isActive = location.pathname === item.href;
                return (
                  <Link
                    key={item.label}
                    to={item.href}
                    onClick={() => setIsMobileMenuOpen(false)}
                    className={`flex h-12 items-center gap-3 rounded-brand px-4 text-sm font-semibold transition ${
                      isActive
                        ? "bg-brand-gradient text-white shadow-glow"
                        : "text-muted hover:bg-surface/70 hover:text-foreground"
                    }`}
                  >
                    <item.icon aria-hidden className="h-5 w-5" />
                    <span>{item.label}</span>
                  </Link>
                );
              })}
            </nav>
            <div className="mt-auto space-y-3">
              <div className="rounded-brand border border-secondary/20 bg-secondary/10 p-4">
                <div className="flex items-center gap-2 text-sm font-bold text-secondary">
                  <Sparkles aria-hidden className="h-4 w-4" />
                  {userName || "Guest"}
                </div>
                <p className="mt-1 text-xs text-muted">
                  {state.status === "authenticated" && state.user.anonymous
                    ? "Anonymous session"
                    : state.status === "authenticated"
                      ? state.user.email
                      : ""}
                </p>
              </div>
              <button
                onClick={logout}
                className="flex w-full items-center gap-2 rounded-brand px-4 py-3 text-sm font-semibold text-muted transition hover:bg-danger/10 hover:text-danger"
              >
                <LogOut className="h-4 w-4" />
                Sign out
              </button>
            </div>
          </motion.div>
        </aside>

        <div className="flex min-w-0 flex-1 flex-col">
          <header className="sticky top-0 z-20 -mx-4 border-b border-border/40 bg-background/76 px-4 py-4 backdrop-blur-lg sm:-mx-6 sm:px-6 lg:top-0 lg:mx-0 lg:border-b-0 lg:bg-transparent lg:px-0">
            <div className="flex flex-wrap items-center justify-between gap-4">
              <div className="flex items-center gap-3 lg:hidden">
                <button 
                  onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
                  className="rounded-md p-2 text-muted hover:bg-surface/50"
                >
                  {isMobileMenuOpen ? <X className="h-6 w-6" /> : <Menu className="h-6 w-6" />}
                </button>
                <LogoMark />
              </div>
              <div className="hidden min-w-0 lg:block">
                <p className="text-sm font-semibold uppercase text-muted">
                  Mental health AI operations
                </p>
                <h1 className="mt-1 text-2xl font-bold text-foreground">
                  MindBridge AI
                </h1>
              </div>
              <div className="ml-auto flex items-center gap-3">
                <StatusPill />
                <ThemeToggle />
              </div>
            </div>
          </header>

          <main className="flex-1 py-6 lg:py-8">{children}</main>
        </div>
      </div>
    </div>
  );
}
