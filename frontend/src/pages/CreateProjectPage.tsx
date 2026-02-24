import { useState, type FormEvent, type KeyboardEvent } from "react";
import { useNavigate } from "react-router-dom";
import { createProject } from "../api/client";
import { useToast } from "../context/ToastContext";
import { FolderPlus, X } from "lucide-react";

export default function CreateProjectPage() {
    const { addToast } = useToast();
    const navigate = useNavigate();
    const [busy, setBusy] = useState(false);

    const [form, setForm] = useState({
        title: "",
        type: "Software Development",
        visibility: "public",
        description: "",
        githubRepo: "",
    });

    const [skills, setSkills] = useState<string[]>([]);
    const [skillInput, setSkillInput] = useState("");
    const [techStack, setTechStack] = useState<string[]>([]);
    const [techInput, setTechInput] = useState("");
    const [domains, setDomains] = useState<string[]>([]);
    const [domainInput, setDomainInput] = useState("");

    const set = (key: string, val: string) => setForm((p) => ({ ...p, [key]: val }));

    const handleTagKeyDown = (
        e: KeyboardEvent<HTMLInputElement>,
        tags: string[],
        setTags: (t: string[]) => void,
        input: string,
        setInput: (v: string) => void
    ) => {
        if ((e.key === "Enter" || e.key === ",") && input.trim()) {
            e.preventDefault();
            const val = input.trim().replace(/,$/, "");
            if (val && !tags.includes(val)) {
                setTags([...tags, val]);
            }
            setInput("");
        }
        if (e.key === "Backspace" && !input && tags.length > 0) {
            setTags(tags.slice(0, -1));
        }
    };

    const handleSubmit = async (e: FormEvent) => {
        e.preventDefault();
        if (!form.title.trim()) {
            addToast("Project title is required", "error");
            return;
        }
        if (skills.length === 0) {
            addToast("Add at least one required skill", "error");
            return;
        }
        setBusy(true);
        try {
            const res = await createProject({
                ...form,
                requiredSkills: skills,
                preferredTechnologies: techStack.length ? techStack : undefined,
                domain: domains.length ? domains : undefined,
            });
            addToast("Project created successfully! 🚀", "success");
            navigate(`/projects/${(res as any).id}`);
        } catch (err: any) {
            addToast(err?.message || "Failed to create project", "error");
        } finally {
            setBusy(false);
        }
    };

    return (
        <div className="page-container">
            <div className="page-header">
                <h1>Create New Project</h1>
                <p>Describe your project and find the right teammates</p>
            </div>

            <form className="create-project-form" onSubmit={handleSubmit}>
                <div className="form-group">
                    <label className="form-label" htmlFor="proj-title">Project Title *</label>
                    <input
                        id="proj-title"
                        className="form-input"
                        placeholder="AI Chatbot Platform"
                        value={form.title}
                        onChange={(e) => set("title", e.target.value)}
                        required
                    />
                </div>

                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0.75rem" }}>
                    <div className="form-group">
                        <label className="form-label" htmlFor="proj-type">Type *</label>
                        <select
                            id="proj-type"
                            className="form-select"
                            value={form.type}
                            onChange={(e) => set("type", e.target.value)}
                        >
                            <option value="Software Development">Software Development</option>
                            <option value="Research">Research</option>
                            <option value="Design">Design</option>
                            <option value="Data Science">Data Science</option>
                            <option value="Mobile App">Mobile App</option>
                            <option value="Other">Other</option>
                        </select>
                    </div>
                    <div className="form-group">
                        <label className="form-label" htmlFor="proj-visibility">Visibility *</label>
                        <select
                            id="proj-visibility"
                            className="form-select"
                            value={form.visibility}
                            onChange={(e) => set("visibility", e.target.value)}
                        >
                            <option value="public">Public</option>
                            <option value="private">Private</option>
                        </select>
                    </div>
                </div>

                <div className="form-group">
                    <label className="form-label" htmlFor="proj-desc">Description</label>
                    <textarea
                        id="proj-desc"
                        className="form-textarea"
                        placeholder="Describe your project, its goals, and what you're looking for in collaborators…"
                        value={form.description}
                        onChange={(e) => set("description", e.target.value)}
                        rows={4}
                    />
                </div>

                <div className="form-group">
                    <label className="form-label">Required Skills *</label>
                    <div className="tag-input-container">
                        {skills.map((s) => (
                            <span key={s} className="tag">
                                {s}
                                <button type="button" onClick={() => setSkills(skills.filter((x) => x !== s))}>
                                    <X size={12} />
                                </button>
                            </span>
                        ))}
                        <input
                            placeholder={skills.length ? "" : "Type and press Enter…"}
                            value={skillInput}
                            onChange={(e) => setSkillInput(e.target.value)}
                            onKeyDown={(e) => handleTagKeyDown(e, skills, setSkills, skillInput, setSkillInput)}
                        />
                    </div>
                    <span className="form-hint">Press Enter or comma to add</span>
                </div>

                <div className="form-group">
                    <label className="form-label">Preferred Technologies</label>
                    <div className="tag-input-container">
                        {techStack.map((t) => (
                            <span key={t} className="tag">
                                {t}
                                <button type="button" onClick={() => setTechStack(techStack.filter((x) => x !== t))}>
                                    <X size={12} />
                                </button>
                            </span>
                        ))}
                        <input
                            placeholder={techStack.length ? "" : "Docker, Kubernetes…"}
                            value={techInput}
                            onChange={(e) => setTechInput(e.target.value)}
                            onKeyDown={(e) => handleTagKeyDown(e, techStack, setTechStack, techInput, setTechInput)}
                        />
                    </div>
                </div>

                <div className="form-group">
                    <label className="form-label">Domains</label>
                    <div className="tag-input-container">
                        {domains.map((d) => (
                            <span key={d} className="tag">
                                {d}
                                <button type="button" onClick={() => setDomains(domains.filter((x) => x !== d))}>
                                    <X size={12} />
                                </button>
                            </span>
                        ))}
                        <input
                            placeholder={domains.length ? "" : "AI/ML, Web Dev…"}
                            value={domainInput}
                            onChange={(e) => setDomainInput(e.target.value)}
                            onKeyDown={(e) => handleTagKeyDown(e, domains, setDomains, domainInput, setDomainInput)}
                        />
                    </div>
                </div>

                <div className="form-group">
                    <label className="form-label" htmlFor="proj-github">GitHub Repository</label>
                    <input
                        id="proj-github"
                        className="form-input"
                        placeholder="https://github.com/user/repo"
                        value={form.githubRepo}
                        onChange={(e) => set("githubRepo", e.target.value)}
                    />
                </div>

                <div style={{ display: "flex", gap: "0.75rem", marginTop: "0.5rem" }}>
                    <button className="btn btn-primary btn-lg" type="submit" disabled={busy}>
                        <FolderPlus size={18} />
                        {busy ? "Creating…" : "Create Project"}
                    </button>
                    <button className="btn btn-ghost btn-lg" type="button" onClick={() => navigate(-1)}>
                        Cancel
                    </button>
                </div>
            </form>
        </div>
    );
}
