import { createContext, useCallback, useContext, useMemo, useState } from "react";
import type { LoginRequest, RegisterRequest, UserResponse } from "../api/client";
import { ApiClient } from "../api/client";

const TOKEN_STORAGE_KEY = "lease-track.auth-token";
const USER_STORAGE_KEY = "lease-track.auth-user";

interface AuthState {
  token: string | null;
  user: UserResponse | null;
}

interface AuthContextValue extends AuthState {
  api: ApiClient;
  isAuthenticated: boolean;
  login: (request: LoginRequest) => Promise<void>;
  register: (request: RegisterRequest) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<AuthState>(() => ({
    token: window.sessionStorage.getItem(TOKEN_STORAGE_KEY),
    user: readStoredUser()
  }));

  const api = useMemo(
    () =>
      new ApiClient({
        getToken: () => window.sessionStorage.getItem(TOKEN_STORAGE_KEY)
      }),
    []
  );

  const setSession = useCallback((token: string, user: UserResponse | null) => {
    window.sessionStorage.setItem(TOKEN_STORAGE_KEY, token);
    if (user) {
      window.sessionStorage.setItem(USER_STORAGE_KEY, JSON.stringify(user));
    }
    setState({ token, user });
  }, []);

  const login = useCallback(
    async (request: LoginRequest) => {
      const response = await api.login(request);
      const existingUser = readStoredUser();
      setSession(response.accessToken, existingUser);
    },
    [api, setSession]
  );

  const register = useCallback(
    async (request: RegisterRequest) => {
      const user = await api.register(request);
      const response = await api.login({
        email: request.email,
        password: request.password
      });
      setSession(response.accessToken, user);
    },
    [api, setSession]
  );

  const logout = useCallback(() => {
    window.sessionStorage.removeItem(TOKEN_STORAGE_KEY);
    window.sessionStorage.removeItem(USER_STORAGE_KEY);
    setState({ token: null, user: null });
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      ...state,
      api,
      isAuthenticated: Boolean(state.token),
      login,
      register,
      logout
    }),
    [api, login, logout, register, state]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used inside AuthProvider");
  }
  return context;
}

function readStoredUser(): UserResponse | null {
  const raw = window.sessionStorage.getItem(USER_STORAGE_KEY);
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as UserResponse;
  } catch {
    window.sessionStorage.removeItem(USER_STORAGE_KEY);
    return null;
  }
}
