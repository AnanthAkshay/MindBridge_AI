import { Client, type IMessage } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { getAccessToken } from "./api";

const WS_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

export type ChatMessage = {
  id: number;
  sessionId: number;
  senderType: "USER" | "AI";
  content: string;
  emotion?: string;
  emotionScore?: number;
  createdAt: string;
};

export type TypingEvent = {
  sessionId: number;
  userId: number;
  fullName: string;
  typing: boolean;
  timestamp: string;
};

export type StreamDelta = {
  messageId: string;
  content: string;
  done: boolean;
};

type MessageHandler = (msg: ChatMessage) => void;
type TypingHandler = (event: TypingEvent) => void;
type StreamHandler = (delta: StreamDelta) => void;

let stompClient: Client | null = null;
let reconnectAttempts = 0;
const MAX_RECONNECT_DELAY = 30000;

/**
 * Connect to the WebSocket STOMP server.
 * JWT is passed as a query param for the SockJS handshake
 * and as a STOMP CONNECT header for protocol-level auth.
 */
export function connectWebSocket(): Client {
  if (stompClient?.connected) {
    return stompClient;
  }

  const token = getAccessToken();

  const client = new Client({
    webSocketFactory: () => {
      const url = `${WS_BASE_URL}/ws/chat?token=${encodeURIComponent(token ?? "")}`;
      return new SockJS(url) as WebSocket;
    },
    connectHeaders: {
      Authorization: `Bearer ${token}`,
    },
    reconnectDelay: 0, // We handle reconnection manually
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      reconnectAttempts = 0;
      console.log("[WS] Connected to MindBridge chat");
    },
    onDisconnect: () => {
      console.log("[WS] Disconnected");
    },
    onStompError: (frame) => {
      console.error("[WS] STOMP Error:", frame.headers["message"]);
      scheduleReconnect();
    },
    onWebSocketClose: () => {
      console.warn("[WS] WebSocket closed, scheduling reconnect...");
      scheduleReconnect();
    },
  });

  client.activate();
  stompClient = client;
  return client;
}

/**
 * Exponential backoff reconnect with cap.
 */
function scheduleReconnect() {
  const delay = Math.min(1000 * Math.pow(2, reconnectAttempts), MAX_RECONNECT_DELAY);
  reconnectAttempts++;
  console.log(`[WS] Reconnect attempt ${reconnectAttempts} in ${delay}ms`);
  setTimeout(() => {
    if (stompClient && !stompClient.connected) {
      // Refresh the token before reconnecting
      const token = getAccessToken();
      if (token) {
        stompClient.connectHeaders = { Authorization: `Bearer ${token}` };
        stompClient.activate();
      }
    }
  }, delay);
}

/**
 * Subscribe to a chat session's messages.
 */
export function subscribeToSession(
  sessionId: number,
  onMessage: MessageHandler,
  onTyping?: TypingHandler,
  onStream?: StreamHandler
): { unsubscribe: () => void } {
  const client = stompClient;
  if (!client?.connected) {
    throw new Error("WebSocket not connected");
  }

  const msgSub = client.subscribe(
    `/topic/session.${sessionId}`,
    (frame: IMessage) => {
      const msg: ChatMessage = JSON.parse(frame.body);
      onMessage(msg);
    }
  );

  let typeSub: { unsubscribe: () => void } | null = null;
  if (onTyping) {
    typeSub = client.subscribe(
      `/topic/session.${sessionId}.typing`,
      (frame: IMessage) => {
        const event: TypingEvent = JSON.parse(frame.body);
        onTyping(event);
      }
    );
  }

  let streamSub: { unsubscribe: () => void } | null = null;
  if (onStream) {
    streamSub = client.subscribe(
      `/topic/session.${sessionId}.stream`,
      (frame: IMessage) => {
        const delta: StreamDelta = JSON.parse(frame.body);
        onStream(delta);
      }
    );
  }

  return {
    unsubscribe: () => {
      msgSub.unsubscribe();
      typeSub?.unsubscribe();
      streamSub?.unsubscribe();
    },
  };
}

/**
 * Send a chat message via STOMP.
 */
export function sendChatMessage(sessionId: number, content: string, useMemory = true) {
  if (!stompClient?.connected) {
    throw new Error("WebSocket not connected");
  }

  stompClient.publish({
    destination: "/app/chat.send",
    body: JSON.stringify({ sessionId, content, useMemory }),
  });
}

/**
 * Send a typing indicator via STOMP.
 */
export function sendTypingIndicator(sessionId: number, typing: boolean) {
  if (!stompClient?.connected) return;

  stompClient.publish({
    destination: "/app/chat.typing",
    body: JSON.stringify({ sessionId, typing }),
  });
}

/**
 * Disconnect the WebSocket client.
 */
export function disconnectWebSocket() {
  if (stompClient) {
    stompClient.deactivate();
    stompClient = null;
    reconnectAttempts = 0;
  }
}

/**
 * Check if WebSocket is currently connected.
 */
export function isWebSocketConnected(): boolean {
  return stompClient?.connected ?? false;
}

let escalationsSub: { unsubscribe: () => void } | null = null;
let riskAlertsSub: { unsubscribe: () => void } | null = null;

export const websocketService = {
  subscribeToEscalations: (onEscalation: (escalationId: number) => void) => {
    if (!stompClient?.connected) return;
    escalationsSub = stompClient.subscribe("/topic/escalations", (frame: IMessage) => {
      onEscalation(Number(frame.body));
    });
  },
  unsubscribeFromEscalations: () => {
    escalationsSub?.unsubscribe();
    escalationsSub = null;
  },
  subscribeToRiskAlerts: (userId: number, onRiskAlert: (alert: any) => void) => {
    if (!stompClient?.connected) return;
    riskAlertsSub = stompClient.subscribe(`/topic/user.${userId}.risk`, (frame: IMessage) => {
      onRiskAlert(JSON.parse(frame.body));
    });
  },
  unsubscribeFromRiskAlerts: (userId: number) => {
    riskAlertsSub?.unsubscribe();
    riskAlertsSub = null;
  }
};
