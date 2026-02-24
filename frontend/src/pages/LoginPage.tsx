import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { login as apiLogin } from "../api/client";
import { useAuth } from "../context/AuthContext";
import { useToast } from "../context/ToastContext";
import { LogIn } from "lucide-react";

export default function LoginPage() {
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [busy, setBusy] = useState(false);
    const { setAuth, logout } = useAuth();
    const { addToast } = useToast();
    const navigate = useNavigate();

    // Clear any stale token when landing on login page
    useEffect(() => { logout(); }, []);

    const handleSubmit = async (e: FormEvent) => {
        e.preventDefault();
        if (!email || !password) {
            addToast("Please fill in all fields", "error");
            return;
        }
        setBusy(true);
        try {
            const data = await apiLogin(email, password);
            setAuth(data.token, data.profile as any);
            addToast("Welcome back!", "success");
            navigate("/dashboard");
        } catch (err: any) {
            addToast(err?.message || "Login failed — check your credentials", "error");
        } finally {
            setBusy(false);
        }
    };

    return (
        <div className="auth-layout">
            <div className="auth-left">
                <div className="auth-card" style={{ animation: "fadeIn 0.5s ease" }}>
                    <h1>Welcome back</h1>
                    <p className="subtitle">Sign in to continue building with your team</p>

                    <form className="auth-form" onSubmit={handleSubmit}>
                        <div className="form-group">
                            <label className="form-label" htmlFor="login-email">Email</label>
                            <input
                                id="login-email"
                                className="form-input"
                                type="email"
                                placeholder="you@university.edu"
                                value={email}
                                onChange={(e) => setEmail(e.target.value)}
                                autoComplete="email"
                            />
                        </div>
                        <div className="form-group">
                            <label className="form-label" htmlFor="login-password">Password</label>
                            <input
                                id="login-password"
                                className="form-input"
                                type="password"
                                placeholder="••••••••"
                                value={password}
                                onChange={(e) => setPassword(e.target.value)}
                                autoComplete="current-password"
                            />
                        </div>
                        <button className="btn btn-primary btn-lg" type="submit" disabled={busy}>
                            <LogIn size={18} />
                            {busy ? "Signing in…" : "Sign In"}
                        </button>
                    </form>

                    <p className="auth-switch">
                        Don't have an account? <Link to="/register">Create one</Link>
                    </p>
                </div>
            </div>

            <div className="auth-right">
                <div className="auth-hero-text">
                    <h2>Find Your Perfect Project Buddy</h2>
                    <p>
                        Converge connects you with skilled collaborators. Upload your resume,
                        explore projects, and build something amazing together.
                    </p>
                </div>
            </div>
        </div>
    );
}
