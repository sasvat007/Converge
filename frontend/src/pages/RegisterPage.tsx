import { useState, type FormEvent, useRef, type DragEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { register as apiRegister, fileToBase64 } from "../api/client";
import { useAuth } from "../context/AuthContext";
import { useToast } from "../context/ToastContext";
import { UserPlus, Upload } from "lucide-react";

export default function RegisterPage() {
    const [form, setForm] = useState({
        email: "",
        password: "",
        name: "",
        year: "",
        department: "",
        institution: "",
        availability: "medium",
        resumeText: "",
    });
    const [pdfFile, setPdfFile] = useState<File | null>(null);
    const [busy, setBusy] = useState(false);
    const [dragOver, setDragOver] = useState(false);
    const fileRef = useRef<HTMLInputElement>(null);
    const { setAuth } = useAuth();
    const { addToast } = useToast();
    const navigate = useNavigate();

    const set = (key: string, val: string) => setForm((p) => ({ ...p, [key]: val }));

    const handleFile = (file: File) => {
        if (file.type !== "application/pdf") {
            addToast("Please upload a PDF file", "error");
            return;
        }
        setPdfFile(file);
        // extract text from the file name as placeholder
        addToast(`Resume "${file.name}" attached`, "success");
    };

    const handleDrop = (e: DragEvent) => {
        e.preventDefault();
        setDragOver(false);
        const file = e.dataTransfer.files?.[0];
        if (file) handleFile(file);
    };

    const handleSubmit = async (e: FormEvent) => {
        e.preventDefault();
        if (!form.email || !form.password || !form.resumeText) {
            addToast("Email, password and resume text are required", "error");
            return;
        }
        setBusy(true);
        try {
            let resumePdf: string | null = null;
            if (pdfFile) resumePdf = await fileToBase64(pdfFile);

            const data = await apiRegister({
                ...form,
                resumePdf,
            });

            setAuth(data.token, data.profile as any);
            addToast("Account created successfully!", "success");
            navigate("/dashboard");
        } catch (err: any) {
            addToast(err?.message || "Registration failed", "error");
        } finally {
            setBusy(false);
        }
    };

    return (
        <div className="auth-layout">
            <div className="auth-left">
                <div className="auth-card" style={{ animation: "fadeIn 0.5s ease", maxWidth: 480 }}>
                    <h1>Join Converge</h1>
                    <p className="subtitle">Create your profile and start finding collaborators</p>

                    <form className="auth-form" onSubmit={handleSubmit}>
                        <div className="form-row">
                            <div className="form-group">
                                <label className="form-label" htmlFor="reg-name">Full Name</label>
                                <input
                                    id="reg-name"
                                    className="form-input"
                                    placeholder="John Doe"
                                    value={form.name}
                                    onChange={(e) => set("name", e.target.value)}
                                />
                            </div>
                            <div className="form-group">
                                <label className="form-label" htmlFor="reg-email">Email *</label>
                                <input
                                    id="reg-email"
                                    className="form-input"
                                    type="email"
                                    placeholder="you@university.edu"
                                    value={form.email}
                                    onChange={(e) => set("email", e.target.value)}
                                    required
                                />
                            </div>
                        </div>

                        <div className="form-group">
                            <label className="form-label" htmlFor="reg-password">Password *</label>
                            <input
                                id="reg-password"
                                className="form-input"
                                type="password"
                                placeholder="Create a strong password"
                                value={form.password}
                                onChange={(e) => set("password", e.target.value)}
                                required
                                autoComplete="new-password"
                            />
                        </div>

                        <div className="form-row">
                            <div className="form-group">
                                <label className="form-label" htmlFor="reg-year">Year</label>
                                <select
                                    id="reg-year"
                                    className="form-select"
                                    value={form.year}
                                    onChange={(e) => set("year", e.target.value)}
                                >
                                    <option value="">Select year</option>
                                    <option value="1st Year">1st Year</option>
                                    <option value="2nd Year">2nd Year</option>
                                    <option value="3rd Year">3rd Year</option>
                                    <option value="4th Year">4th Year</option>
                                    <option value="Graduate">Graduate</option>
                                </select>
                            </div>
                            <div className="form-group">
                                <label className="form-label" htmlFor="reg-dept">Department</label>
                                <input
                                    id="reg-dept"
                                    className="form-input"
                                    placeholder="Computer Science"
                                    value={form.department}
                                    onChange={(e) => set("department", e.target.value)}
                                />
                            </div>
                        </div>

                        <div className="form-row">
                            <div className="form-group">
                                <label className="form-label" htmlFor="reg-institution">Institution</label>
                                <input
                                    id="reg-institution"
                                    className="form-input"
                                    placeholder="MIT"
                                    value={form.institution}
                                    onChange={(e) => set("institution", e.target.value)}
                                />
                            </div>
                            <div className="form-group">
                                <label className="form-label" htmlFor="reg-availability">Availability</label>
                                <select
                                    id="reg-availability"
                                    className="form-select"
                                    value={form.availability}
                                    onChange={(e) => set("availability", e.target.value)}
                                >
                                    <option value="low">Low</option>
                                    <option value="medium">Medium</option>
                                    <option value="high">High</option>
                                </select>
                            </div>
                        </div>

                        <div className="form-group">
                            <label className="form-label" htmlFor="reg-resume">Resume Text *</label>
                            <textarea
                                id="reg-resume"
                                className="form-textarea"
                                placeholder="Paste your resume content here... This will be parsed by AI to build your profile."
                                value={form.resumeText}
                                onChange={(e) => set("resumeText", e.target.value)}
                                rows={4}
                                required
                            />
                        </div>

                        <div className="form-group">
                            <label className="form-label">Resume PDF (optional)</label>
                            <div
                                className={`file-drop-zone ${dragOver ? "drag-active" : ""}`}
                                onClick={() => fileRef.current?.click()}
                                onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
                                onDragLeave={() => setDragOver(false)}
                                onDrop={handleDrop}
                                style={dragOver ? { borderColor: "var(--accent)", background: "var(--accent-bg)" } : {}}
                            >
                                <Upload />
                                <p>{pdfFile ? pdfFile.name : "Click or drag PDF here"}</p>
                                <p className="hint">Max 10 MB</p>
                                <input
                                    ref={fileRef}
                                    type="file"
                                    accept="application/pdf"
                                    style={{ display: "none" }}
                                    onChange={(e) => e.target.files?.[0] && handleFile(e.target.files[0])}
                                />
                            </div>
                        </div>

                        <button className="btn btn-primary btn-lg" type="submit" disabled={busy}>
                            <UserPlus size={18} />
                            {busy ? "Creating account…" : "Create Account"}
                        </button>
                    </form>

                    <p className="auth-switch">
                        Already have an account? <Link to="/login">Sign in</Link>
                    </p>
                </div>
            </div>

            <div className="auth-right">
                <div className="auth-hero-text">
                    <h2>Build Together, Grow Together</h2>
                    <p>
                        Upload your resume, let AI parse your skills, and find the perfect
                        teammates for your next big project.
                    </p>
                </div>
            </div>
        </div>
    );
}
