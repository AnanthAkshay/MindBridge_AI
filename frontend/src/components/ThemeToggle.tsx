import { Moon, SunMedium } from "lucide-react";
import { motion } from "framer-motion";
import { useTheme } from "../hooks/useTheme";

export function ThemeToggle() {
  const { theme, toggleTheme } = useTheme();
  const isDark = theme === "dark";

  return (
    <button
      type="button"
      aria-label={isDark ? "Switch to light mode" : "Switch to dark mode"}
      onClick={toggleTheme}
      className="group relative inline-flex h-11 w-[5.6rem] items-center rounded-full border border-border/70 bg-surface/70 p-1 text-foreground shadow-soft backdrop-blur-lg transition hover:border-secondary/60"
    >
      <span className="sr-only">Toggle theme</span>
      <motion.span
        layout
        transition={{ type: "spring", stiffness: 420, damping: 30 }}
        className="absolute h-9 w-9 rounded-full bg-brand-gradient shadow-glow"
        style={{ left: isDark ? "3.05rem" : "0.25rem" }}
      />
      <span className="relative z-10 grid h-9 w-9 place-items-center text-white">
        <SunMedium aria-hidden className="h-4 w-4" />
      </span>
      <span className="relative z-10 grid h-9 w-9 place-items-center text-white">
        <Moon aria-hidden className="h-4 w-4" />
      </span>
    </button>
  );
}
