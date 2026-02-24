import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { getMyProjects, getIncomingRequests } from "../api/client";
import type { Project, TeamRequest } from "../types";
import {
    FolderGit2,
    Users,
    Bell,
    ArrowRight,
    Clock,
    Rocket,
    CheckCircle2,
    Plus,
} from "lucide-react";

export default function DashboardPage() {
    const { profile } = useAuth();
    const navigate = useNavigate();
    const [projects, setProjects] = useState<Project[]>([]);
    const [requests, setRequests] = useState<TeamRequest[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        Promise.all([
            getMyProjects().catch(() => []),
            getIncomingRequests().catch(() => []),
        ]).then(([p, r]) => {
            setProjects(p as unknown as Project[]);
            setRequests((r as unknown as TeamRequest[]).filter((n) => n.status === "PENDING"));
            setLoading(false);
        });
    }, []);

    if (loading) {
        return (
            <div className="spinner-overlay">
                <div className="spinner" />
            </div>
        );
    }

    const activeProjects = projects.filter((p) => p.status === "ACTIVE");
    const completedProjects = projects.filter((p) => p.status === "COMPLETED");
    const greeting = () => {
        const h = new Date().getHours();
        if (h < 12) return "Good morning";
        if (h < 18) return "Good afternoon";
        return "Good evening";
    };

    return (
        <div className="page-container">
            <div className="page-header" style={{ marginBottom: "1.5rem" }}>
                <h1>{greeting()}, {profile?.name?.split(" ")[0] || "there"} 👋</h1>
                <p>Here's what's happening with your projects</p>
            </div>

            {/* stats */}
            <div className="stats-grid">
                <div className="stat-card">
                    <div className="stat-icon accent">
                        <FolderGit2 />
                    </div>
                    <div className="stat-content">
                        <h4>{projects.length}</h4>
                        <p>Total Projects</p>
                    </div>
                </div>
                <div className="stat-card">
                    <div className="stat-icon success">
                        <Rocket />
                    </div>
                    <div className="stat-content">
                        <h4>{activeProjects.length}</h4>
                        <p>Active</p>
                    </div>
                </div>
                <div className="stat-card">
                    <div className="stat-icon info">
                        <CheckCircle2 />
                    </div>
                    <div className="stat-content">
                        <h4>{completedProjects.length}</h4>
                        <p>Completed</p>
                    </div>
                </div>
                <div className="stat-card" style={{ cursor: "pointer" }} onClick={() => navigate("/notifications")}>
                    <div className="stat-icon warning">
                        <Bell />
                    </div>
                    <div className="stat-content">
                        <h4>{requests.length}</h4>
                        <p>Pending Requests</p>
                    </div>
                </div>
            </div>

            {/* quick actions */}
            <div style={{ display: "flex", gap: "0.75rem", marginBottom: "2rem", flexWrap: "wrap" }}>
                <button className="btn btn-primary" onClick={() => navigate("/projects/new")}>
                    <Plus size={16} /> New Project
                </button>
                <button className="btn btn-secondary" onClick={() => navigate("/explore")}>
                    <Users size={16} /> Explore Projects
                </button>
            </div>

            {/* recent projects */}
            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "1rem" }}>
                <h2 style={{ fontSize: "1.15rem", fontWeight: 600 }}>Your Projects</h2>
                {projects.length > 0 && (
                    <button className="btn btn-ghost btn-sm" onClick={() => navigate("/explore")}>
                        View all <ArrowRight size={14} />
                    </button>
                )}
            </div>

            {projects.length === 0 ? (
                <div className="empty-state" style={{ padding: "2rem" }}>
                    <FolderGit2 />
                    <h3>No projects yet</h3>
                    <p>Create your first project and start finding collaborators</p>
                    <button className="btn btn-primary" onClick={() => navigate("/projects/new")}>
                        <Plus size={16} /> Create Project
                    </button>
                </div>
            ) : (
                <div className="projects-grid">
                    {projects.slice(0, 6).map((p, i) => (
                        <div
                            key={p.id}
                            className="project-card"
                            style={{ animationDelay: `${i * 0.08}s` }}
                            onClick={() => navigate(`/projects/${p.id}`)}
                        >
                            <div className="project-card-header">
                                <h3>{p.title}</h3>
                                <span className={`badge ${p.status === "ACTIVE" ? "badge-success" : "badge-neutral"}`}>
                                    {p.status}
                                </span>
                            </div>
                            {p.description && (
                                <p className="project-card-desc">{p.description}</p>
                            )}
                            <div className="tag-list">
                                {p.requiredSkills?.split(",").slice(0, 4).map((s) => (
                                    <span key={s} className="tag">{s.trim()}</span>
                                ))}
                            </div>
                            <div className="project-card-footer">
                                <div className="project-card-meta">
                                    <Clock size={14} />
                                    {new Date(p.createdAt).toLocaleDateString()}
                                </div>
                                <span className="badge badge-accent">{p.type}</span>
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}
