import { NavLink, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { useEffect, useState } from "react";
import { getIncomingRequests } from "../api/client";
import {
    LayoutDashboard,
    Compass,
    FolderPlus,
    Bell,
    User,
    LogOut,
    Menu,
    X,
} from "lucide-react";

export default function Sidebar() {
    const { profile, logout } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();
    const [open, setOpen] = useState(false);
    const [notifCount, setNotifCount] = useState(0);

    useEffect(() => {
        getIncomingRequests()
            .then((r) => setNotifCount(r.filter((n) => (n as any).status === "PENDING").length))
            .catch(() => { });
    }, [location.pathname]);

    const handleLogout = () => {
        logout();
        navigate("/login");
    };

    const initials =
        profile?.name
            ?.split(" ")
            .map((w) => w[0])
            .join("")
            .slice(0, 2)
            .toUpperCase() || "?";

    const links = [
        { to: "/dashboard", label: "Dashboard", icon: <LayoutDashboard /> },
        { to: "/explore", label: "Explore", icon: <Compass /> },
        { to: "/projects/new", label: "New Project", icon: <FolderPlus /> },
        {
            to: "/notifications",
            label: "Notifications",
            icon: <Bell />,
            badge: notifCount > 0 ? notifCount : undefined,
        },
        { to: "/profile", label: "Profile", icon: <User /> },
    ];

    return (
        <>
            {/* mobile header */}
            <div className="mobile-header">
                <button onClick={() => setOpen(true)} aria-label="Open sidebar">
                    <Menu size={22} />
                </button>
                <h2>Converge</h2>
                <div style={{ width: 22 }} />
            </div>

            {open && <div className="sidebar-overlay" onClick={() => setOpen(false)} />}

            <aside className={`sidebar ${open ? "open" : ""}`}>
                <div className="sidebar-brand">
                    <div className="sidebar-brand-icon">C</div>
                    <h2>Converge</h2>
                    <button
                        className="sidebar-logout"
                        style={{ marginLeft: "auto", display: open ? "flex" : "none" }}
                        onClick={() => setOpen(false)}
                        aria-label="Close sidebar"
                    >
                        <X size={18} />
                    </button>
                </div>

                <nav className="sidebar-nav">
                    <span className="sidebar-section-label">Navigation</span>
                    {links.map((l) => (
                        <NavLink
                            key={l.to}
                            to={l.to}
                            className={({ isActive }) =>
                                `sidebar-link ${isActive ? "active" : ""}`
                            }
                            onClick={() => setOpen(false)}
                        >
                            {l.icon}
                            {l.label}
                            {l.badge && <span className="sidebar-badge">{l.badge}</span>}
                        </NavLink>
                    ))}
                </nav>

                <div className="sidebar-footer">
                    <div className="sidebar-user">
                        <div className="sidebar-avatar">{initials}</div>
                        <div className="sidebar-user-info">
                            <div className="name">{profile?.name || "User"}</div>
                            <div className="email">{profile?.email || ""}</div>
                        </div>
                        <button
                            className="sidebar-logout"
                            onClick={handleLogout}
                            title="Logout"
                            aria-label="Logout"
                        >
                            <LogOut size={16} />
                        </button>
                    </div>
                </div>
            </aside>
        </>
    );
}
