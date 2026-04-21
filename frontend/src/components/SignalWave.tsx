import { motion } from "framer-motion";

const bars = [42, 58, 73, 63, 88, 69, 79, 92, 76, 64, 84, 71];

export function SignalWave() {
  return (
    <div className="flex h-36 items-end gap-2 rounded-panel border border-border/60 bg-surface/40 p-4 backdrop-blur-lg">
      {bars.map((height, index) => (
        <motion.span
          key={height + index}
          initial={{ height: 18, opacity: 0.45 }}
          animate={{ height, opacity: 1 }}
          transition={{
            delay: index * 0.045,
            duration: 0.72,
            ease: "easeOut"
          }}
          className="min-w-0 flex-1 rounded-full bg-gradient-to-t from-primary via-secondary to-white/80 shadow-glow"
        />
      ))}
    </div>
  );
}
