export default {
    darkMode: "class",
    content: ["./index.html", "./src/**/*.{ts,tsx}"],
    theme: {
        extend: {
            colors: {
                primary: "rgb(var(--color-primary) / <alpha-value>)",
                secondary: "rgb(var(--color-secondary) / <alpha-value>)",
                background: "rgb(var(--color-background) / <alpha-value>)",
                foreground: "rgb(var(--color-foreground) / <alpha-value>)",
                muted: "rgb(var(--color-muted) / <alpha-value>)",
                border: "rgb(var(--color-border) / <alpha-value>)",
                surface: "rgb(var(--color-surface) / <alpha-value>)",
                success: "rgb(var(--color-success) / <alpha-value>)",
                warning: "rgb(var(--color-warning) / <alpha-value>)",
                danger: "rgb(var(--color-danger) / <alpha-value>)"
            },
            borderRadius: {
                brand: "var(--radius-brand)",
                panel: "var(--radius-panel)"
            },
            boxShadow: {
                soft: "var(--shadow-soft)",
                glow: "var(--shadow-glow)"
            },
            fontFamily: {
                sans: ["Inter", "ui-sans-serif", "system-ui", "Segoe UI", "sans-serif"]
            },
            backgroundImage: {
                "brand-gradient": "linear-gradient(135deg, #7C3AED 0%, #14B8A6 100%)",
                "canvas-light": "radial-gradient(circle at top left, rgba(124,58,237,0.18), transparent 34%), radial-gradient(circle at 78% 18%, rgba(20,184,166,0.18), transparent 32%)",
                "canvas-dark": "radial-gradient(circle at top left, rgba(124,58,237,0.34), transparent 34%), radial-gradient(circle at 78% 18%, rgba(20,184,166,0.26), transparent 32%)"
            }
        }
    },
    plugins: []
};
