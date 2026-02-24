import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import {
    getProjectById,
    completeProject,
    sendTeammateRequest,
} from "../api/client";
import { useAuth } from "../context/AuthContext";
import { useToast } from "../context/ToastContext";
import type { Project } from "../types";
import {
    ArrowLeft,
    ExternalLink,
    Users,
    CheckCircle2,
    UserPlus,
    Calendar,
    GitBranch,
    Globe,
    Lock,
} from "lucide-react";

export default function ProjectDetailPage() {
    const { id } = useParams<{ id: string }>();
    const { profile } = useAuth();
    const { addToast } = useToast();
    const navigate = useNavigate();
    const [project, setProject] = useState<Project | null>(null);
    const [loading, setLoading] = useState(true);
    const [inviteEmail, setInviteEmail] = useState("");
    const [inviting, setInviting] = useState(false);
    const [completing, setCompleting] = useState(false);
    const [showInvite, setShowInvite] = useState(false);

    useEffect(() => {
        if (!id) return;
        getProjectById(Number(id))
            .then((p) => setProject(p as unknown as Project))
            .catch(() => addToast("Project not found", "error"))
            .finally(() => setLoading(false));
    }, [id]);

    if (loading) {
        return (
            <div className="spinner-overlay">
                <div className="spinner" />
            </div>
        );
    }

    if (!project) {
        return (
            <div className="page-container">
                <div className="empty-state">
                    <h3>Project not found</h3>
                    <button className="btn btn-primary" onClick={() => navigate("/dashboard")}>
                        Back to Dashboard
                    </button>
                </div>
            </div>
        );
    }

    const isOwner = profile?.email === project.email;
    const skills = project.requiredSkills?.split(",").map((s) => s.trim()).filter(Boolean) || [];
    const tech = project.preferredTechnologies?.split(",").map((s) => s.trim()).filter(Boolean) || [];
    const domains = project.domain?.split(",").map((s) => s.trim()).filter(Boolean) || [];

    const handleInvite = async () => {
        if (!inviteEmail.trim()) return;
        setInviting(true);
        try {
            await sendTeammateRequest(project.id, inviteEmail.trim());
            addToast("Invitation sent!", "success");
            setInviteEmail("");
            setShowInvite(false);
        } catch (err: any) {
            addToast(err?.message || "Failed to send invitation", "error");
        } finally {
            setInviting(false);
        }
    };

    const handleComplete = async () => {
        setCompleting(true);
        try {
            await completeProject(project.id);
            setProject((p) => (p ? { ...p, status: "COMPLETED" } : p));
            addToast("Project marked as completed 🎉", "success");
        } catch (err: any) {
            addToast(err?.message || "Failed to complete project", "error");
        } finally {
            setCompleting(false);
        }
    };

    return (
        <div className="page-container">
            <button
                className="btn btn-ghost btn-sm"
                onClick={() => navigate(-1)}
                style={{ marginBottom: "1rem" }}
            >
                <ArrowLeft size={16} /> Back
            </button>

            {/* header */}
            <div className="project-detail-header">
                <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: "1rem", flexWrap: "wrap" }}>
                    <div>
                        <h1>{project.title}</h1>
                        <p style={{ color: "var(--text-secondary)", fontSize: "0.875rem" }}>
                            Posted by {project.email}
                        </p>
                    </div>
                    <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
                        <span className={`badge ${project.status === "ACTIVE" ? "badge-success" : "badge-neutral"}`}>
                            {project.status}
                        </span>
                        <span className="badge badge-accent">{project.type}</span>
                        <span className="badge badge-info">
                            {project.visibility === "public" ? <Globe size={12} /> : <Lock size={12} />}
                            <span style={{ marginLeft: 4 }}>{project.visibility}</span>
                        </span>
                    </div>
                </div>
                <div className="project-detail-meta">
                    <div className="project-card-meta">
                        <Calendar size={14} />
                        {new Date(project.createdAt).toLocaleDateString("en-US", {
                            year: "numeric", month: "long", day: "numeric"
                        })}
                    </div>
                    {project.githubRepo && (
                        <a
                            href={project.githubRepo}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="btn btn-ghost btn-sm"
                        >
                            <GitBranch size={14} /> GitHub <ExternalLink size={12} />
                        </a>
                    )}
                </div>
            </div>

            {/* body */}
            <div className="project-detail-body">
                <div>
                    {/* description */}
                    {project.description && (
                        <div className="profile-section">
                            <h3>📝 Description</h3>
                            <p style={{ color: "var(--text-secondary)", lineHeight: 1.7 }}>
                                {project.description}
                            </p>
                        </div>
                    )}

                    {/* skills */}
                    <div className="profile-section">
                        <h3>🛠 Required Skills</h3>
                        <div className="tag-list">
                            {skills.map((s) => (
                                <span key={s} className="tag">{s}</span>
                            ))}
                        </div>
                    </div>

                    {tech.length > 0 && (
                        <div className="profile-section">
                            <h3>⚡ Preferred Technologies</h3>
                            <div className="tag-list">
                                {tech.map((t) => (
                                    <span key={t} className="tag" style={{ background: "var(--info-bg)", color: "var(--info)", borderColor: "rgba(59,130,246,0.15)" }}>{t}</span>
                                ))}
                            </div>
                        </div>
                    )}

                    {domains.length > 0 && (
                        <div className="profile-section">
                            <h3>🌐 Domains</h3>
                            <div className="tag-list">
                                {domains.map((d) => (
                                    <span key={d} className="tag" style={{ background: "var(--warning-bg)", color: "var(--warning)", borderColor: "rgba(245,158,11,0.15)" }}>{d}</span>
                                ))}
                            </div>
                        </div>
                    )}
                </div>

                {/* sidebar */}
                <div>
                    {/* team */}
                    <div className="profile-section">
                        <h3><Users size={18} /> Team</h3>
                        {project.teammates && project.teammates.length > 0 ? (
                            <div className="team-list">
                                {project.teammates.map((t) => (
                                    <div key={t.email} className="team-member">
                                        <div className="team-member-avatar">
                                            {t.name?.[0]?.toUpperCase() || t.email[0].toUpperCase()}
                                        </div>
                                        <div className="team-member-info">
                                            <div className="name">{t.name || t.email}</div>
                                            <div className="detail">
                                                {t.department || ""} {t.year ? `• ${t.year}` : ""}
                                            </div>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        ) : (
                            <p style={{ color: "var(--text-muted)", fontSize: "0.85rem" }}>
                                No teammates yet
                            </p>
                        )}

                        {isOwner && project.status === "ACTIVE" && (
                            <div style={{ marginTop: "1rem" }}>
                                {!showInvite ? (
                                    <button className="btn btn-secondary btn-sm" onClick={() => setShowInvite(true)} style={{ width: "100%" }}>
                                        <UserPlus size={14} /> Invite Teammate
                                    </button>
                                ) : (
                                    <div style={{ display: "flex", gap: "0.5rem" }}>
                                        <input
                                            className="form-input"
                                            placeholder="teammate@email.com"
                                            value={inviteEmail}
                                            onChange={(e) => setInviteEmail(e.target.value)}
                                            onKeyDown={(e) => e.key === "Enter" && handleInvite()}
                                            style={{ fontSize: "0.8rem" }}
                                        />
                                        <button className="btn btn-primary btn-sm" onClick={handleInvite} disabled={inviting}>
                                            {inviting ? "…" : "Send"}
                                        </button>
                                    </div>
                                )}
                            </div>
                        )}
                    </div>

                    {/* actions */}
                    {isOwner && project.status === "ACTIVE" && (
                        <div className="profile-section" style={{ marginTop: "1rem" }}>
                            <h3><CheckCircle2 size={18} /> Actions</h3>
                            <button
                                className="btn btn-success"
                                style={{ width: "100%" }}
                                onClick={handleComplete}
                                disabled={completing}
                            >
                                <CheckCircle2 size={16} />
                                {completing ? "Completing…" : "Mark as Completed"}
                            </button>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}
