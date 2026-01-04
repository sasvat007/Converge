package sasvar.example.chatbot.Controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sasvar.example.chatbot.Database.ProjectData;
import sasvar.example.chatbot.Service.ProjectService;
import sasvar.example.chatbot.Service.ProjectTeamService;
import sasvar.example.chatbot.Database.ProjectTeamRequest;
import sasvar.example.chatbot.Database.ProjectTeam;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectTeamService projectTeamService;

    public ProjectController(ProjectService projectService, ProjectTeamService projectTeamService) {
        this.projectService = projectService;
        this.projectTeamService = projectTeamService;
    }

    /**
     * Create project for the authenticated user.
     * Expects JSON:
     * {
     *   "title": "...",              // required
     *   "type": "...",               // required
     *   "visibility": "...",         // required
     *   "requiredSkills": ["a","b"] or "a,b", // required
     *   "preferredTechnologies": ["t1","t2"] or "t1,t2", // optional, NEW
     *   "domain": ["d1","d2"] or "d1,d2", // optional, ALREADY HANDLED
     *   "githubRepo": "...",         // optional
     *   "description": "..."         // required
     * }
     */
    @PostMapping
    public ResponseEntity<?> createProject(@RequestBody Map<String, Object> body) {
        try {
            String title = (String) body.get("title");
            String type = (String) body.get("type");
            String visibility = (String) body.get("visibility");

            // Accept several possible keys for incoming lists/arrays/strings
            Object reqSkillsObj = body.get("requiredSkills");
            if (reqSkillsObj == null) reqSkillsObj = body.get("required_skills");

            Object prefTechObj = body.get("preferredTechnologies");
            if (prefTechObj == null) prefTechObj = body.get("preferred_technologies");
            if (prefTechObj == null) prefTechObj = body.get("preferredSkills");
            if (prefTechObj == null) prefTechObj = body.get("preferred_skills");

            Object domainObj = body.get("domain");
            if (domainObj == null) domainObj = body.get("domains");
            if (domainObj == null) domainObj = body.get("projectDomains");
            if (domainObj == null) domainObj = body.get("domain_list");

            String githubRepo = body.getOrDefault("githubRepo", "").toString();
            String description = body.get("description") == null ? "" : body.get("description").toString();

            // basic validation
            if (title == null || title.isBlank()
                    || type == null || type.isBlank()
                    || visibility == null || visibility.isBlank()
                    || reqSkillsObj == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Missing required fields"));
            }

            // normalize inputs to CSV using helper
            String requiredSkillsCsv = toCsv(reqSkillsObj);
            String preferredTechCsv = prefTechObj == null ? "" : toCsv(prefTechObj);
            String domainCsv = domainObj == null ? "" : toCsv(domainObj);

            ProjectData saved = projectService.createProject(
                    title,
                    type,
                    visibility,
                    requiredSkillsCsv,
                    githubRepo,
                    description,
                    domainCsv,                 // pass normalized domain CSV
                    preferredTechCsv           // pass normalized preferred technologies CSV
            );

            // return created project to frontend (include owner's email)
            Map<String, Object> resp = new HashMap<>();
            resp.put("id", saved.getId());
            resp.put("title", saved.getTitle());
            resp.put("type", saved.getType());
            resp.put("visibility", saved.getVisibility());
            resp.put("requiredSkills", saved.getRequiredSkills());
            resp.put("preferredTechnologies", saved.getPreferredTechnologies());
            resp.put("githubRepo", saved.getGithubRepo());
            resp.put("description", saved.getDescription());
            resp.put("domain", saved.getDomain());
            resp.put("createdAt", saved.getCreatedAt());
            resp.put("email", saved.getEmail());

            return ResponseEntity.status(HttpStatus.CREATED).body(resp);

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Unauthorized"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to create project"));
        }
    }

    // helper: accept List or String and return comma-separated CSV (trimmed)
    private String toCsv(Object obj) {
        if (obj == null) return "";
        if (obj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) obj;
            return list.stream()
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(","));
        } else {
            String s = obj.toString().trim();
            // If the string looks like a JSON array: ["a","b"], try to clean it
            if (s.startsWith("[") && s.endsWith("]")) {
                s = s.substring(1, s.length() - 1);
            }
            // replace any occurrences of "], [" or "] , [" etc.
            s = s.replace("],", ",").replace("],", ",");
            // split by commas and re-join to normalize spacing/brackets
            return Arrays.stream(s.split(","))
                    .map(String::trim)
                    .map(x -> x.replaceAll("^\\[|\\]$", "")) // strip stray brackets
                    .filter(x -> !x.isEmpty())
                    .collect(Collectors.joining(","));
        }
    }

    // list projects for current user
    @GetMapping
    public ResponseEntity<?> listMyProjects() {
        try {
            List<ProjectData> projects = projectService.listProjectsForCurrentUser();
            List<Map<String, Object>> out = projects.stream().map(p -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", p.getId());
                m.put("title", p.getTitle());
                m.put("type", p.getType());
                m.put("visibility", p.getVisibility());
                m.put("requiredSkills", p.getRequiredSkills());
                m.put("preferredTechnologies", p.getPreferredTechnologies()); // NEW
                m.put("githubRepo", p.getGithubRepo());
                m.put("description", p.getDescription());
                m.put("domain", p.getDomain()); // include domain
                m.put("createdAt", p.getCreatedAt());
                // ✅ FIX: postedBy object (matches frontend exactly)
                Map<String, Object> postedBy = new HashMap<>();
                postedBy.put("email", p.getEmail());
                m.put("postedBy", postedBy);
                return m;
            }).collect(Collectors.toList());
            return ResponseEntity.ok(out);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Unauthorized"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to list projects"));
        }
    }

    // explore feed — list ALL projects in DB (public)
    @GetMapping("/explore")
    public ResponseEntity<?> exploreProjects() {
        try {
            List<ProjectData> projects = projectService.listAllProjects();
            List<Map<String, Object>> out = projects.stream().map(p -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", p.getId());
                m.put("title", p.getTitle());
                m.put("type", p.getType());
                m.put("visibility", p.getVisibility());
                m.put("requiredSkills", p.getRequiredSkills());
                m.put("preferredTechnologies", p.getPreferredTechnologies()); // NEW
                m.put("githubRepo", p.getGithubRepo());
                m.put("description", p.getDescription());
                m.put("domain", p.getDomain()); // include domain
                m.put("createdAt", p.getCreatedAt());
                Map<String, Object> postedBy = new HashMap<>();
                postedBy.put("email", p.getEmail());

                m.put("postedBy", postedBy);// owner email
                return m;
            }).collect(Collectors.toList());
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to fetch explore feed"));
        }
    }

    // Send teammate request (owner initiates) — NEW behavior (previously immediately added)
    // Accept id as String and validate to avoid MethodArgumentTypeMismatch for "undefined"
    @PostMapping("/{id}/teammates")
    public ResponseEntity<?> createTeammateRequest(@PathVariable("id") String projectIdStr,
                                                   @RequestBody Map<String, String> body) {
        Long projectId = parseId(projectIdStr);
        if (projectId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid project id"));
        }

        try {
            String targetEmail = body.get("email");
            if (targetEmail == null || targetEmail.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Member email required"));
            }

            ProjectTeamRequest saved = projectTeamService.createTeammateRequest(projectId, targetEmail);

            Map<String, Object> resp = new HashMap<>();
            resp.put("requestId", saved.getId());
            resp.put("projectId", saved.getProjectId());
            resp.put("requesterEmail", saved.getRequesterEmail());
            resp.put("targetEmail", saved.getTargetEmail());
            resp.put("status", saved.getStatus());
            resp.put("createdAt", saved.getCreatedAt());
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to create teammate request"));
        }
    }

    // Target user accepts a teammate request
    // Accept request id as String and validate
    @PostMapping("/teammates/requests/{id}/accept")
    public ResponseEntity<?> acceptTeammateRequest(@PathVariable("id") String requestIdStr) {
        Long requestId = parseId(requestIdStr);
        if (requestId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid request id"));
        }

        try {
            ProjectTeam saved = projectTeamService.acceptTeammateRequest(requestId);
            Map<String, Object> resp = new HashMap<>();
            resp.put("projectId", saved.getProjectId());
            resp.put("memberEmail", saved.getMemberEmail());
            resp.put("addedAt", saved.getAddedAt());
            return ResponseEntity.ok(resp);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Failed to accept request"));
        }
    }

    // Target user rejects a teammate request
    @PostMapping("/teammates/requests/{id}/reject")
    public ResponseEntity<?> rejectTeammateRequest(@PathVariable("id") String requestIdStr) {
        Long requestId = parseId(requestIdStr);
        if (requestId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid request id"));
        }

        try {
            projectTeamService.rejectTeammateRequest(requestId);
            return ResponseEntity.ok(Map.of("message", "Request rejected"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Failed to reject request"));
        }
    }

    // List incoming teammate requests for current user
    @GetMapping("/teammates/requests")
    public ResponseEntity<?> listIncomingRequests() {
        try {
            List<ProjectTeamRequest> reqs = projectTeamService.listIncomingRequestsForCurrentUser();
            List<Map<String, Object>> out = reqs.stream().map(r -> {
                Map<String, Object> m = new HashMap<>();
                m.put("requestId", r.getId());
                m.put("projectId", r.getProjectId());
                m.put("requesterEmail", r.getRequesterEmail());
                m.put("targetEmail", r.getTargetEmail());
                m.put("status", r.getStatus());
                m.put("createdAt", r.getCreatedAt());
                m.put("updatedAt", r.getUpdatedAt());
                return m;
            }).collect(Collectors.toList());
            return ResponseEntity.ok(out);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Failed to list requests"));
        }
    }

    // Get single project details including stored teammates
    @GetMapping("/{id}")
    public ResponseEntity<?> getProject(@PathVariable("id") String projectIdStr) {
        Long projectId = parseId(projectIdStr);
        if (projectId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid project id"));
        }

        try {
            ProjectData p = projectService.getProjectById(projectId);
            if (p == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Project not found"));
            }

            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId());
            m.put("title", p.getTitle());
            m.put("type", p.getType());
            m.put("visibility", p.getVisibility());
            m.put("requiredSkills", p.getRequiredSkills());
            m.put("preferredTechnologies", p.getPreferredTechnologies());
            m.put("githubRepo", p.getGithubRepo());
            m.put("description", p.getDescription());
            m.put("domain", p.getDomain());
            m.put("createdAt", p.getCreatedAt());
            m.put("email", p.getEmail());

            // include teammates
            var teammates = projectTeamService.listTeammatesForProject(projectId);
            m.put("teammates", teammates);

            return ResponseEntity.ok(m);

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Failed to fetch project"));
        }
    }

    // Mark project as completed and delete from DB (owner only)
    @PostMapping("/{id}/complete")
    public ResponseEntity<?> completeProject(@PathVariable("id") String projectIdStr) {
        Long projectId = parseId(projectIdStr);
        if (projectId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid project id"));
        }

        try {
            projectService.deleteProjectAsCompleted(projectId);
            return ResponseEntity.ok(Map.of("message", "Project marked as completed and removed"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to complete project"));
        }
    }

    // helper to parse path variable id strings to Long; returns null when invalid
    private Long parseId(String idStr) {
        if (idStr == null) return null;
        idStr = idStr.trim();
        if (idStr.isEmpty() || "undefined".equalsIgnoreCase(idStr) || "null".equalsIgnoreCase(idStr)) {
            return null;
        }
        try {
            return Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
