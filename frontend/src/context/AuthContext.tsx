import { createContext, useContext, useState, useEffect, type ReactNode } from "react";
import { getMyProfile } from "../api/client";
import type { Profile } from "../types";

interface AuthCtx {
    token: string | null;
    profile: Profile | null;
    loading: boolean;
    setAuth: (token: string, profile?: Profile | null) => void;
    refreshProfile: () => Promise<void>;
    logout: () => void;
}

const AuthContext = createContext<AuthCtx>({
    token: null,
    profile: null,
    loading: true,
    setAuth: () => { },
    refreshProfile: async () => { },
    logout: () => { },
});

export function AuthProvider({ children }: { children: ReactNode }) {
    const [token, setToken] = useState<string | null>(() => localStorage.getItem("token"));
    const [profile, setProfile] = useState<Profile | null>(null);
    const [loading, setLoading] = useState(true);

    const fetchProfile = async () => {
        if (!token) {
            setLoading(false);
            return;
        }
        try {
            const data = await getMyProfile();
            setProfile(data as unknown as Profile);
        } catch {
            // token invalid – clear
            localStorage.removeItem("token");
            setToken(null);
            setProfile(null);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchProfile();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [token]);

    const setAuth = (t: string, p?: Profile | null) => {
        localStorage.setItem("token", t);
        setToken(t);
        if (p) setProfile(p);
    };

    const refreshProfile = async () => {
        await fetchProfile();
    };

    const logout = () => {
        localStorage.removeItem("token");
        setToken(null);
        setProfile(null);
    };

    return (
        <AuthContext.Provider value={{ token, profile, loading, setAuth, refreshProfile, logout }}>
            {children}
        </AuthContext.Provider>
    );
}

export const useAuth = () => useContext(AuthContext);
