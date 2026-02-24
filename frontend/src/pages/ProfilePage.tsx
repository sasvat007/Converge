import { useEffect, useState } from "react";
import { useAuth } from "../context/AuthContext";
import { useToast } from "../context/ToastContext";
import { resumeDownloadUrl } from "../api/client";
import type { ParsedResume } from "../types";
import {
    GraduationCap, Building2, Clock, Download, Code, Wrench,
    Lightbulb, Trophy, Briefcase, Target,
} from "lucide-react";

export default function ProfilePage() {
    const { profile } = useAuth();
    const { addToast } = useToast();
    const [resume, setResume] = useState<ParsedResume | null>(null);

    useEffect(() => {
        if (profile?.Resume) {
            try {
                setResume(JSON.parse(profile.Resume));
            } catch {
                addToast("Could not parse resume data", "error");
            }
        }
    }, [profile]);

    if (!profile) return <div className="spinner-overlay"><div className="spinner" /></div>;

    const initials = profile.name?.split(" ").map((w) => w[0]).join("").slice(0, 2).toUpperCase() || "?";

    const Section = ({ title, icon, children }: { title: string; icon: React.ReactNode; children: React.ReactNode }) => (
        <div className="profile-section">
            <h3>{icon} {title}</h3>
            {children}
        </div>
    );

    return (
        <div className="page-container">
            <div className="page-header"><h1>My Profile</h1></div>

            {/* Hero */}
            <div className="profile-hero">
                <div className="profile-avatar-lg">{initials}</div>
                <div className="profile-info">
                    <h2>{profile.name || "Unknown"}</h2>
                    <p className="email">{profile.email}</p>
                    <div className="profile-meta">
                        {profile.department && <span className="profile-meta-item"><GraduationCap size={14} />{profile.department}</span>}
                        {profile.year && <span className="profile-meta-item"><Clock size={14} />{profile.year}</span>}
                        {profile.institution && <span className="profile-meta-item"><Building2 size={14} />{profile.institution}</span>}
                        {profile.availability && (
                            <span className={`badge ${profile.availability === "high" ? "badge-success" : profile.availability === "medium" ? "badge-warning" : "badge-danger"}`}>
                                {profile.availability} availability
                            </span>
                        )}
                    </div>
                    {profile.id && (
                        <a href={resumeDownloadUrl(profile.id)} target="_blank" rel="noopener noreferrer" className="btn btn-secondary btn-sm" style={{ marginTop: "0.75rem" }}>
                            <Download size={14} /> Download Resume
                        </a>
                    )}
                </div>
            </div>

            {/* Resume Sections */}
            {resume && (
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "1rem" }}>
                    {resume.skills && (
                        <Section title="Skills" icon={<Code size={18} />}>
                            {resume.skills.programming_languages?.length ? (
                                <div style={{ marginBottom: "0.75rem" }}>
                                    <p className="form-label" style={{ marginBottom: "0.375rem" }}>Languages</p>
                                    <div className="tag-list">{resume.skills.programming_languages.map((s) => <span key={s} className="tag">{s}</span>)}</div>
                                </div>
                            ) : null}
                            {resume.skills.frameworks_libraries?.length ? (
                                <div style={{ marginBottom: "0.75rem" }}>
                                    <p className="form-label" style={{ marginBottom: "0.375rem" }}>Frameworks</p>
                                    <div className="tag-list">{resume.skills.frameworks_libraries.map((s) => <span key={s} className="tag">{s}</span>)}</div>
                                </div>
                            ) : null}
                            {resume.skills.tools_platforms?.length ? (
                                <div>
                                    <p className="form-label" style={{ marginBottom: "0.375rem" }}>Tools</p>
                                    <div className="tag-list">{resume.skills.tools_platforms.map((s) => <span key={s} className="tag">{s}</span>)}</div>
                                </div>
                            ) : null}
                        </Section>
                    )}

                    {resume.interests && (
                        <Section title="Interests" icon={<Lightbulb size={18} />}>
                            {resume.interests.technical?.length ? (
                                <div style={{ marginBottom: "0.75rem" }}>
                                    <p className="form-label" style={{ marginBottom: "0.375rem" }}>Technical</p>
                                    <div className="tag-list">{resume.interests.technical.map((s) => <span key={s} className="tag">{s}</span>)}</div>
                                </div>
                            ) : null}
                            {resume.interests.learning_goals?.length ? (
                                <div>
                                    <p className="form-label" style={{ marginBottom: "0.375rem" }}>Learning Goals</p>
                                    <div className="tag-list">{resume.interests.learning_goals.map((s) => <span key={s} className="tag">{s}</span>)}</div>
                                </div>
                            ) : null}
                        </Section>
                    )}

                    {resume.projects && resume.projects.length > 0 && (
                        <Section title="Past Projects" icon={<Briefcase size={18} />}>
                            <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
                                {resume.projects.slice(0, 5).map((p, i) => (
                                    <div key={i} style={{ padding: "0.75rem", background: "var(--bg-elevated)", borderRadius: "var(--radius-md)" }}>
                                        <p style={{ fontWeight: 600, fontSize: "0.85rem" }}>{p.title}</p>
                                        {p.description && <p style={{ fontSize: "0.8rem", color: "var(--text-secondary)", marginTop: "0.25rem" }}>{p.description}</p>}
                                        {p.technologies?.length ? <div className="tag-list" style={{ marginTop: "0.375rem" }}>{p.technologies.map((t) => <span key={t} className="tag">{t}</span>)}</div> : null}
                                    </div>
                                ))}
                            </div>
                        </Section>
                    )}

                    {resume.achievements && (
                        <Section title="Achievements" icon={<Trophy size={18} />}>
                            {resume.achievements.hackathons?.length ? (
                                <div style={{ marginBottom: "0.75rem" }}>
                                    <p className="form-label" style={{ marginBottom: "0.375rem" }}>Hackathons</p>
                                    <ul style={{ paddingLeft: "1.25rem", color: "var(--text-secondary)", fontSize: "0.85rem" }}>
                                        {resume.achievements.hackathons.map((h) => <li key={h}>{h}</li>)}
                                    </ul>
                                </div>
                            ) : null}
                            {resume.achievements.certifications?.length ? (
                                <div>
                                    <p className="form-label" style={{ marginBottom: "0.375rem" }}>Certifications</p>
                                    <div className="tag-list">{resume.achievements.certifications.map((c) => <span key={c} className="tag">{c}</span>)}</div>
                                </div>
                            ) : null}
                        </Section>
                    )}
                </div>
            )}

            {!resume && (
                <div className="empty-state">
                    <Target />
                    <h3>No resume data yet</h3>
                    <p>Your AI-parsed resume data will appear here after you upload a resume</p>
                </div>
            )}
        </div>
    );
}
