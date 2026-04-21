import { motion, AnimatePresence } from "framer-motion";
import { Bot } from "lucide-react";

type Props = {
  visible: boolean;
  name?: string;
};

/**
 * Three-dot pulsing typing indicator with AI avatar.
 * Smooth enter/exit transitions via Framer Motion.
 */
export function TypingIndicator({ visible, name = "MindBridge AI" }: Props) {
  return (
    <AnimatePresence>
      {visible && (
        <motion.div
          initial={{ opacity: 0, y: 10, scale: 0.95 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          exit={{ opacity: 0, y: 10, scale: 0.95 }}
          transition={{ type: "spring", stiffness: 400, damping: 30 }}
          className="flex items-center gap-3"
        >
          <div className="grid h-9 w-9 shrink-0 place-items-center rounded-2xl bg-brand-gradient text-white shadow-glow">
            <Bot className="h-4 w-4" />
          </div>
          <div className="rounded-2xl rounded-bl-md border border-border/40 bg-surface/70 px-4 py-3 backdrop-blur-sm dark:bg-surface/30">
            <p className="mb-1.5 text-xs font-bold text-secondary">{name}</p>
            <div className="flex items-center gap-1.5">
              {[0, 1, 2].map((i) => (
                <motion.span
                  key={i}
                  className="block h-2 w-2 rounded-full bg-secondary"
                  animate={{ y: [0, -6, 0] }}
                  transition={{
                    duration: 0.6,
                    repeat: Infinity,
                    delay: i * 0.15,
                    ease: "easeInOut",
                  }}
                />
              ))}
            </div>
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
