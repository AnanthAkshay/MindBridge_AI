import { apiFetch } from "./api";

export type AuthUser = {
  id: number;
  email: string | null;
  fullName: string;
  role: string;
  anonymous: boolean;
};

export type AuthResponse = {
  accessToken: string;
  refreshToken: string;
  user: AuthUser;
};

export async function registerUser(
  fullName: string,
  email: string,
  password: string
): Promise<AuthResponse> {
  return apiFetch<AuthResponse>("/api/auth/register", {
    method: "POST",
    body: { fullName, email, password },
  });
}

export async function loginUser(
  email: string,
  password: string
): Promise<AuthResponse> {
  return apiFetch<AuthResponse>("/api/auth/login", {
    method: "POST",
    body: { email, password },
  });
}

export async function anonymousLogin(): Promise<AuthResponse> {
  return apiFetch<AuthResponse>("/api/auth/anonymous", {
    method: "POST",
  });
}

export async function refreshToken(): Promise<AuthResponse> {
  return apiFetch<AuthResponse>(
    "/api/auth/refresh",
    { method: "POST" },
    false // Don't retry on 401 — would cause infinite loop
  );
}

export async function sendOtp(email: string): Promise<{ message: string }> {
  return apiFetch<{ message: string }>("/api/auth/otp/send", {
    method: "POST",
    body: { email },
  });
}

export async function verifyOtp(email: string, code: string, fullName?: string): Promise<AuthResponse> {
  return apiFetch<AuthResponse>("/api/auth/otp/verify", {
    method: "POST",
    body: { email, code, fullName },
  });
}

export async function logoutUser(): Promise<void> {
  await apiFetch("/api/auth/logout", {
    method: "POST"
  });
}
