import { useCallback, useEffect, useRef, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  Send,
  Plus,
  ArrowLeft,
  MessageCircle,
  Sparkles,
  Shield,
  Wifi,
  WifiOff,
} from "lucide-react";
import { MessageBubble } from "../components/MessageBubble";
import { TypingIndicator } from "../components/TypingIndicator";
import {
  connectWebSocket,
  subscribeToSession,
  sendChatMessage,
  sendTypingIndicator,
  disconnectWebSocket,
  isWebSocketConnected,
  type ChatMessage,
  type TypingEvent,
  type StreamDelta,
} from "../services/websocket";
import { InterventionWidget } from "../components/InterventionWidget";
import { type InterventionCard, suggestInterventions } from "../services/recommendations";
import {
  createChatSession,
  listChatSessions,
  getChatHistory,
  fetchMemoryInsight,
  type ChatSession,
  type MemoryInsight,
} from "../services/chat";

type View = "sessions" | "chat";

export function ChatPage() {
  const [view, setView] = useState<View>("sessions");
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [activeSession, setActiveSession] = useState<ChatSession | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [isTyping, setIsTyping] = useState(false);
  const [streamBuffer, setStreamBuffer] = useState<StreamDelta | null>(null);
  const [connected, setConnected] = useState(false);
  const [sending, setSending] = useState(false);
  const [loadingSessions, setLoadingSessions] = useState(true);
  const [memoryInsight, setMemoryInsight] = useState<MemoryInsight | null>(null);
  const [recommendations, setRecommendations] = useState<InterventionCard[]>([]);
  const [sendError, setSendError] = useState<string | null>(null);
  const [useMemory, setUseMemory] = useState<boolean>(() => {
    const saved = localStorage.getItem("mindbridge_use_memory");
    return saved == null ? true : saved === "true";
  });

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const typingTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const subscriptionRef = useRef<{ unsubscribe: () => void } | null>(null);

  const MAX_LENGTH = 2000;

  // Scroll to bottom when new messages arrive
  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, []);

  useEffect(() => {
    scrollToBottom();
  }, [messages, isTyping, streamBuffer, scrollToBottom]);

  // Load sessions on mount
  useEffect(() => {
    async function load() {
      try {
        const data = await listChatSessions();
        setSessions(data);
      } catch (e) {
        console.error("Failed to load sessions:", e);
      } finally {
        setLoadingSessions(false);
      }
    }
    load();
  }, []);

  // Connect WebSocket on mount
  useEffect(() => {
    const client = connectWebSocket();

    const checkConnection = setInterval(() => {
      setConnected(isWebSocketConnected());
    }, 1000);

    // Wait for connection
    const waitForConnect = setInterval(() => {
      if (client.connected) {
        setConnected(true);
        clearInterval(waitForConnect);
      }
    }, 200);

    return () => {
      clearInterval(checkConnection);
      clearInterval(waitForConnect);
      disconnectWebSocket();
    };
  }, []);

  // Re-fetch history on reconnect to ensure no dropped messages
  useEffect(() => {
    if (connected && activeSession) {
      getChatHistory(activeSession.id)
        .then((history) => setMessages(history))
        .catch((e) => console.error("Failed to sync history on reconnect:", e));
    }
  }, [connected, activeSession]);

  // Subscribe to active session's topic
  useEffect(() => {
    if (!activeSession || !connected) return;

    // Clean up previous subscription
    subscriptionRef.current?.unsubscribe();

    const sub = subscribeToSession(
      activeSession.id,
      (msg: ChatMessage) => {
        setMessages((prev) => {
          const withoutOptimistic = prev.filter(
            (m) =>
              !(
                m.id < 0 &&
                m.senderType === "USER" &&
                msg.senderType === "USER" &&
                m.content === msg.content
              )
          );
          // Deduplicate by id
          if (withoutOptimistic.some((m) => m.id === msg.id)) return withoutOptimistic;
          return [...withoutOptimistic, msg];
        });
      },
      (event: TypingEvent) => {
        // Only show typing indicator for AI (userId === 0)
        if (event.userId === 0) {
          setIsTyping(event.typing);
        }
      },
      (delta: StreamDelta) => {
        if (delta.done) {
          setStreamBuffer(null);
          // When AI finishes replying, check the user's last message emotion explicitly
          setMessages((currentMessages) => {
            let lastUserMsgIndex = -1;
            for (let i = currentMessages.length - 1; i >= 0; i--) {
              if (currentMessages[i].senderType === "USER") {
                lastUserMsgIndex = i;
                break;
              }
            }
            if (lastUserMsgIndex !== -1) {
              const lastUserMsg = currentMessages[lastUserMsgIndex];
              if (lastUserMsg.emotion && lastUserMsg.emotion !== "neutral") {
                const isHighRiskEmotion = ["sad", "hopeless", "depressed", "despair"].includes(lastUserMsg.emotion.toLowerCase());
                const calculatedRisk = isHighRiskEmotion ? (lastUserMsg.emotionScore ? lastUserMsg.emotionScore * 100 : 70) : 30;

                suggestInterventions(lastUserMsg.emotion, calculatedRisk, activeSession.id)
                  .then(rec => setRecommendations(rec))
                  .catch(e => console.error(e));
              }
            }
            return currentMessages;
          });
        } else {
          setStreamBuffer((prev) => {
            if (!prev || prev.messageId !== delta.messageId) {
              return delta;
            }
            return {
              ...prev,
              content: prev.content + delta.content,
            };
          });
        }
      }
    );

    subscriptionRef.current = sub;

    return () => {
      sub.unsubscribe();
      subscriptionRef.current = null;
    };
  }, [activeSession, connected]);

  // Open a session: load history, switch view
  async function openSession(session: ChatSession) {
    setActiveSession(session);
    setView("chat");
    setMessages([]);
    setIsTyping(false);

    try {
      const history = await getChatHistory(session.id);
      setMessages(history);
      if (useMemory) {
        const memory = await fetchMemoryInsight();
        if (memory && memory.triggers && memory.triggers.length > 0) {
          setMemoryInsight(memory);
        } else {
          setMemoryInsight(null);
        }
      } else {
        setMemoryInsight(null);
      }
    } catch (e) {
      console.error("Failed to load history or memory:", e);
    }
  }

  // Create a new session
  async function handleNewSession() {
    try {
      const session = await createChatSession();
      setSessions((prev) => [session, ...prev]);
      openSession(session);
    } catch (e) {
      console.error("Failed to create session:", e);
    }
  }

  // Send message
  function handleSend() {
    const trimmed = input.trim();
    if (!trimmed || !activeSession || sending || !connected) return;

    setSending(true);
    setSendError(null);
    const optimisticId = -Date.now();
    setMessages((prev) => [
      ...prev,
      {
        id: optimisticId,
        sessionId: activeSession.id,
        senderType: "USER",
        content: trimmed,
        createdAt: new Date().toISOString(),
      },
    ]);

    try {
      sendChatMessage(activeSession.id, trimmed, useMemory);
      setInput("");
      // Clear typing indicator
      if (typingTimeoutRef.current) {
        clearTimeout(typingTimeoutRef.current);
      }
      sendTypingIndicator(activeSession.id, false);
    } catch (e) {
      console.error("Failed to send:", e);
      setMessages((prev) => prev.filter((m) => m.id !== optimisticId));
      setSendError("Message failed to send. Check connection and try again.");
    } finally {
      setSending(false);
    }

    // Refocus input
    inputRef.current?.focus();
  }

  // Debounced typing indicator
  function handleInputChange(value: string) {
    setInput(value);

    if (!activeSession || !connected) return;

    sendTypingIndicator(activeSession.id, true);

    if (typingTimeoutRef.current) {
      clearTimeout(typingTimeoutRef.current);
    }
    typingTimeoutRef.current = setTimeout(() => {
      sendTypingIndicator(activeSession.id, false);
    }, 1500);
  }

  // Handle Enter key (Shift+Enter for newline)
  function handleKeyDown(e: React.KeyboardEvent) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  }

  // === RENDER ===

  return (
    <div className="flex h-[calc(100vh-10rem)] flex-col overflow-hidden rounded-panel border border-border/40 bg-surface/30 shadow-soft backdrop-blur-xl dark:bg-surface/10 lg:h-[calc(100vh-8rem)]">
      <AnimatePresence mode="wait">
        {view === "sessions" ? (
          <motion.div
            key="sessions"
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: -20 }}
            transition={{ duration: 0.2 }}
            className="flex h-full flex-col"
          >
            {/* Sessions Header */}
            <div className="flex items-center justify-between border-b border-border/40 px-5 py-4">
              <div>
                <h2 className="text-lg font-bold text-foreground">Sessions</h2>
                <p className="mt-0.5 text-xs font-medium text-muted">
                  Your private conversations
                </p>
              </div>
              <div className="flex items-center gap-2">
                <ConnectionBadge connected={connected} />
                <motion.button
                  whileHover={{ scale: 1.04 }}
                  whileTap={{ scale: 0.96 }}
                  onClick={handleNewSession}
                  className="flex items-center gap-2 rounded-brand bg-brand-gradient px-4 py-2.5 text-sm font-bold text-white shadow-glow transition"
                >
                  <Plus className="h-4 w-4" />
                  New chat
                </motion.button>
              </div>
            </div>

            {/* Sessions List */}
            <div className="flex-1 overflow-y-auto p-4">
              {loadingSessions ? (
                <div className="flex h-full items-center justify-center">
                  <div className="h-8 w-8 animate-spin rounded-full border-2 border-secondary border-t-transparent" />
                </div>
              ) : sessions.length === 0 ? (
                <EmptyState onNew={handleNewSession} />
              ) : (
                <div className="space-y-2">
                  {sessions.map((session, i) => (
                    <motion.button
                      key={session.id}
                      initial={{ opacity: 0, y: 10 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ delay: i * 0.05 }}
                      onClick={() => openSession(session)}
                      className="flex w-full items-center gap-3 rounded-brand border border-border/40 bg-surface/50 p-4 text-left transition hover:border-secondary/40 hover:bg-surface/80 dark:bg-surface/20"
                    >
                      <div className="grid h-10 w-10 shrink-0 place-items-center rounded-2xl bg-primary/10 text-primary dark:bg-primary/20">
                        <MessageCircle className="h-5 w-5" />
                      </div>
                      <div className="min-w-0 flex-1">
                        <p className="truncate font-semibold text-foreground">
                          {session.title}
                        </p>
                        <p className="mt-0.5 text-xs text-muted">
                          {formatDate(session.createdAt)}
                        </p>
                      </div>
                      <span
                        className={`rounded-full px-2.5 py-1 text-[10px] font-bold uppercase ${
                          session.status === "ACTIVE"
                            ? "bg-success/10 text-success"
                            : "bg-muted/10 text-muted"
                        }`}
                      >
                        {session.status}
                      </span>
                    </motion.button>
                  ))}
                </div>
              )}
            </div>
          </motion.div>
        ) : (
          <motion.div
            key="chat"
            initial={{ opacity: 0, x: 20 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: 20 }}
            transition={{ duration: 0.2 }}
            className="flex h-full flex-col"
          >
            {/* Chat Header */}
            <div className="flex items-center gap-3 border-b border-border/40 px-4 py-3">
              <button
                onClick={() => setView("sessions")}
                className="grid h-9 w-9 place-items-center rounded-brand text-muted transition hover:bg-surface/70 hover:text-foreground"
              >
                <ArrowLeft className="h-5 w-5" />
              </button>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-bold text-foreground">
                  {activeSession?.title ?? "Chat"}
                </p>
                <div className="flex items-center gap-1.5 text-[11px] text-muted">
                  <Shield className="h-3 w-3 text-success" />
                  <span>End-to-end encrypted</span>
                </div>
              </div>
              <ConnectionBadge connected={connected} />
            </div>
            <div className="flex items-center justify-between px-4 py-2 border-b border-border/30 bg-surface/20">
              <p className="text-[11px] text-muted">AI mode: Empathetic therapist assistant</p>
              <button
                onClick={() => {
                  const next = !useMemory;
                  setUseMemory(next);
                  localStorage.setItem("mindbridge_use_memory", String(next));
                  setMemoryInsight(null);
                }}
                className={`rounded-full px-2.5 py-1 text-[10px] font-bold uppercase ${useMemory ? "bg-secondary/20 text-secondary" : "bg-muted/20 text-muted"}`}
              >
                Memory {useMemory ? "On" : "Off"}
              </button>
            </div>

            {/* Memory Insight Card */}
            {memoryInsight && memoryInsight.trend !== "Baseline" && (
              <motion.div 
                initial={{ opacity: 0, y: -10 }}
                animate={{ opacity: 1, y: 0 }}
                className="mx-4 mt-4 flex items-start gap-3 rounded-xl border border-secondary/20 bg-secondary/5 px-4 py-3 shadow-sm backdrop-blur-sm"
              >
                <div className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-secondary/10 text-secondary">
                  <Sparkles className="h-4 w-4" />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-semibold text-foreground">Memory Insight</p>
                  <p className="mt-0.5 text-xs font-medium text-muted/80">{memoryInsight.recentSummary}</p>
                  {memoryInsight.triggers.length > 0 && (
                    <div className="mt-2 flex flex-wrap gap-1.5">
                      {memoryInsight.triggers.map((tag) => (
                        <span key={tag} className="rounded bg-background/50 px-2 py-0.5 text-[10px] uppercase font-bold text-muted border border-border/50">
                          {tag}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              </motion.div>
            )}

            {/* Messages Area */}
            <div className="flex-1 overflow-y-auto px-4 py-5">
              {messages.length === 0 && !isTyping ? (
                <ChatWelcome onSelectPrompt={(text) => handleInputChange(text)} />
              ) : (
                <div className="space-y-4">
                  {messages.map((msg, i) => (
                    <MessageBubble
                      key={msg.id}
                      message={msg}
                      isLatest={i === messages.length - 1 && !streamBuffer}
                    />
                  ))}
                  {streamBuffer && (
                    <MessageBubble
                      message={{
                        id: 999999, // Temp ID
                        sessionId: activeSession?.id ?? 0,
                        senderType: "AI",
                        content: streamBuffer.content,
                        createdAt: new Date().toISOString(),
                      }}
                      isLatest={true}
                    />
                  )}
                  <TypingIndicator visible={isTyping && !streamBuffer} />
                  <div ref={messagesEndRef} />
                </div>
              )}
            </div>

            {/* Recommendations Tray */}
            {recommendations.length > 0 && !streamBuffer && (
              <motion.div 
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                className="px-4 pb-4 space-y-2"
              >
                {recommendations.map(req => (
                  <InterventionWidget 
                    key={req.id} 
                    card={req} 
                    onComplete={() => setRecommendations(prev => prev.filter(r => r.id !== req.id))}
                  />
                ))}
              </motion.div>
            )}

            {/* Input Area */}
            <div className="border-t border-border/40 px-4 py-3">
              {!streamBuffer && messages.some((m) => m.senderType === "AI") && (
                <div className="mb-2 flex flex-wrap gap-2">
                  <button
                    onClick={() => {
                      const lastUser = [...messages].reverse().find((m) => m.senderType === "USER");
                      if (lastUser) {
                        setInput(lastUser.content);
                        inputRef.current?.focus();
                      }
                    }}
                    className="rounded-full border border-border/40 px-3 py-1 text-xs text-muted hover:text-foreground hover:border-secondary/40"
                  >
                    Regenerate reply
                  </button>
                  <button
                    onClick={() => {
                      setInput("Can you summarize what we discussed and give me 3 practical next steps?");
                      inputRef.current?.focus();
                    }}
                    className="rounded-full border border-border/40 px-3 py-1 text-xs text-muted hover:text-foreground hover:border-secondary/40"
                  >
                    Summarize session
                  </button>
                </div>
              )}
              {sendError && (
                <p className="mb-2 rounded-lg border border-warning/40 bg-warning/10 px-3 py-2 text-xs font-medium text-warning">
                  {sendError}
                </p>
              )}
              <div className="flex items-end gap-2">
                <div className="relative flex-1">
                  <textarea
                    ref={inputRef}
                    value={input}
                    onChange={(e) => handleInputChange(e.target.value)}
                    onKeyDown={handleKeyDown}
                    placeholder="Type your message…"
                    maxLength={MAX_LENGTH}
                    rows={1}
                    disabled={!connected || sending}
                    className="max-h-32 min-h-[44px] w-full resize-none rounded-2xl border border-border/50 bg-surface/60 py-3 pl-4 pr-12 text-sm text-foreground placeholder:text-muted/50 backdrop-blur-sm transition focus:border-secondary focus:outline-none focus:ring-2 focus:ring-secondary/20 disabled:opacity-50 dark:bg-surface/20"
                    style={{ height: "auto", overflow: "hidden" }}
                    onInput={(e) => {
                      const target = e.target as HTMLTextAreaElement;
                      target.style.height = "auto";
                      target.style.height =
                        Math.min(target.scrollHeight, 128) + "px";
                    }}
                  />
                  <span className="absolute bottom-2 right-3 text-[10px] font-medium text-muted/40">
                    {input.length}/{MAX_LENGTH}
                  </span>
                </div>
                <motion.button
                  whileHover={{ scale: 1.06 }}
                  whileTap={{ scale: 0.94 }}
                  onClick={handleSend}
                  disabled={!input.trim() || !connected || sending}
                  className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-brand-gradient text-white shadow-glow transition disabled:opacity-40 disabled:shadow-none"
                >
                  <Send className="h-4 w-4" />
                </motion.button>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

// ── Sub-components ──

function ConnectionBadge({ connected }: { connected: boolean }) {
  return (
    <div
      className={`flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[10px] font-bold ${
        connected
          ? "bg-success/10 text-success"
          : "bg-warning/10 text-warning"
      }`}
    >
      {connected ? (
        <Wifi className="h-3 w-3" />
      ) : (
        <WifiOff className="h-3 w-3" />
      )}
      {connected ? "Live" : "Connecting…"}
    </div>
  );
}

function EmptyState({ onNew }: { onNew: () => void }) {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-4 text-center">
      <motion.div
        initial={{ scale: 0, rotate: -180 }}
        animate={{ scale: 1, rotate: 0 }}
        transition={{ type: "spring", stiffness: 200, damping: 20 }}
        className="grid h-20 w-20 place-items-center rounded-3xl bg-brand-gradient text-white shadow-glow"
      >
        <MessageCircle className="h-10 w-10" strokeWidth={1.8} />
      </motion.div>
      <div>
        <h3 className="text-xl font-bold text-foreground">
          Start a conversation
        </h3>
        <p className="mt-2 max-w-xs text-sm leading-relaxed text-muted">
          Your messages are end-to-end encrypted. Only you can read them.
        </p>
      </div>
      <motion.button
        whileHover={{ scale: 1.03 }}
        whileTap={{ scale: 0.97 }}
        onClick={onNew}
        className="mt-2 flex items-center gap-2 rounded-brand bg-brand-gradient px-6 py-3 text-sm font-bold text-white shadow-glow"
      >
        <Plus className="h-4 w-4" />
        New conversation
      </motion.button>
    </div>
  );
}

function ChatWelcome({ onSelectPrompt }: { onSelectPrompt: (text: string) => void }) {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-5 text-center">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1 }}
        className="grid h-16 w-16 place-items-center rounded-3xl bg-brand-gradient text-white shadow-glow"
      >
        <Sparkles className="h-8 w-8" strokeWidth={1.8} />
      </motion.div>
      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.2 }}
      >
        <h3 className="text-lg font-bold text-foreground">
          How are you feeling today?
        </h3>
        <p className="mt-2 max-w-sm text-sm leading-relaxed text-muted">
          Share whatever is on your mind. This is a safe, private space — your
          messages are encrypted and only visible to you.
        </p>
      </motion.div>
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.4 }}
        className="flex flex-wrap justify-center gap-2"
      >
        {[
          "I'm feeling anxious",
          "I can't sleep well",
          "Just need to talk",
          "I feel overwhelmed and stuck",
          "Help me calm down right now",
        ].map((prompt) => (
          <button
            key={prompt}
            onClick={() => onSelectPrompt(prompt)}
            className="rounded-full border border-border/50 bg-surface/50 px-3.5 py-1.5 text-xs font-medium text-muted transition hover:border-secondary/40 hover:text-foreground hover:bg-surface/80"
          >
            {prompt}
          </button>
        ))}
      </motion.div>
    </div>
  );
}

function formatDate(iso: string): string {
  try {
    const d = new Date(iso);
    const now = new Date();
    const isToday = d.toDateString() === now.toDateString();
    if (isToday) return "Today";
    const yesterday = new Date(now);
    yesterday.setDate(yesterday.getDate() - 1);
    if (d.toDateString() === yesterday.toDateString()) return "Yesterday";
    return d.toLocaleDateString(undefined, { month: "short", day: "numeric" });
  } catch {
    return "";
  }
}
