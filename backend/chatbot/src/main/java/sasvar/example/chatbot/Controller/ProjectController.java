package sasvar.example.chatbot.Controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sasvar.example.chatbot.Database.ProjectData;
import sasvar.example.chatbot.Database.ProjectTeam;
import sasvar.example.chatbot.Database.ProjectTeamRequest;
import sasvar.example.chatbot.Service.ProjectService;
import sasvar.example.chatbot.Service.ProjectTeamService;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects", description = "Create, explore, and manage collaborative projects")
public class ProjectController {

    private static final Logger log = LoggerFactory.getLogger(ProjectController.class);

    private final ProjectService projectService;
    private final ProjectTeamService projectTeamService;

    public ProjectController(ProjectService projectService,
            ProjectTeamService projectTeamService) {
        this.projectService = projectService;
        this.projectTeamService = projectTeamService;
    }

    /* ------------------------------------------------------------------ */
    /* POST /api/projects */
    /* ------------------------------------------------------------------ */

    @Operation(summary = "Create a new project", description = """
            Creates a project for the authenticated user. Requires title, type,
            visibility, and required skills. Optionally accepts preferred
            technologies, domains, GitHub repo URL, and a text description.""")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Project creation payload", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = Object.class), examples = @ExampleObject(value = """
            {
              "title": "AI Chatbot Platform",
              "type": "Software Development",
              "visibility": "public",
              "requiredSkills": ["Java", "Spring Boot", "React"],
              "preferredTechnologies": ["Docker", "Kubernetes"],
              "domain": ["AI/ML", "Web Development"],
              "githubRepo": "https://github.com/user/project",
              "description": "An intelligent chatbot platform built with modern tech."
            }""")))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Project created successfully"),
            @ApiResponse(responseCode = "400", description = "Missing required fields"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping
    public ResponseEntity<?> createProject(@RequestBody Map<String, Object> body) {

        String title = (String) body.get("title");
        String type = (String) body.get("type");
        String visibility = (String) body.get("visibility");

        Object reqSkillsObj = firstNonNull(body, "requiredSkills", "required_skills");
        Object prefTechObj = firstNonNull(body, "preferredTechnologies",
                "preferred_technologies", "preferredSkills", "preferred_skills");
        Object domainObj = firstNonNull(body, "domain", "domains",
                "projectDomains", "domain_list");

        String githubRepo = String.valueOf(body.getOrDefault("githubRepo", ""));
        String description = body.get("description") == null
                ? ""
                : body.get("description").toString();

        if (isBlank(title) || isBlank(type) || isBlank(visibility) || reqSkillsObj == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Missing required fields"));
        }

        String requiredSkillsCsv = toCsv(reqSkillsObj);
        String preferredTechCsv = prefTechObj == null ? "" : toCsv(prefTechObj);
        String domainCsv = domainObj == null ? "" : toCsv(domainObj);

        ProjectData saved = projectService.createProject(
                title, type, visibility, requiredSkillsCsv,
                githubRepo, description, domainCsv, preferredTechCsv);

        return ResponseEntity.status(HttpStatus.CREATED).body(buildProjectMap(saved));
    }

    /* ------------------------------------------------------------------ */
    /* GET /api/projects */
    /* ------------------------------------------------------------------ */

    @Operation(summary = "List my projects", description = "Returns all projects owned by the currently authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of projects returned"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping
    public ResponseEntity<?> listMyProjects() {

        List<ProjectData> projects = projectService.listProjectsForCurrentUser();

        List<Map<String, Object>> out = projects.stream()
                .map(this::buildProjectMapWithPostedBy)
                .collect(Collectors.toList());

        return ResponseEntity.ok(out);
    }

    /* ------------------------------------------------------------------ */
    /* GET /api/projects/explore */
    /* ------------------------------------------------------------------ */

    @Operation(summary = "Explore all projects", description = "Returns all projects in the system (public explore feed).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of all projects returned"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/explore")
    public ResponseEntity<?> exploreProjects() {

        List<ProjectData> projects = projectService.listAllProjects();

        List<Map<String, Object>> out = projects.stream()
                .map(this::buildProjectMapWithPostedBy)
                .collect(Collectors.toList());

        return ResponseEntity.ok(out);
    }

    /* ------------------------------------------------------------------ */
    /* POST /api/projects/{id}/teammates */
    /* ------------------------------------------------------------------ */

    @Operation(summary = "Send teammate request", description = """
            Project owner sends an invitation to a user (by email) to join
            the project team. The target user must accept.""")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Target user email", required = true, content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
            { "email": "teammate@example.com" }""")))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Teammate request created"),
            @ApiResponse(responseCode = "400", description = "Invalid project ID or missing email"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/{id}/teammates")
    public ResponseEntity<?> createTeammateRequest(
            @Parameter(description = "Project ID") @PathVariable("id") String projectIdStr,
            @RequestBody Map<String, String> body) {

        Long projectId = parseId(projectIdStr);
        if (projectId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Invalid project id"));
        }

        String targetEmail = body.get("email");
        if (targetEmail == null || targetEmail.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Member email required"));
        }

        ProjectTeamRequest saved = projectTeamService
                .createTeammateRequest(projectId, targetEmail);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("requestId", saved.getId());
        resp.put("projectId", saved.getProjectId());
        resp.put("requesterEmail", saved.getRequesterEmail());
        resp.put("targetEmail", saved.getTargetEmail());
        resp.put("status", saved.getStatus());
        resp.put("createdAt", saved.getCreatedAt());

        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    /* ------------------------------------------------------------------ */
    /* POST /api/projects/teammates/requests/{id}/accept */
    /* ------------------------------------------------------------------ */

    @Operation(summary = "Accept teammate request", description = "Target user accepts a pending teammate invitation. The user is added to the project team.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Request accepted; teammate added to project"),
            @ApiResponse(responseCode = "400", description = "Invalid request ID"),
            @ApiResponse(responseCode = "403", description = "Forbidden – not the target user"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/teammates/requests/{id}/accept")
    public ResponseEntity<?> acceptTeammateRequest(
            @Parameter(description = "Teammate request ID") @PathVariable("id") String requestIdStr) {

        Long requestId = parseId(requestIdStr);
        if (requestId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Invalid request id"));
        }

        ProjectTeam saved = projectTeamService.acceptTeammateRequest(requestId);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("projectId", saved.getProjectId());
        resp.put("memberEmail", saved.getMemberEmail());
        resp.put("addedAt", saved.getAddedAt());

        return ResponseEntity.ok(resp);
    }

    /* ------------------------------------------------------------------ */
    /* POST /api/projects/teammates/requests/{id}/reject */
    /* ------------------------------------------------------------------ */

    @Operation(summary = "Reject teammate request", description = "Target user rejects a pending teammate invitation.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Request rejected"),
            @ApiResponse(responseCode = "400", description = "Invalid request ID"),
            @ApiResponse(responseCode = "403", description = "Forbidden – not the target user"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/teammates/requests/{id}/reject")
    public ResponseEntity<?> rejectTeammateRequest(
            @Parameter(description = "Teammate request ID") @PathVariable("id") String requestIdStr) {

        Long requestId = parseId(requestIdStr);
        if (requestId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Invalid request id"));
        }

        projectTeamService.rejectTeammateRequest(requestId);
        return ResponseEntity.ok(Map.of("message", "Request rejected"));
    }

    /* ------------------------------------------------------------------ */
    /* GET /api/projects/teammates/requests */
    /* ------------------------------------------------------------------ */

    @Operation(summary = "List incoming teammate requests", description = "Returns all pending teammate/rating requests addressed to the authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of incoming requests"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/teammates/requests")
    public ResponseEntity<?> listIncomingRequests() {

        List<ProjectTeamRequest> reqs = projectTeamService
                .listIncomingRequestsForCurrentUser();

        List<Map<String, Object>> out = reqs.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("requestId", r.getId());
            m.put("projectId", r.getProjectId());
            m.put("projectTitle", r.getProjectTitle());
            m.put("requesterEmail", r.getRequesterEmail());
            m.put("targetEmail", r.getTargetEmail());
            m.put("status", r.getStatus());
            m.put("type", r.getType());
            m.put("rateeEmail", r.getRateeEmail());
            m.put("rateeName", r.getRateeName());
            m.put("createdAt", r.getCreatedAt());
            m.put("updatedAt", r.getUpdatedAt());
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(out);
    }

    /* ------------------------------------------------------------------ */
    /* GET /api/projects/{id} */
    /* ------------------------------------------------------------------ */

    @Operation(summary = "Get project details", description = "Returns details of a single project by ID, including its current teammates.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Project details returned"),
            @ApiResponse(responseCode = "400", description = "Invalid project ID"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Project not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/{id}")
    public ResponseEntity<?> getProject(
            @Parameter(description = "Project ID") @PathVariable("id") String projectIdStr) {

        Long projectId = parseId(projectIdStr);
        if (projectId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Invalid project id"));
        }

        ProjectData p = projectService.getProjectById(projectId);
        if (p == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Project not found"));
        }

        Map<String, Object> m = buildProjectMap(p);
        m.put("teammates", projectTeamService.listTeammatesForProject(projectId));

        return ResponseEntity.ok(m);
    }

    /* ------------------------------------------------------------------ */
    /* POST /api/projects/{id}/complete */
    /* ------------------------------------------------------------------ */

    @Operation(summary = "Mark project as completed", description = "Project owner marks a project as completed. Only the owner can perform this action.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Project marked as completed"),
            @ApiResponse(responseCode = "400", description = "Invalid project ID"),
            @ApiResponse(responseCode = "403", description = "Only the owner can complete this project"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/{id}/complete")
    public ResponseEntity<?> completeProject(
            @Parameter(description = "Project ID") @PathVariable("id") String projectIdStr) {

        Long projectId = parseId(projectIdStr);
        if (projectId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Invalid project id"));
        }

        ProjectData updatedProject = projectService.completeProject(projectId);

        return ResponseEntity.ok(Map.of(
                "message", "Project marked as completed",
                "project", updatedProject));
    }

    /* ================================================================== */
    /* Private helpers */
    /* ================================================================== */

    /** Build a response map for a single project (owner view). */
    private Map<String, Object> buildProjectMap(ProjectData p) {
        Map<String, Object> m = new LinkedHashMap<>();
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
        m.put("status", p.getStatus());
        return m;
    }

    /**
     * Build a response map for a project with {@code postedBy} wrapper (feed view).
     */
    private Map<String, Object> buildProjectMapWithPostedBy(ProjectData p) {
        Map<String, Object> m = buildProjectMap(p);
        m.put("postedBy", Map.of("email", p.getEmail()));
        return m;
    }

    /** Return the first non-null value for the given keys. */
    private Object firstNonNull(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val != null)
                return val;
        }
        return null;
    }

    /**
     * Convert a {@code List} or comma-separated {@code String} to a normalised CSV.
     */
    @SuppressWarnings("unchecked")
    private String toCsv(Object obj) {
        if (obj == null)
            return "";

        if (obj instanceof List) {
            return ((List<Object>) obj).stream()
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(","));
        }

        String s = obj.toString().trim();
        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1);
        }
        s = s.replace("],", ",");

        return Arrays.stream(s.split(","))
                .map(String::trim)
                .map(x -> x.replaceAll("^\\[|\\]$", ""))
                .filter(x -> !x.isEmpty())
                .collect(Collectors.joining(","));
    }

    /**
     * Parse a path-variable ID string to {@code Long}; returns {@code null} on
     * failure.
     */
    private Long parseId(String idStr) {
        if (idStr == null)
            return null;
        idStr = idStr.trim();
        if (idStr.isEmpty()
                || "undefined".equalsIgnoreCase(idStr)
                || "null".equalsIgnoreCase(idStr)) {
            return null;
        }
        try {
            return Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
