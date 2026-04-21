import { useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { Navigate } from "react-router-dom";
import {
  BrainCircuit,
  ArrowRight,
  Mail,
  Lock,
  User as UserIcon,
  Sparkles,
  Eye,
  EyeOff,
  Ghost,
  ShieldCheck,
  KeyRound,
} from "lucide-react";
import { useAuth } from "../store/auth-store";
import { sendOtp } from "../services/auth";

type Step = "welcome" | "auth" | "preferences";
type AuthMode = "otp";

const slideVariants = {
  enter: (direction: number) => ({
    x: direction > 0 ? 300 : -300,
    opacity: 0,
  }),
  center: {
    x: 0,
    opacity: 1,
  },
  exit: (direction: number) => ({
    x: direction < 0 ? 300 : -300,
    opacity: 0,
  }),
};

export function OnboardingPage() {
  const { state, login, register, loginAnonymous, loginWithOtp } = useAuth();
  const [step, setStep] = useState<Step>("welcome");
  const [direction, setDirection] = useState(0);
  const [authMode, setAuthMode] = useState<AuthMode>("otp");

  // Form state
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [otpCode, setOtpCode] = useState("");
  const [otpSent, setOtpSent] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  // Already authenticated → go to dashboard
  if (state.status === "authenticated") {
    return <Navigate to="/" replace />;
  }

  function goForward(next: Step) {
    setDirection(1);
    setStep(next);
    setError("");
  }

  function goBack(prev: Step) {
    setDirection(-1);
    setStep(prev);
    setError("");
  }

  async function handleAuth() {
    setError("");
    setLoading(true);
    try {
      // Standard login/register removed
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "Something went wrong");
    } finally {
      setLoading(false);
    }
  }

  async function handleSendOtp() {
    setError("");
    setLoading(true);
    try {
      await sendOtp(email);
      setOtpSent(true);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "Could not send OTP");
    } finally {
      setLoading(false);
    }
  }

  async function handleVerifyOtp() {
    setError("");
    setLoading(true);
    try {
      await loginWithOtp(email, otpCode, fullName);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "Invalid or expired OTP");
    } finally {
      setLoading(false);
    }
  }

  async function handleAnonymous() {
    setError("");
    setLoading(true);
    try {
      await loginAnonymous();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "Something went wrong");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-canvas-light px-4 dark:bg-canvas-dark">
      {/* Ambient background orbs */}
      <div className="pointer-events-none absolute inset-0 overflow-hidden">
        <motion.div
          animate={{ x: [0, 40, 0], y: [0, -30, 0] }}
          transition={{ duration: 16, repeat: Infinity, ease: "easeInOut" }}
          className="absolute -left-32 -top-32 h-[500px] w-[500px] rounded-full bg-primary/20 blur-[120px]"
        />
        <motion.div
          animate={{ x: [0, -30, 0], y: [0, 40, 0] }}
          transition={{ duration: 20, repeat: Infinity, ease: "easeInOut" }}
          className="absolute -bottom-32 -right-32 h-[500px] w-[500px] rounded-full bg-secondary/20 blur-[120px]"
        />
      </div>

      <div className="relative z-10 w-full max-w-md">
        <AnimatePresence custom={direction} mode="wait">
          {step === "welcome" && (
            <motion.div
              key="welcome"
              custom={direction}
              variants={slideVariants}
              initial="enter"
              animate="center"
              exit="exit"
              transition={{ type: "spring", stiffness: 300, damping: 30 }}
              className="glass-panel rounded-panel p-8 sm:p-10"
            >
              {/* Logo */}
              <div className="flex justify-center">
                <motion.div
                  initial={{ scale: 0, rotate: -180 }}
                  animate={{ scale: 1, rotate: 0 }}
                  transition={{ type: "spring", stiffness: 200, delay: 0.1 }}
                  className="grid h-20 w-20 place-items-center rounded-3xl bg-brand-gradient text-white shadow-glow"
                >
                  <BrainCircuit className="h-10 w-10" strokeWidth={1.8} />
                </motion.div>
              </div>

              <motion.h1
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.2 }}
                className="mt-8 text-center text-3xl font-black text-foreground"
              >
                Welcome to{" "}
                <span className="bg-brand-gradient bg-clip-text text-transparent">
                  MindBridge AI
                </span>
              </motion.h1>

              <motion.p
                initial={{ opacity: 0, y: 16 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.3 }}
                className="mt-4 text-center text-base leading-7 text-muted"
              >
                Your safe space for mental wellness — guided by AI,
                grounded in privacy.
              </motion.p>

              {/* Feature highlights */}
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ delay: 0.45 }}
                className="mt-8 space-y-3"
              >
                {[
                  { icon: ShieldCheck, text: "End-to-end privacy" },
                  { icon: Sparkles, text: "AI-powered insights" },
                  { icon: BrainCircuit, text: "Evidence-based tools" },
                ].map((item) => (
                  <div
                    key={item.text}
                    className="flex items-center gap-3 rounded-brand border border-border/40 bg-surface/40 px-4 py-3 backdrop-blur-lg"
                  >
                    <item.icon className="h-5 w-5 shrink-0 text-secondary" />
                    <span className="text-sm font-semibold text-foreground">
                      {item.text}
                    </span>
                  </div>
                ))}
              </motion.div>

              {/* CTA */}
              <motion.button
                initial={{ opacity: 0, y: 16 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.55 }}
                whileHover={{ scale: 1.02 }}
                whileTap={{ scale: 0.98 }}
                onClick={() => goForward("auth")}
                className="mt-8 flex w-full items-center justify-center gap-2 rounded-brand bg-brand-gradient px-6 py-4 text-base font-bold text-white shadow-glow transition"
              >
                Get started
                <ArrowRight className="h-5 w-5" />
              </motion.button>


            </motion.div>
          )}

          {step === "auth" && (
            <motion.div
              key="auth"
              custom={direction}
              variants={slideVariants}
              initial="enter"
              animate="center"
              exit="exit"
              transition={{ type: "spring", stiffness: 300, damping: 30 }}
              className="glass-panel rounded-panel p-8 sm:p-10"
            >
              {/* Authentication Header */}
              <div className="text-center">
                <h2 className="text-2xl font-bold text-foreground">Passwordless Sign In</h2>
                <p className="mt-2 text-sm text-muted">Enter your email to receive a secure login code.</p>
              </div>

              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  if (authMode === "otp") {
                    if (!otpSent) handleSendOtp();
                    else handleVerifyOtp();
                  } else {
                    handleAuth();
                  }
                }}
                className="mt-8 space-y-4"
              >
                {/* Full name — Only shown if sentiment needs a name or for first-time profile creation during OTP */}
                <AnimatePresence>
                  {otpSent && (
                    <motion.div
                      initial={{ height: 0, opacity: 0 }}
                      animate={{ height: "auto", opacity: 1 }}
                      exit={{ height: 0, opacity: 0 }}
                      transition={{ duration: 0.25 }}
                      className="overflow-hidden mb-4"
                    >
                      <label className="block text-sm font-semibold text-muted">
                        Full Name (Optional)
                      </label>
                      <div className="relative mt-1.5">
                        <UserIcon className="absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" />
                        <input
                          type="text"
                          value={fullName}
                          onChange={(e) => setFullName(e.target.value)}
                          placeholder="Alex Morgan"
                          className="w-full rounded-brand border border-border/60 bg-surface/50 py-3.5 pl-11 pr-4 text-sm font-medium text-foreground placeholder:text-muted/60 backdrop-blur-lg transition focus:border-secondary focus:outline-none focus:ring-2 focus:ring-secondary/20"
                        />
                      </div>
                    </motion.div>
                  )}
                </AnimatePresence>

                {/* Email */}
                <div>
                  <label className="block text-sm font-semibold text-muted">
                    Email
                  </label>
                  <div className="relative mt-1.5">
                    <Mail className="absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" />
                    <input
                      type="email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      placeholder="you@email.com"
                      required
                      className="w-full rounded-brand border border-border/60 bg-surface/50 py-3.5 pl-11 pr-4 text-sm font-medium text-foreground placeholder:text-muted/60 backdrop-blur-lg transition focus:border-secondary focus:outline-none focus:ring-2 focus:ring-secondary/20"
                    />
                  </div>
                </div>



                {/* OTP Code - Shown only in OTP mode when sent */}
                <AnimatePresence>
                  {authMode === "otp" && otpSent && (
                    <motion.div
                      initial={{ height: 0, opacity: 0 }}
                      animate={{ height: "auto", opacity: 1 }}
                      exit={{ height: 0, opacity: 0 }}
                      transition={{ duration: 0.25 }}
                      className="overflow-hidden"
                    >
                      <label className="block text-sm font-semibold text-muted">
                        6-Digit OTP Code
                      </label>
                      <div className="relative mt-1.5">
                        <KeyRound className="absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" />
                        <input
                          type="text"
                          value={otpCode}
                          onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                          placeholder="123456"
                          required={authMode === "otp" && otpSent}
                          maxLength={6}
                          className="w-full rounded-brand border border-border/60 bg-surface/50 py-3.5 pl-11 pr-4 text-center tracking-[0.5em] text-lg font-bold text-foreground placeholder:text-muted/60 backdrop-blur-lg transition focus:border-secondary focus:outline-none focus:ring-2 focus:ring-secondary/20"
                        />
                      </div>
                    </motion.div>
                  )}
                </AnimatePresence>


                {/* Error message */}
                <AnimatePresence>
                  {error && (
                    <motion.p
                      initial={{ opacity: 0, y: -8 }}
                      animate={{ opacity: 1, y: 0 }}
                      exit={{ opacity: 0, y: -8 }}
                      className="rounded-brand border border-danger/30 bg-danger/10 px-4 py-3 text-sm font-semibold text-danger"
                    >
                      {error}
                    </motion.p>
                  )}
                </AnimatePresence>

                {/* Submit */}
                <motion.button
                  type="submit"
                  whileHover={{ scale: 1.01 }}
                  whileTap={{ scale: 0.98 }}
                  disabled={loading}
                  className="flex w-full items-center justify-center gap-2 rounded-brand bg-brand-gradient px-6 py-4 text-base font-bold text-white shadow-glow transition disabled:opacity-50"
                >
                  {loading
                    ? "Please wait…"
                    : (otpSent ? "Verify & Sign In" : "Send Login Code")}
                </motion.button>
              </form>


              {/* Back */}
              <button
                onClick={() => goBack("welcome")}
                className="mt-4 w-full text-center text-sm font-semibold text-muted transition hover:text-foreground"
              >
                ← Back to welcome
              </button>
            </motion.div>
          )}
        </AnimatePresence>

        {/* Bottom branding */}
        <p className="mt-6 text-center text-xs text-muted/60">
          © {new Date().getFullYear()} MindBridge AI · Privacy-first mental wellness
        </p>
      </div>
    </div>
  );
}
