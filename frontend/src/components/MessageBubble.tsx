import { motion } from "framer-motion";
import { Bot, User as UserIcon, Activity } from "lucide-react";
import type { ChatMessage } from "../services/websocket";

type Props = {
  message: ChatMessage;
  isLatest: boolean;
};

export function MessageBubble({ message, isLatest }: Props) {
  const isUser = message.senderType === "USER";

  return (
    <motion.div
      initial={isLatest ? { opacity: 0, y: 16, scale: 0.96 } : false}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      transition={{ type: "spring", stiffness: 400, damping: 30 }}
      className={`flex gap-3 ${isUser ? "flex-row-reverse" : "flex-row"}`}
    >
      {/* Avatar */}
      <div
        className={`grid h-9 w-9 shrink-0 place-items-center rounded-2xl ${
          isUser
            ? "bg-primary/15 text-primary dark:bg-primary/25"
            : "bg-brand-gradient text-white shadow-glow"
        }`}
      >
        {isUser ? (
          <UserIcon className="h-4 w-4" />
        ) : (
          <Bot className="h-4 w-4" />
        )}
      </div>

      {/* Bubble */}
      <div
        className={`relative max-w-[75%] rounded-2xl px-4 py-3 text-sm leading-relaxed ${
          isUser
            ? "rounded-br-md bg-primary/10 text-foreground dark:bg-primary/20"
            : "rounded-bl-md border border-border/40 bg-surface/70 text-foreground backdrop-blur-sm dark:bg-surface/30"
        }`}
      >
        {!isUser && (
          <p className="mb-1.5 text-xs font-bold text-secondary">MindBridge AI</p>
        )}
        <div className="whitespace-pre-wrap">
          {message.content}
          {message.id === 999999 && (
            <motion.span
              animate={{ opacity: [1, 0, 1] }}
              transition={{ repeat: Infinity, duration: 0.8 }}
              className="inline-block ml-[3px] w-[5px] h-[1em] bg-foreground/60 align-middle rounded-sm"
            />
          )}
        </div>

        {isUser && message.emotion && message.emotion !== "neutral" && (
          <motion.div 
            initial={{ opacity: 0, scale: 0.8 }} 
            animate={{ opacity: 1, scale: 1 }} 
            transition={{ delay: 0.2 }}
            className={`mt-3 flex w-fit items-center gap-1.5 rounded-full px-2 py-0.5 text-[10px] font-bold border ${getEmotionColor(message.emotion)}`}
          >
            <Activity className="h-3 w-3" />
            <span>{message.emotion}</span>
            {message.emotionScore && (
              <span className="opacity-60 ml-0.5">· {Math.round(message.emotionScore * 100)}%</span>
            )}
          </motion.div>
        )}

        <p
          className={`mt-2 text-[10px] font-medium ${
            isUser ? "text-right text-muted/60" : "text-muted/60"
          }`}
        >
          {formatTime(message.createdAt)}
        </p>
      </div>
    </motion.div>
  );
}

function getEmotionColor(emotion: string): string {
  const normalized = emotion.toLowerCase();
  
  if (["sadness", "grief", "hopeless"].includes(normalized)) return "bg-info/10 text-info border-info/20";
  if (["anxious", "fear", "nervousness", "worried"].includes(normalized)) return "bg-warning/10 text-warning border-warning/20";
  if (["anger", "annoyance", "disgust"].includes(normalized)) return "bg-danger/10 text-danger border-danger/20";
  if (["joy", "excitement", "love", "optimism"].includes(normalized)) return "bg-success/10 text-success border-success/20";
  
  return "bg-secondary/10 text-secondary border-secondary/20";
}

function formatTime(iso: string): string {
  try {
    return new Date(iso).toLocaleTimeString([], {
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return "";
  }
}
