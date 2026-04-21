import type { LucideIcon } from "lucide-react";
import { motion } from "framer-motion";

type StatCardProps = {
  label: string;
  value: string;
  delta: string;
  icon: LucideIcon;
  tone: "primary" | "secondary" | "warning";
};

const toneStyles = {
  primary: "from-primary/22 text-primary",
  secondary: "from-secondary/22 text-secondary",
  warning: "from-warning/22 text-warning"
};

export function StatCard({
  label,
  value,
  delta,
  icon: Icon,
  tone
}: StatCardProps) {
  return (
    <motion.article
      whileHover={{ y: -4 }}
      transition={{ type: "spring", stiffness: 280, damping: 24 }}
      className={`glass-panel rounded-panel bg-gradient-to-br ${toneStyles[tone]} to-transparent p-5`}
    >
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="text-sm font-medium text-muted">{label}</p>
          <p className="mt-3 text-3xl font-bold text-foreground">{value}</p>
        </div>
        <div className="grid h-11 w-11 shrink-0 place-items-center rounded-brand bg-surface/75 shadow-soft backdrop-blur-lg">
          <Icon aria-hidden className="h-5 w-5" />
        </div>
      </div>
      <p className="mt-5 text-sm font-semibold text-muted">{delta}</p>
    </motion.article>
  );
}
