# Converge – Buddy Finder API Documentation

> **Base URL:** `http://localhost:8080`
> **Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
> **OpenAPI JSON:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

---

## Table of Contents

1. [Authentication Overview](#authentication-overview)
2. [Error Response Format](#error-response-format)
3. [Auth Endpoints](#1-authentication)
   - [POST /auth/register](#post-authregister)
   - [POST /auth/login](#post-authlogin)
4. [Profile & Resume Endpoints](#2-profile--resume)
   - [POST /api/upload](#post-apiupload)
   - [GET /api/profile](#get-apiprofile)
   - [GET /api/profile/:id](#get-apiprofileid)
   - [PUT /api/resume/update](#put-apiresumeupdate)
   - [GET /api/resume/download/:id](#get-apiresumedownloadid)
   - [GET /api/resume/download](#get-apiresumedownload)
5. [Project Endpoints](#3-projects)
   - [POST /api/projects](#post-apiprojects)
   - [GET /api/projects](#get-apiprojects)
   - [GET /api/projects/explore](#get-apiprojectsexplore)
   - [GET /api/projects/:id](#get-apiprojectsid)
   - [POST /api/projects/:id/complete](#post-apiprojectsidcomplete)
6. [Team Management Endpoints](#4-team-management)
   - [POST /api/projects/:id/teammates](#post-apiprojectsidteammates)
   - [GET /api/projects/teammates/requests](#get-apiprojectsteammatesrequests)
   - [POST /api/projects/teammates/requests/:id/accept](#post-apiprojectsteammatesrequestsidaccept)
   - [POST /api/projects/teammates/requests/:id/reject](#post-apiprojectsteammatesrequestsidreject)

---

## Authentication Overview

All endpoints **except** `/auth/register`, `/auth/login`, and Swagger UI require a **JWT Bearer token**.

### How to use the token

After a successful **register** or **login**, you receive a `token` in the response body. Include it in every subsequent request as an HTTP header:

```
Authorization: Bearer <your-jwt-token>
```

### Frontend example (Axios)

```javascript
const api = axios.create({
  baseURL: "http://localhost:8080",
});

// After login/register, store the token
localStorage.setItem("token", response.data.token);

// Attach the token to every request
api.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});
```

### Frontend example (fetch)

```javascript
const headers = {
  "Content-Type": "application/json",
  Authorization: `Bearer ${localStorage.getItem("token")}`,
};

const res = await fetch("http://localhost:8080/api/profile", { headers });
```

---

## Error Response Format

All errors return a consistent JSON envelope:

```json
{
  "timestamp": "2026-02-23T12:00:00.000Z",
  "status": 404,
  "error": "Not Found",
  "message": "Profile not found for id: 42"
}
```

| Field       | Type   | Description                        |
| ----------- | ------ | ---------------------------------- |
| `timestamp` | string | ISO-8601 timestamp of the error    |
| `status`    | number | HTTP status code                   |
| `error`     | string | HTTP status reason phrase           |
| `message`   | string | Human-readable error description   |

---

## 1. Authentication

### POST `/auth/register`

Creates a new user account, parses the resume via Gemini AI, and returns a JWT token.

**Auth required:** ❌ No

**Request Body** (`Content-Type: application/json`):

```json
{
  "email": "user@example.com",
  "password": "securePassword123",
  "resumeText": "John Doe, Software Engineer with 3 years experience in Java, Spring Boot...",
  "resumePdf": "<base64-encoded-pdf-string>",
  "name": "John Doe",
  "year": "3rd Year",
  "department": "Computer Science",
  "institution": "MIT",
  "availability": "high"
}
```

| Field          | Type   | Required | Description                                                      |
| -------------- | ------ | -------- | ---------------------------------------------------------------- |
| `email`        | string | ✅ Yes   | Unique email address                                             |
| `password`     | string | ✅ Yes   | User password (stored hashed)                                    |
| `resumeText`   | string | ✅ Yes   | Plain text resume — parsed by AI into structured JSON            |
| `resumePdf`    | string | ❌ No    | Base64-encoded PDF file of the resume                            |
| `name`         | string | ❌ No    | Display name (auto-extracted from resume if not provided)        |
| `year`         | string | ❌ No    | Academic year, e.g. `"3rd Year"`                                 |
| `department`   | string | ❌ No    | Department, e.g. `"Computer Science"`                            |
| `institution`  | string | ❌ No    | College/university name                                          |
| `availability` | string | ❌ No    | One of: `"low"`, `"medium"`, `"high"`                            |

**Success Response** (`200 OK`):

```json
{
  "message": "Registered successfully",
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "profile": {
    "email": "user@example.com",
    "name": "John Doe",
    "year": "3rd Year",
    "department": "Computer Science",
    "institution": "MIT",
    "availability": "high",
    "resumePdfUrl": "/api/resume/download/1"
  }
}
```

**Error Responses:**

| Status | Condition                          |
| ------ | ---------------------------------- |
| `400`  | Missing email, password, or resumeText |
| `409`  | Email already registered           |
| `500`  | Resume parsing or DB error         |

**Frontend integration:**

```javascript
const register = async (formData) => {
  // Convert PDF file to base64
  let resumePdf = null;
  if (formData.pdfFile) {
    resumePdf = await fileToBase64(formData.pdfFile);
  }

  const res = await fetch("http://localhost:8080/auth/register", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      email: formData.email,
      password: formData.password,
      resumeText: formData.resumeText,
      resumePdf: resumePdf,
      name: formData.name,
      year: formData.year,
      department: formData.department,
      institution: formData.institution,
      availability: formData.availability,
    }),
  });

  const data = await res.json();
  if (res.ok) {
    localStorage.setItem("token", data.token);
    // Navigate to dashboard
  }
};

// Helper: convert File to base64 string
const fileToBase64 = (file) =>
  new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result.split(",")[1]); // strip data:...;base64, prefix
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
```

---

### POST `/auth/login`

Authenticates an existing user and returns a JWT token.

**Auth required:** ❌ No

**Request Body:**

```json
{
  "email": "user@example.com",
  "password": "securePassword123"
}
```

| Field      | Type   | Required | Description     |
| ---------- | ------ | -------- | --------------- |
| `email`    | string | ✅ Yes   | Registered email |
| `password` | string | ✅ Yes   | User password   |

**Success Response** (`200 OK`):

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "profile": {
    "email": "user@example.com",
    "name": "John Doe",
    "year": "3rd Year",
    "department": "Computer Science",
    "institution": "MIT",
    "availability": "high",
    "resumePdfUrl": "/api/resume/download/1"
  }
}
```

> **Note:** `profile` may be missing if the user registered but their profile wasn't saved (edge case).

**Error Responses:**

| Status | Condition                  |
| ------ | -------------------------- |
| `400`  | Missing email or password  |
| `401`  | User not found or wrong password |

**Frontend integration:**

```javascript
const login = async (email, password) => {
  const res = await fetch("http://localhost:8080/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });

  const data = await res.json();
  if (res.ok) {
    localStorage.setItem("token", data.token);
    // Store profile data in state/context
    setUser(data.profile);
  } else {
    alert(data.message || "Login failed");
  }
};
```

---

## 2. Profile & Resume

> All endpoints in this section require the `Authorization: Bearer <token>` header.

### POST `/api/upload`

Uploads resume text (and optional PDF), parses it via Gemini AI, and saves the profile.

**Auth required:** ✅ Yes

**Request Body:**

```json
{
  "resumeText": "John Doe, 3 years experience in Java, Spring Boot...",
  "resumePdf": "<base64-encoded-pdf>",
  "name": "John Doe",
  "year": "3rd Year",
  "department": "Computer Science",
  "institution": "MIT",
  "availability": "high"
}
```

| Field          | Type   | Required | Description                                               |
| -------------- | ------ | -------- | --------------------------------------------------------- |
| `resumeText`   | string | ❌ No    | Resume text to parse (if empty, stores `{}` as profile JSON) |
| `resumePdf`    | string | ❌ No    | Base64-encoded PDF                                        |
| `name`         | string | ❌ No    | Display name                                              |
| `year`         | string | ❌ No    | Academic year                                             |
| `department`   | string | ❌ No    | Department                                                |
| `institution`  | string | ❌ No    | Institution                                               |
| `availability` | string | ❌ No    | `"low"` / `"medium"` / `"high"`                           |

**Success Response** (`200 OK`):

```json
{
  "email": "user@example.com",
  "name": "John Doe",
  "year": "3rd Year",
  "department": "Computer Science",
  "institution": "MIT",
  "availability": "high",
  "resumePdfUrl": "/api/resume/download/1"
}
```

---

### GET `/api/profile`

Returns the currently authenticated user's full profile.

**Auth required:** ✅ Yes

**Request Body:** None

**Success Response** (`200 OK`):

```json
{
  "id": 1,
  "email": "user@example.com",
  "name": "John Doe",
  "year": "3rd Year",
  "department": "Computer Science",
  "institution": "MIT",
  "availability": "high",
  "resumePdfUrl": "/api/resume/download/1",
  "Resume": "{\"profile\":{\"name\":\"John Doe\",...},\"skills\":{...},...}"
}
```

> **Note:** The `Resume` field contains the **raw JSON string** of the AI-parsed resume. Parse it on the frontend with `JSON.parse(data.Resume)`.

**Error Responses:**

| Status | Condition                        |
| ------ | -------------------------------- |
| `401`  | Missing or invalid JWT           |
| `404`  | No profile found for this user   |

**Frontend integration:**

```javascript
const getProfile = async () => {
  const res = await fetch("http://localhost:8080/api/profile", {
    headers: {
      Authorization: `Bearer ${localStorage.getItem("token")}`,
    },
  });

  const data = await res.json();
  if (res.ok) {
    const parsedResume = JSON.parse(data.Resume); // parse the resume JSON string
    setProfile({ ...data, resumeData: parsedResume });
  }
};
```

---

### GET `/api/profile/:id`

Returns any user's profile by their profile ID. Useful for viewing other users' profiles.

**Auth required:** ✅ Yes

**URL Parameters:**

| Param | Type   | Description                 |
| ----- | ------ | --------------------------- |
| `id`  | number | Profile ID of the user      |

**Success Response** (`200 OK`):

```json
{
  "email": "otheruser@example.com",
  "name": "Jane Smith",
  "year": "2nd Year",
  "department": "Electronics",
  "institution": "Stanford",
  "availability": "medium",
  "Resume": "{...}",
  "resumePdfUrl": "/api/resume/download/2"
}
```

**Error Responses:**

| Status | Condition          |
| ------ | ------------------ |
| `404`  | Profile not found  |

---

### PUT `/api/resume/update`

Updates an **existing** profile's resume. Only updates resume content — other profile fields are unchanged.

**Auth required:** ✅ Yes

**Request Body:**

```json
{
  "resumeText": "Updated resume content with new skills...",
  "resumePdf": "<base64-encoded-pdf>"
}
```

| Field        | Type   | Required | Description                     |
| ------------ | ------ | -------- | ------------------------------- |
| `resumeText` | string | ❌ No    | New resume text to re-parse     |
| `resumePdf`  | string | ❌ No    | Updated base64-encoded PDF      |

**Success Response** (`200 OK`):

```json
{
  "message": "Resume updated successfully",
  "profile": {
    "id": 1,
    "email": "user@example.com",
    "name": "John Doe",
    "year": "3rd Year",
    "department": "Computer Science",
    "institution": "MIT",
    "availability": "high",
    "resumePdfUrl": "/api/resume/download/1"
  }
}
```

**Error Responses:**

| Status | Condition                                   |
| ------ | ------------------------------------------- |
| `400`  | Invalid base64 PDF format                   |
| `404`  | Profile not found (must upload first)       |

---

### GET `/api/resume/download/:id`

Downloads a user's resume PDF by profile ID.

**Auth required:** ❌ No (public)

**URL Parameters:**

| Param | Type   | Description   |
| ----- | ------ | ------------- |
| `id`  | number | Profile ID    |

**Success Response** (`200 OK`):
- `Content-Type: application/pdf`
- Body: binary PDF file

**Error Responses:**

| Status | Condition              |
| ------ | ---------------------- |
| `404`  | Resume PDF not found   |

**Frontend integration:**

```javascript
// Open PDF in new tab
const downloadResume = (profileId) => {
  window.open(`http://localhost:8080/api/resume/download/${profileId}`, "_blank");
};

// Or download programmatically
const downloadResumeAsFile = async (profileId) => {
  const res = await fetch(`http://localhost:8080/api/resume/download/${profileId}`);
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `resume_${profileId}.pdf`;
  a.click();
  URL.revokeObjectURL(url);
};
```

---

### GET `/api/resume/download`

Downloads the **currently authenticated** user's resume PDF.

**Auth required:** ✅ Yes

**Success Response:** Same as above (binary PDF).

**Error Responses:**

| Status | Condition                       |
| ------ | ------------------------------- |
| `401`  | Unauthorized                    |
| `404`  | Profile or resume PDF not found |

---

## 3. Projects

> All endpoints require `Authorization: Bearer <token>` header.

### POST `/api/projects`

Creates a new project for the authenticated user.

**Auth required:** ✅ Yes

**Request Body:**

```json
{
  "title": "AI Chatbot Platform",
  "type": "Software Development",
  "visibility": "public",
  "requiredSkills": ["Java", "Spring Boot", "React"],
  "preferredTechnologies": ["Docker", "Kubernetes"],
  "domain": ["AI/ML", "Web Development"],
  "githubRepo": "https://github.com/user/project",
  "description": "An intelligent chatbot platform built with modern tech."
}
```

| Field                    | Type           | Required | Description                                           |
| ------------------------ | -------------- | -------- | ----------------------------------------------------- |
| `title`                  | string         | ✅ Yes   | Project title                                         |
| `type`                   | string         | ✅ Yes   | Project type, e.g. `"Software Development"`, `"Research"` |
| `visibility`             | string         | ✅ Yes   | `"public"` or `"private"`                             |
| `requiredSkills`         | string[]       | ✅ Yes   | Array of required skill names                         |
| `preferredTechnologies`  | string[]       | ❌ No    | Array of preferred tech stack                         |
| `domain`                 | string[]       | ❌ No    | Array of project domains                              |
| `githubRepo`             | string         | ❌ No    | GitHub repository URL                                 |
| `description`            | string         | ❌ No    | Free-text project description                         |

> **Alternative key names accepted:** `required_skills`, `preferred_technologies`, `preferred_skills`, `preferredSkills`, `domains`, `projectDomains`, `domain_list`

**Success Response** (`201 Created`):

```json
{
  "id": 1,
  "title": "AI Chatbot Platform",
  "type": "Software Development",
  "visibility": "public",
  "requiredSkills": "Java,Spring Boot,React",
  "preferredTechnologies": "Docker,Kubernetes",
  "githubRepo": "https://github.com/user/project",
  "description": "An intelligent chatbot platform built with modern tech.",
  "domain": "AI/ML,Web Development",
  "createdAt": "2026-02-23T12:00:00.000Z",
  "email": "user@example.com",
  "status": "ACTIVE"
}
```

> **Important:** `requiredSkills`, `preferredTechnologies`, and `domain` are stored and returned as **comma-separated strings**. Split them on the frontend:
> ```javascript
> const skills = project.requiredSkills?.split(",") || [];
> ```

**Error Responses:**

| Status | Condition                |
| ------ | ------------------------ |
| `400`  | Missing required fields  |
| `401`  | Unauthorized             |

**Frontend integration:**

```javascript
const createProject = async (formData) => {
  const res = await fetch("http://localhost:8080/api/projects", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${localStorage.getItem("token")}`,
    },
    body: JSON.stringify({
      title: formData.title,
      type: formData.type,
      visibility: formData.visibility,
      requiredSkills: formData.skills,        // array of strings
      preferredTechnologies: formData.techStack, // array of strings
      domain: formData.domains,               // array of strings
      githubRepo: formData.githubUrl,
      description: formData.description,
    }),
  });

  const data = await res.json();
  if (res.status === 201) {
    // Project created successfully
    navigateTo(`/projects/${data.id}`);
  }
};
```

---

### GET `/api/projects`

Returns all projects owned by or associated with the authenticated user (owned + teammate).

**Auth required:** ✅ Yes

**Request Body:** None

**Success Response** (`200 OK`):

```json
[
  {
    "id": 1,
    "title": "AI Chatbot Platform",
    "type": "Software Development",
    "visibility": "public",
    "requiredSkills": "Java,Spring Boot,React",
    "preferredTechnologies": "Docker,Kubernetes",
    "githubRepo": "https://github.com/user/project",
    "description": "An intelligent chatbot platform...",
    "domain": "AI/ML,Web Development",
    "createdAt": "2026-02-23T12:00:00.000Z",
    "email": "user@example.com",
    "status": "ACTIVE",
    "postedBy": {
      "email": "user@example.com"
    }
  }
]
```

---

### GET `/api/projects/explore`

Returns **all** projects in the system (public explore/feed page).

**Auth required:** ✅ Yes

**Request Body:** None

**Success Response** (`200 OK`): Same array format as `GET /api/projects`.

---

### GET `/api/projects/:id`

Returns a single project's details including its current teammates.

**Auth required:** ✅ Yes

**URL Parameters:**

| Param | Type   | Description |
| ----- | ------ | ----------- |
| `id`  | number | Project ID  |

**Success Response** (`200 OK`):

```json
{
  "id": 1,
  "title": "AI Chatbot Platform",
  "type": "Software Development",
  "visibility": "public",
  "requiredSkills": "Java,Spring Boot,React",
  "preferredTechnologies": "Docker,Kubernetes",
  "githubRepo": "https://github.com/user/project",
  "description": "An intelligent chatbot platform...",
  "domain": "AI/ML,Web Development",
  "createdAt": "2026-02-23T12:00:00.000Z",
  "email": "user@example.com",
  "status": "ACTIVE",
  "teammates": [
    {
      "id": 2,
      "email": "teammate@example.com",
      "name": "Jane Smith",
      "year": "2nd Year",
      "department": "Electronics",
      "institution": "Stanford",
      "availability": "medium",
      "addedAt": "2026-02-23T14:00:00.000Z"
    }
  ]
}
```

**Error Responses:**

| Status | Condition          |
| ------ | ------------------ |
| `400`  | Invalid project ID |
| `404`  | Project not found  |

---

### POST `/api/projects/:id/complete`

Marks a project as completed. **Only the project owner** can do this. When completed, rating requests are automatically generated for all team members.

**Auth required:** ✅ Yes

**URL Parameters:**

| Param | Type   | Description |
| ----- | ------ | ----------- |
| `id`  | number | Project ID  |

**Request Body:** None

**Success Response** (`200 OK`):

```json
{
  "message": "Project marked as completed",
  "project": {
    "id": 1,
    "title": "AI Chatbot Platform",
    "status": "COMPLETED",
    ...
  }
}
```

**Error Responses:**

| Status | Condition                          |
| ------ | ---------------------------------- |
| `400`  | Invalid project ID                 |
| `403`  | Not the project owner              |

---

## 4. Team Management

### POST `/api/projects/:id/teammates`

Project owner sends a **teammate invitation** to another user by email. The target user must accept/reject.

**Auth required:** ✅ Yes (must be the project owner)

**URL Parameters:**

| Param | Type   | Description |
| ----- | ------ | ----------- |
| `id`  | number | Project ID  |

**Request Body:**

```json
{
  "email": "teammate@example.com"
}
```

| Field   | Type   | Required | Description                      |
| ------- | ------ | -------- | -------------------------------- |
| `email` | string | ✅ Yes   | Email of the user to invite      |

**Success Response** (`201 Created`):

```json
{
  "requestId": 1,
  "projectId": 1,
  "requesterEmail": "owner@example.com",
  "targetEmail": "teammate@example.com",
  "status": "PENDING",
  "createdAt": "2026-02-23T14:00:00.000Z"
}
```

**Error Responses:**

| Status | Condition                                   |
| ------ | ------------------------------------------- |
| `400`  | Invalid project ID or missing email         |
| `403`  | Not the project owner                       |
| `500`  | Target user not found / already a teammate  |

**Frontend integration:**

```javascript
const sendTeammateRequest = async (projectId, targetEmail) => {
  const res = await fetch(
    `http://localhost:8080/api/projects/${projectId}/teammates`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${localStorage.getItem("token")}`,
      },
      body: JSON.stringify({ email: targetEmail }),
    }
  );

  const data = await res.json();
  if (res.status === 201) {
    showToast("Invitation sent!");
  }
};
```

---

### GET `/api/projects/teammates/requests`

Returns all **incoming** teammate requests and rating requests for the authenticated user.

**Auth required:** ✅ Yes

**Request Body:** None

**Success Response** (`200 OK`):

```json
[
  {
    "requestId": 1,
    "projectId": 1,
    "projectTitle": "AI Chatbot Platform",
    "requesterEmail": "owner@example.com",
    "targetEmail": "user@example.com",
    "status": "PENDING",
    "type": "JOIN_REQUEST",
    "rateeEmail": null,
    "rateeName": null,
    "createdAt": "2026-02-23T14:00:00.000Z",
    "updatedAt": "2026-02-23T14:00:00.000Z"
  },
  {
    "requestId": 5,
    "projectId": 1,
    "projectTitle": "AI Chatbot Platform",
    "requesterEmail": "System",
    "targetEmail": "user@example.com",
    "status": "PENDING",
    "type": "RATING_REQUEST",
    "rateeEmail": "teammate@example.com",
    "rateeName": "Jane Smith",
    "createdAt": "2026-02-23T16:00:00.000Z",
    "updatedAt": "2026-02-23T16:00:00.000Z"
  }
]
```

**Request types:**

| `type`           | Description                                                              |
| ---------------- | ------------------------------------------------------------------------ |
| `JOIN_REQUEST`   | An invitation to join a project team (accept or reject)                  |
| `RATING_REQUEST` | Auto-generated after project completion — rate a teammate (`rateeEmail`) |

**Frontend integration:**

```javascript
const getNotifications = async () => {
  const res = await fetch(
    "http://localhost:8080/api/projects/teammates/requests",
    {
      headers: {
        Authorization: `Bearer ${localStorage.getItem("token")}`,
      },
    }
  );

  const requests = await res.json();

  // Separate by type
  const joinRequests = requests.filter((r) => r.type === "JOIN_REQUEST");
  const ratingRequests = requests.filter((r) => r.type === "RATING_REQUEST");
};
```

---

### POST `/api/projects/teammates/requests/:id/accept`

Accepts a pending teammate request. The user is added to the project team and the request is deleted.

**Auth required:** ✅ Yes (must be the target user of the request)

**URL Parameters:**

| Param | Type   | Description         |
| ----- | ------ | ------------------- |
| `id`  | number | Teammate request ID |

**Request Body:** None

**Success Response** (`200 OK`):

```json
{
  "projectId": 1,
  "memberEmail": "user@example.com",
  "addedAt": "2026-02-23T14:30:00.000Z"
}
```

**Error Responses:**

| Status | Condition                                |
| ------ | ---------------------------------------- |
| `400`  | Invalid request ID                       |
| `403`  | Not the target user                      |
| `500`  | Request not pending / already a teammate |

---

### POST `/api/projects/teammates/requests/:id/reject`

Rejects a pending teammate request. The request is deleted.

**Auth required:** ✅ Yes (must be the target user of the request)

**URL Parameters:**

| Param | Type   | Description         |
| ----- | ------ | ------------------- |
| `id`  | number | Teammate request ID |

**Request Body:** None

**Success Response** (`200 OK`):

```json
{
  "message": "Request rejected"
}
```

**Error Responses:**

| Status | Condition                   |
| ------ | --------------------------- |
| `400`  | Invalid request ID          |
| `403`  | Not the target user         |
| `500`  | Request already processed   |

---

## Quick Reference Card

| Method | Endpoint                                         | Auth | Description                          |
| ------ | ------------------------------------------------ | ---- | ------------------------------------ |
| POST   | `/auth/register`                                 | ❌   | Register new user                    |
| POST   | `/auth/login`                                    | ❌   | Login                                |
| POST   | `/api/upload`                                    | ✅   | Upload & parse resume                |
| GET    | `/api/profile`                                   | ✅   | Get my profile                       |
| GET    | `/api/profile/:id`                               | ✅   | Get user profile by ID               |
| PUT    | `/api/resume/update`                             | ✅   | Update resume                        |
| GET    | `/api/resume/download/:id`                       | ❌   | Download resume PDF by ID            |
| GET    | `/api/resume/download`                           | ✅   | Download my resume PDF               |
| POST   | `/api/projects`                                  | ✅   | Create project                       |
| GET    | `/api/projects`                                  | ✅   | List my projects                     |
| GET    | `/api/projects/explore`                          | ✅   | Explore all projects                 |
| GET    | `/api/projects/:id`                              | ✅   | Get project details + teammates      |
| POST   | `/api/projects/:id/complete`                     | ✅   | Mark project as completed            |
| POST   | `/api/projects/:id/teammates`                    | ✅   | Send teammate invite                 |
| GET    | `/api/projects/teammates/requests`               | ✅   | List my incoming requests            |
| POST   | `/api/projects/teammates/requests/:id/accept`    | ✅   | Accept teammate request              |
| POST   | `/api/projects/teammates/requests/:id/reject`    | ✅   | Reject teammate request              |

---

## CORS Configuration

The backend allows requests from these origins:

- `http://localhost:5173` (Vite dev server)
- `http://localhost:3000` (React dev server)

Allowed methods: `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`

Allowed headers: `Authorization`, `Content-Type`

> If you're running the frontend on a different port, ask the backend dev to add it to `SecurityConfig.java`.
