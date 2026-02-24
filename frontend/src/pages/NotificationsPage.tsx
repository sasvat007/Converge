import { useEffect, useState } from "react";
import { getIncomingRequests, acceptRequest, rejectRequest } from "../api/client";
import { useToast } from "../context/ToastContext";
import type { TeamRequest } from "../types";
import { UserPlus, Star, Check, X, Inbox } from "lucide-react";

export default function NotificationsPage() {
    const { addToast } = useToast();
    const [requests, setRequests] = useState<TeamRequest[]>([]);
    const [loading, setLoading] = useState(true);
    const [processing, setProcessing] = useState<Record<number, boolean>>({});
    const [tab, setTab] = useState<"all" | "join" | "rating">("all");

    useEffect(() => {
        getIncomingRequests()
            .then((r) => setRequests(r as unknown as TeamRequest[]))
            .catch(() => addToast("Failed to load notifications", "error"))
            .finally(() => setLoading(false));
    }, []);

    const handle = async (r: TeamRequest, accept: boolean) => {
        setProcessing((p) => ({ ...p, [r.requestId]: true }));
        try {
            if (accept) {
                await acceptRequest(r.requestId);
                addToast("Request accepted! 🎉", "success");
            } else {
                await rejectRequest(r.requestId);
                addToast("Request rejected", "info");
            }
            setRequests((prev) => prev.filter((x) => x.requestId !== r.requestId));
        } catch (err: any) {
            addToast(err?.message || "Action failed", "error");
        } finally {
            setProcessing((p) => ({ ...p, [r.requestId]: false }));
        }
    };

    if (loading) return <div className="spinner-overlay"><div className="spinner" /></div>;

    const filtered = requests.filter((r) => {
        if (tab === "join") return r.type === "JOIN_REQUEST";
        if (tab === "rating") return r.type === "RATING_REQUEST";
        return true;
    });

    const pending = requests.filter((r) => r.status === "PENDING").length;

    return (
        <div className="page-container">
            <div className="page-header">
                <h1>Notifications</h1>
                <p>{pending > 0 ? `${pending} pending request${pending > 1 ? "s" : ""}` : "You're all caught up!"}</p>
            </div>

            <div className="tabs">
                {(["all", "join", "rating"] as const).map((t) => (
                    <button key={t} className={`tab ${tab === t ? "active" : ""}`} onClick={() => setTab(t)}>
                        {t === "all" ? `All (${requests.length})` : t === "join" ? "Team Invites" : "Ratings"}
                    </button>
                ))}
            </div>

            {filtered.length === 0 ? (
                <div className="empty-state"><Inbox /><h3>No notifications</h3><p>Invitations and rating requests will appear here</p></div>
            ) : (
                <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
                    {filtered.map((r, i) => (
                        <div key={r.requestId} className="notification-card" style={{ animationDelay: `${i * 0.06}s` }}>
                            <div className={`notification-icon ${r.type === "JOIN_REQUEST" ? "join" : "rating"}`}>
                                {r.type === "JOIN_REQUEST" ? <UserPlus /> : <Star />}
                            </div>
                            <div className="notification-body">
                                <h4>{r.type === "JOIN_REQUEST" ? `Invitation — ${r.projectTitle || `Project #${r.projectId}`}` : `Rate — ${r.rateeName || r.rateeEmail}`}</h4>
                                <p>{r.type === "JOIN_REQUEST" ? `${r.requesterEmail} invited you` : `Rate ${r.rateeName || r.rateeEmail} on "${r.projectTitle}"`}</p>
                                <span className="time">{new Date(r.createdAt).toLocaleDateString()}</span>
                            </div>
                            {r.status === "PENDING" ? (
                                <div className="notification-actions">
                                    <button className="btn btn-success btn-sm" onClick={() => handle(r, true)} disabled={processing[r.requestId]}><Check size={14} /> Accept</button>
                                    <button className="btn btn-danger btn-sm" onClick={() => handle(r, false)} disabled={processing[r.requestId]}><X size={14} /> Reject</button>
                                </div>
                            ) : (
                                <span className={`badge ${r.status === "ACCEPTED" ? "badge-success" : "badge-neutral"}`}>{r.status}</span>
                            )}
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}
