import type { HTMLAttributes, PropsWithChildren } from "react";

type GlassCardProps = PropsWithChildren<HTMLAttributes<HTMLElement>> & {
  as?: "article" | "section" | "div";
};

export function GlassCard({
  as: Element = "article",
  className = "",
  children,
  ...props
}: GlassCardProps) {
  return (
    <Element
      className={`glass-panel rounded-panel p-5 transition duration-300 ${className}`}
      {...props}
    >
      {children}
    </Element>
  );
}
