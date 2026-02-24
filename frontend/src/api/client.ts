/* ------------------------------------------------------------------ */
/*  Centralised API client for the Converge backend                   */
/* ------------------------------------------------------------------ */

const BASE = "";

/* ----------- helpers ----------- */

function authHeaders(): Record<string, string> {
    const token = localStorage.getItem("token");
    const h: Record<string, string> = { "Content-Type": "application/json" };
    if (token) h["Authorization"] = `Bearer ${token}`;
    return h;
}

async function handleRes<T>(res: Response): Promise<T> {
    let data: any;
    try {
        data = await res.json();
    } catch {
        if (res.status === 401) throw { status: 401, message: "Authentication required — please log in" };
        if (res.status === 403) throw { status: 403, message: "Session expired — please log in again" };
        if (!res.ok) throw { status: res.status, message: `Server error (${res.status})` };
        throw { status: res.status, message: "Invalid response from server" };
    }
    if (!res.ok) throw { status: res.status, ...data };
    return data as T;
}

export function fileToBase64(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => {
            const result = reader.result as string;
            resolve(result.split(",")[1]);
        };
        reader.onerror = reject;
        reader.readAsDataURL(file);
    });
}

/* ================================================================== */
/*  Auth                                                              */
/* ================================================================== */

export async function register(body: {
    email: string;
    password: string;
    resumeText: string;
    resumePdf?: string | null;
    name?: string;
    year?: string;
    department?: string;
    institution?: string;
    availability?: string;
}) {
    const res = await fetch(`${BASE}/auth/register`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
    });
    return handleRes<{ token: string; message: string; profile?: Record<string, unknown> }>(res);
}

export async function login(email: string, password: string) {
    const res = await fetch(`${BASE}/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
    });
    return handleRes<{ token: string; profile?: Record<string, unknown> }>(res);
}

/* ================================================================== */
/*  Profile & Resume                                                  */
/* ================================================================== */

export async function getMyProfile() {
    const res = await fetch(`${BASE}/api/profile`, { headers: authHeaders() });
    return handleRes<Record<string, unknown>>(res);
}

export async function getProfileById(id: number) {
    const res = await fetch(`${BASE}/api/profile/${id}`, { headers: authHeaders() });
    return handleRes<Record<string, unknown>>(res);
}

export async function uploadResume(body: Record<string, unknown>) {
    const res = await fetch(`${BASE}/api/upload`, {
        method: "POST",
        headers: authHeaders(),
        body: JSON.stringify(body),
    });
    return handleRes<Record<string, unknown>>(res);
}

export async function updateResume(body: { resumeText?: string; resumePdf?: string }) {
    const res = await fetch(`${BASE}/api/resume/update`, {
        method: "PUT",
        headers: authHeaders(),
        body: JSON.stringify(body),
    });
    return handleRes<{ message: string; profile: Record<string, unknown> }>(res);
}

export function resumeDownloadUrl(id: number) {
    return `${BASE}/api/resume/download/${id}`;
}

/* ================================================================== */
/*  Projects                                                          */
/* ================================================================== */

export async function createProject(body: {
    title: string;
    type: string;
    visibility: string;
    requiredSkills: string[];
    preferredTechnologies?: string[];
    domain?: string[];
    githubRepo?: string;
    description?: string;
}) {
    const res = await fetch(`${BASE}/api/projects`, {
        method: "POST",
        headers: authHeaders(),
        body: JSON.stringify(body),
    });
    return handleRes<Record<string, unknown>>(res);
}

export async function getMyProjects() {
    const res = await fetch(`${BASE}/api/projects`, { headers: authHeaders() });
    return handleRes<Record<string, unknown>[]>(res);
}

export async function exploreProjects() {
    const res = await fetch(`${BASE}/api/projects/explore`, { headers: authHeaders() });
    return handleRes<Record<string, unknown>[]>(res);
}

export async function getProjectById(id: number) {
    const res = await fetch(`${BASE}/api/projects/${id}`, { headers: authHeaders() });
    return handleRes<Record<string, unknown>>(res);
}

export async function completeProject(id: number) {
    const res = await fetch(`${BASE}/api/projects/${id}/complete`, {
        method: "POST",
        headers: authHeaders(),
    });
    return handleRes<Record<string, unknown>>(res);
}

/* ================================================================== */
/*  Team management                                                   */
/* ================================================================== */

export async function sendTeammateRequest(projectId: number, email: string) {
    const res = await fetch(`${BASE}/api/projects/${projectId}/teammates`, {
        method: "POST",
        headers: authHeaders(),
        body: JSON.stringify({ email }),
    });
    return handleRes<Record<string, unknown>>(res);
}

export async function getIncomingRequests() {
    const res = await fetch(`${BASE}/api/projects/teammates/requests`, {
        headers: authHeaders(),
    });
    return handleRes<Record<string, unknown>[]>(res);
}

export async function acceptRequest(requestId: number) {
    const res = await fetch(`${BASE}/api/projects/teammates/requests/${requestId}/accept`, {
        method: "POST",
        headers: authHeaders(),
    });
    return handleRes<Record<string, unknown>>(res);
}

export async function rejectRequest(requestId: number) {
    const res = await fetch(`${BASE}/api/projects/teammates/requests/${requestId}/reject`, {
        method: "POST",
        headers: authHeaders(),
    });
    return handleRes<Record<string, unknown>>(res);
}
