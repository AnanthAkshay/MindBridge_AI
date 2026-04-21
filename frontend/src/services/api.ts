const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

type RequestOptions = Omit<RequestInit, "body"> & {
  body?: unknown;
};

/**
 * Singleton token holder — access token lives in memory only (not localStorage).
 * This prevents XSS from stealing it.
 */
let accessToken: string | null = null;

export function setAccessToken(token: string | null) {
  accessToken = token;
}

export function getAccessToken(): string | null {
  return accessToken;
}

/**
 * Authenticated fetch wrapper.
 * - Attaches Bearer token automatically
 * - On 401, attempts silent refresh then retries the original request once
 */
export async function apiFetch<T = unknown>(
  endpoint: string,
  options: RequestOptions = {},
  retry = true
): Promise<T> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    Accept: "application/json",
    ...(options.headers as Record<string, string>),
  };

  if (accessToken) {
    headers["Authorization"] = `Bearer ${accessToken}`;
  }

  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers,
    credentials: "include", // Essential for HttpOnly cookies
    body: options.body ? JSON.stringify(options.body) : undefined,
  });

  // On 401, try silent refresh
  if (response.status === 401 && retry) {
    const refreshed = await attemptSilentRefresh();
    if (refreshed) {
      return apiFetch<T>(endpoint, options, false);
    }
  }

  if (!response.ok) {
    const error = await response.json().catch(() => ({
      message: `Request failed with status ${response.status}`,
    }));
    throw new ApiError(
      error.message || `Request failed`,
      response.status,
      error
    );
  }

  // Handle empty responses (204 No Content)
  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

// Global lock to prevent multiple concurrent refresh calls
let refreshPromise: Promise<boolean> | null = null;

async function attemptSilentRefresh(): Promise<boolean> {
  if (refreshPromise) {
    return refreshPromise;
  }

  refreshPromise = (async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include", // Send the HttpOnly cookie
      });

      if (!response.ok) {
        accessToken = null;
        return false;
      }

      const data = await response.json();
      accessToken = data.accessToken;
      // Refresh token is now managed by the browser (Set-Cookie)
      return true;
    } catch {
      return false;
    } finally {
      refreshPromise = null;
    }
  })();

  return refreshPromise;
}

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly data: unknown
  ) {
    super(message);
    this.name = "ApiError";
  }
}
