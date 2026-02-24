import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { exploreProjects } from "../api/client";
import type { Project } from "../types";
import { Search, Clock, Compass } from "lucide-react";

const TYPES = ["All", "Software Development", "Research", "Design", "Other"];

export default function ExplorePage() {
    const [projects, setProjects] = useState<Project[]>([]);
    const [loading, setLoading] = useState(true);
    const [search, setSearch] = useState("");
    const [typeFilter, setTypeFilter] = useState("All");
    const navigate = useNavigate();

    useEffect(() => {
        exploreProjects()
            .then((p) => setProjects(p as unknown as Project[]))
            .catch(() => { })
            .finally(() => setLoading(false));
    }, []);

    if (loading) {
        return (
            <div className="spinner-overlay">
                <div className="spinner" />
            </div>
        );
    }

    const filtered = projects.filter((p) => {
        const matchSearch =
            !search ||
            p.title.toLowerCase().includes(search.toLowerCase()) ||
            p.description?.toLowerCase().includes(search.toLowerCase()) ||
            p.requiredSkills?.toLowerCase().includes(search.toLowerCase());
        const matchType = typeFilter === "All" || p.type === typeFilter;
        return matchSearch && matchType;
    });

    return (
        <div className="page-container">
            <div className="page-header">
                <h1>Explore Projects</h1>
                <p>Discover projects that match your skills and interests</p>
            </div>

            <div className="search-bar">
                <Search />
                <input
                    id="explore-search"
                    type="text"
                    placeholder="Search by title, description, or skills…"
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                />
            </div>

            <div className="filter-bar">
                {TYPES.map((t) => (
                    <button
                        key={t}
                        className={`filter-chip ${typeFilter === t ? "active" : ""}`}
                        onClick={() => setTypeFilter(t)}
                    >
                        {t}
                    </button>
                ))}
            </div>

            {filtered.length === 0 ? (
                <div className="empty-state">
                    <Compass />
                    <h3>No projects found</h3>
                    <p>Try adjusting your search or filters</p>
                </div>
            ) : (
                <div className="projects-grid">
                    {filtered.map((p, i) => (
                        <div
                            key={p.id}
                            className="project-card"
                            style={{ animationDelay: `${i * 0.06}s` }}
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
                                {p.requiredSkills?.split(",").slice(0, 5).map((s) => (
                                    <span key={s} className="tag">{s.trim()}</span>
                                ))}
                            </div>
                            <div className="project-card-footer">
                                <div className="project-card-meta">
                                    <Clock size={14} />
                                    {new Date(p.createdAt).toLocaleDateString()}
                                </div>
                                <div style={{ display: "flex", gap: "0.375rem" }}>
                                    <span className="badge badge-accent">{p.type}</span>
                                    {p.domain && (
                                        <span className="badge badge-info">
                                            {p.domain.split(",")[0]}
                                        </span>
                                    )}
                                </div>
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}
