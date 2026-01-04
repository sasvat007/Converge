package sasvar.example.chatbot.Controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sasvar.example.chatbot.Service.ChatBotService;
import sasvar.example.chatbot.Database.JsonData;
import sasvar.example.chatbot.Exception.ProfileNotFoundException;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChatBotController {

    private final ChatBotService chatBotService;

    public ChatBotController(ChatBotService chatBotService) {
        this.chatBotService = chatBotService;
    }

    /**
     * Parse resume → store JSON in DB → return stored profile (only top-level fields)
     * Frontend should send JSON:
     * {
     *   "resumeText": "...",
     *   "name": "...",                // optional: provided profile fields
     *   "year": "...",
     *   "department": "...",
     *   "institution": "...",
     *   "availability": "low|medium|high"
     * }
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadResume(@RequestBody Map<String, Object> request) {
        try {
            String resumeText = (String) request.get("resumeText");
            String name = (String) request.get("name");
            String year = (String) request.get("year");
            String department = (String) request.get("department");
            String institution = (String) request.get("institution");
            String availability = (String) request.get("availability");

            // get current user email
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
            }
            String email = auth.getName();

            String json = "{}";
            if (resumeText != null && !resumeText.isBlank()) {
                json = chatBotService.convertJSON(resumeText);
            }

            // Use reverted saveJsonForEmail signature (no PDF)
            JsonData saved = chatBotService.saveJsonForEmail(json, email, name, year, department, institution, availability);

            Map<String, Object> profile = new HashMap<>();
            profile.put("email", saved.getEmail());
            profile.put("name", saved.getName());
            profile.put("year", saved.getYear());
            profile.put("department", saved.getDepartment());
            profile.put("institution", saved.getInstitution());
            profile.put("availability", saved.getAvailability());

            try { chatBotService.sendResumeJson(saved); } catch (Exception ignored) {}

            return ResponseEntity.ok(profile);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Failed to upload and parse resume"));
        }
    }

    // Return current user's profile (only top-level fields — exclude parsed JSON)
    @GetMapping("/profile")
    public ResponseEntity<?> getCurrentUserProfile() {
        try {
            JsonData data = chatBotService.getProfileForCurrentUser();
            Map<String, Object> profile = new HashMap<>();
            profile.put("id", data.getId());                           // ✅ ADD: user's JsonData.id
            profile.put("email", data.getEmail());
            profile.put("name", data.getName());
            profile.put("year", data.getYear());
            profile.put("department", data.getDepartment());
            profile.put("institution", data.getInstitution());
            profile.put("availability", data.getAvailability());
            profile.put("Resume", data.getProfileJson()); // include parsed resume JSON

            return ResponseEntity.ok(profile);
        } catch (ProfileNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Profile not found"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Failed to fetch profile"));
        }
    }

    @GetMapping("/profile/{id}")
    public ResponseEntity<?> getUserProfileById(@PathVariable Long id) {
        try {
            var profile = chatBotService.getUserProfileById(id); // or userService
            if (profile == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "User not found"));
            }
            return ResponseEntity.ok(profile);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied"));
        }
    }

}
