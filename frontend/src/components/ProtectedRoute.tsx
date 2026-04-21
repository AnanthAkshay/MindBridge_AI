import { Navigate } from "react-router-dom";
import { useAuth } from "../store/auth-store";
import { motion } from "framer-motion";
import { BrainCircuit } from "lucide-react";

/**
 * Wraps protected routes — shows loading spinner during session restore,
 * redirects to onboarding if unauthenticated.
 */
export function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { state } = useAuth();

  if (state.status === "loading") {
    return (
      <div className="flex min-h-screen items-center justify-center bg-canvas-light dark:bg-canvas-dark">
        <motion.div
          initial={{ opacity: 0, scale: 0.8 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.5 }}
          className="flex flex-col items-center gap-5"
        >
          <motion.div
            animate={{ rotate: 360 }}
            transition={{ duration: 2, repeat: Infinity, ease: "linear" }}
            className="grid h-16 w-16 place-items-center rounded-2xl bg-brand-gradient text-white shadow-glow"
          >
            <BrainCircuit className="h-8 w-8" />
          </motion.div>
          <p className="text-sm font-semibold text-muted">Restoring session…</p>
        </motion.div>
      </div>
    );
  }

  if (state.status === "unauthenticated") {
    return <Navigate to="/onboarding" replace />;
  }

  return <>{children}</>;
}
