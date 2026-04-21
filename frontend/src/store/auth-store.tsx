import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import { setAccessToken } from "../services/api";
import {
  anonymousLogin,
  loginUser,
  logoutUser,
  refreshToken,
  registerUser,
  sendOtp,
  verifyOtp,
  type AuthUser,
} from "../services/auth";

type AuthState =
  | { status: "loading" }
  | { status: "unauthenticated" }
  | { status: "authenticated"; user: AuthUser };

type AuthContextValue = {
  state: AuthState;
  login: (email: string, password: string) => Promise<void>;
  register: (fullName: string, email: string, password: string) => Promise<void>;
  loginAnonymous: () => Promise<void>;
  loginWithOtp: (email: string, code: string, fullName?: string) => Promise<void>;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: "loading" });

  const handleAuthSuccess = useCallback(
    (newAccessToken: string, user: AuthUser) => {
      setAccessToken(newAccessToken);
      setState({ status: "authenticated", user });
    },
    []
  );

  // Initial load: restore session securely via HttpOnly token (browser attached)
  useEffect(() => {
    let active = true;

    async function initSession() {
      try {
        const res = await refreshToken();
        if (active) {
          handleAuthSuccess(res.accessToken, res.user);
        }
      } catch {
        if (active) {
          setState({ status: "unauthenticated" });
        }
      }
    }

    initSession();
    return () => { active = false; };
  }, [handleAuthSuccess]);

  const login = useCallback(
    async (email: string, password: string) => {
      const res = await loginUser(email, password);
      handleAuthSuccess(res.accessToken, res.user);
    },
    [handleAuthSuccess]
  );

  const register = useCallback(
    async (fullName: string, email: string, password: string) => {
      const res = await registerUser(fullName, email, password);
      handleAuthSuccess(res.accessToken, res.user);
    },
    [handleAuthSuccess]
  );

  const loginAnonymous = useCallback(async () => {
    const res = await anonymousLogin();
    handleAuthSuccess(res.accessToken, res.user);
  }, [handleAuthSuccess]);

  const loginWithOtp = useCallback(async (email: string, code: string, fullName?: string) => {
    const res = await verifyOtp(email, code, fullName);
    handleAuthSuccess(res.accessToken, res.user);
  }, [handleAuthSuccess]);

  const logout = useCallback(async () => {
    try {
      await logoutUser();
    } catch {
      // Best effort backend logout.
    } finally {
      setAccessToken(null);
      setState({ status: "unauthenticated" });
    }
  }, []);

  const value = useMemo(
    () => ({ state, login, register, loginAnonymous, loginWithOtp, logout }),
    [state, login, register, loginAnonymous, loginWithOtp, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return ctx;
}
