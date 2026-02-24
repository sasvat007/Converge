/* ------------------------------------------------------------------ */
/*  Shared type definitions for the Converge frontend                 */
/* ------------------------------------------------------------------ */

export interface Profile {
    id?: number;
    email: string;
    name: string | null;
    year: string | null;
    department: string | null;
    institution: string | null;
    availability: string | null;
    resumePdfUrl?: string | null;
    Resume?: string; // raw JSON string from backend
}

export interface ParsedResume {
    profile?: {
        name?: string;
        year?: string;
        department?: string;
        availability?: string;
    };
    skills?: {
        programming_languages?: string[];
        frameworks_libraries?: string[];
        tools_platforms?: string[];
        core_cs_concepts?: string[];
        domain_skills?: string[];
    };
    experience_level?: {
        overall?: string;
        by_domain?: Record<string, string>;
    };
    projects?: {
        title?: string;
        description?: string;
        technologies?: string[];
        domain?: string;
        role?: string;
        completion_status?: string;
    }[];
    interests?: {
        technical?: string[];
        problem_domains?: string[];
        learning_goals?: string[];
    };
    achievements?: {
        hackathons?: string[];
        certifications?: string[];
        awards?: string[];
    };
}

export interface Project {
    id: number;
    title: string;
    type: string;
    visibility: string;
    requiredSkills: string;
    preferredTechnologies?: string;
    githubRepo?: string;
    description?: string;
    domain?: string;
    createdAt: string;
    email: string;
    status: string;
    postedBy?: { email: string };
    teammates?: Teammate[];
}

export interface Teammate {
    id?: number;
    email: string;
    name: string | null;
    year?: string | null;
    department?: string | null;
    institution?: string | null;
    availability?: string | null;
    addedAt?: string;
}

export interface TeamRequest {
    requestId: number;
    projectId: number;
    projectTitle?: string;
    requesterEmail: string;
    targetEmail: string;
    status: string;
    type: string; // JOIN_REQUEST | RATING_REQUEST
    rateeEmail?: string | null;
    rateeName?: string | null;
    createdAt: string;
    updatedAt?: string;
}

export interface ApiError {
    timestamp: string;
    status: number;
    error: string;
    message: string;
}
