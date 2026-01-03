package sasvar.example.chatbot.Controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sasvar.example.chatbot.Database.ProjectData;
import sasvar.example.chatbot.Service.ProjectService;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * Create project for the authenticated user.
     * Expects JSON:
     * {
     *   "title": "...",              // required
     *   "type": "...",               // required
     *   "visibility": "...",         // required
     *   "requiredSkills": ["a","b"] or "a,b", // required
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
            Object reqSkillsObj = body.get("requiredSkills");
            String githubRepo = body.getOrDefault("githubRepo", "").toString();
            String description = body.get("description") == null ? "" : body.get("description").toString();
            String domain = body.getOrDefault("domain", "").toString(); // new: read domain

            // basic validation
            if (title == null || title.isBlank()
                    || type == null || type.isBlank()
                    || visibility == null || visibility.isBlank()
                    || reqSkillsObj == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Missing required fields"));
            }

            // normalize requiredSkills to CSV
            String requiredSkillsCsv;
            if (reqSkillsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) reqSkillsObj;
                requiredSkillsCsv = list.stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(","));
            } else {
                requiredSkillsCsv = reqSkillsObj.toString();
            }

            ProjectData saved = projectService.createProject(
                    title, type, visibility, requiredSkillsCsv, githubRepo, description, domain // pass domain
            );

            // return created project to frontend (include owner's email)
            Map<String, Object> resp = new HashMap<>();
            resp.put("id", saved.getId());
            resp.put("title", saved.getTitle());
            resp.put("type", saved.getType());
            resp.put("visibility", saved.getVisibility());
            resp.put("requiredSkills", saved.getRequiredSkills());
            resp.put("githubRepo", saved.getGithubRepo());
            resp.put("description", saved.getDescription());
            resp.put("domain", saved.getDomain()); // include domain
            resp.put("createdAt", saved.getCreatedAt());
            resp.put("email", saved.getEmail()); // <-- owner email included

            return ResponseEntity.status(HttpStatus.CREATED).body(resp);

        } catch (RuntimeException e) {
            // likely authentication error
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Unauthorized"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to create project"));
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
                m.put("githubRepo", p.getGithubRepo());
                m.put("description", p.getDescription());
                m.put("domain", p.getDomain()); // include domain
                m.put("createdAt", p.getCreatedAt());
                m.put("email", p.getEmail()); // <-- include owner email here too
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
                m.put("githubRepo", p.getGithubRepo());
                m.put("description", p.getDescription());
                m.put("domain", p.getDomain()); // include domain
                m.put("createdAt", p.getCreatedAt());
                m.put("email", p.getEmail()); // owner email
                return m;
            }).collect(Collectors.toList());
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to fetch explore feed"));
        }
    }
}
