package sasvar.example.chatbot.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import sasvar.example.chatbot.Database.JsonData;
import sasvar.example.chatbot.Exception.ProfileNotFoundException;
import sasvar.example.chatbot.Repository.JsonDataRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import sasvar.example.chatbot.Database.ProjectData;

import java.time.Instant;
import java.util.Optional;
import java.util.Map;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

@Service
public class ChatBotService {

    @Autowired
    private JsonDataRepository jsonDataRepository;

    @Value("${gemini.api.key}")
    private String apiKey;

    // ✅ Stable & recommended
    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/" +
                    "gemini-2.5-flash:generateContent?key=%s";

    public String convertJSON(String resumeText) {

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // ✅ Improved prompt (allows inference)
        String prompt = """
You are an AI resume parser.

Extract structured information from the resume text below.

Rules:
- Infer name, email, phone, skills, and education if clearly present.
- Do NOT leave fields empty when information is visible.
- Only leave fields empty if information is truly missing.
- Return ONLY valid minified JSON.
- Do NOT include explanations, markdown, or extra text.

JSON Schema:
{
  "profile": {
    "name": "",
    "year": "",
    "department": "",
    "availability": "low | medium | high"
  },

  "skills": {
    "programming_languages": [],
    "frameworks_libraries": [],
    "tools_platforms": [],
    "core_cs_concepts": [],
    "domain_skills": []
  },

  "experience_level": {
    "overall": "beginner | intermediate | advanced",
    "by_domain": {
      "web_dev": "beginner | intermediate | advanced",
      "ml_ai": "beginner | intermediate | advanced",
      "systems": "beginner | intermediate | advanced",
      "security": "beginner | intermediate | advanced"
    }
  },

  "projects": [
    {
      "title": "",
      "description": "",
      "technologies": [],
      "domain": "",
      "role": "",
      "completion_status": "completed | ongoing"
    }
  ],

  "interests": {
    "technical": [],
    "problem_domains": [],
    "learning_goals": []
  },

  "open_source": {
    "experience": "none | beginner | active | maintainer",
    "technologies": [],
    "contributions": 0
  },

  "achievements": {
    "hackathons": [],
    "certifications": [],
    "awards": []
  },

  "reputation_signals": {
    "completed_projects": 0,
    "average_rating": 0.0,
    "peer_endorsements": 0
  },
}

Resume Text:
\"\"\"%s\"\"\"
""".formatted(resumeText);

        // ✅ Proper escaping (VERY IMPORTANT)
        String escapedPrompt = prompt
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");

        String body = """
        {
          "contents": [
            {
              "parts": [
                { "text": "%s" }
              ]
            }
          ]
        }
        """.formatted(escapedPrompt);

        HttpEntity<String> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response =
                    restTemplate.postForEntity(
                            String.format(GEMINI_URL, apiKey),
                            request,
                            String.class
                    );

            String result = extractGeminiReply(response.getBody());

            // ✅ Validate JSON before returning
            new ObjectMapper().readTree(result);

            return result;

        } catch (Exception e) {
            e.printStackTrace();
            return "Error calling Gemini API";
        }
    }

    private String extractGeminiReply(String responseBody) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(responseBody);

            return root
                    .path("candidates")
                    .get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text")
                    .asText();

        } catch (Exception e) {
            throw new RuntimeException("Error parsing Gemini response", e);
        }
    }


    // New: save parsed JSON and profile fields for a specific email (used during registration)
    public JsonData saveJsonForEmail(String json,
                                    String email,
                                    String providedName,
                                    String providedYear,
                                    String providedDepartment,
                                    String providedInstitution,
                                    String providedAvailability) {

        // Use provided email (no SecurityContext required)
        if (email == null || email.isBlank()) {
            throw new RuntimeException("Email required to save profile");
        }

        JsonData profile = jsonDataRepository.findByEmail(email)
                .orElse(new JsonData());

        profile.setEmail(email);
        profile.setProfileJson(json);
        profile.setCreatedAt(Instant.now().toString());

        if (providedName != null && !providedName.isBlank()) {
            profile.setName(providedName);
        }
        if (providedYear != null && !providedYear.isBlank()) {
            profile.setYear(providedYear);
        }
        if (providedDepartment != null && !providedDepartment.isBlank()) {
            profile.setDepartment(providedDepartment);
        }
        if (providedInstitution != null && !providedInstitution.isBlank()) {
            profile.setInstitution(providedInstitution);
        }
        if (providedAvailability != null && !providedAvailability.isBlank()) {
            profile.setAvailability(providedAvailability);
        }

        // For any missing fields, try to extract from parsed JSON
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);
            JsonNode profileNode = root.path("profile");
            if (!profileNode.isMissingNode()) {
                if ((profile.getName() == null || profile.getName().isBlank())
                        && profileNode.hasNonNull("name")) {
                    profile.setName(profileNode.get("name").asText());
                }
                if ((profile.getYear() == null || profile.getYear().isBlank())
                        && profileNode.hasNonNull("year")) {
                    profile.setYear(profileNode.get("year").asText());
                }
                if ((profile.getDepartment() == null || profile.getDepartment().isBlank())
                        && profileNode.hasNonNull("department")) {
                    profile.setDepartment(profileNode.get("department").asText());
                }
                if ((profile.getInstitution() == null || profile.getInstitution().isBlank())
                        && profileNode.hasNonNull("institution")) {
                    profile.setInstitution(profileNode.get("institution").asText());
                }
                if ((profile.getAvailability() == null || profile.getAvailability().isBlank())
                        && profileNode.hasNonNull("availability")) {
                    profile.setAvailability(profileNode.get("availability").asText());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return jsonDataRepository.save(profile);
    }

    // New helper: fetch profile by email (used after login)
    public JsonData getProfileByEmail(String email) {
        if (email == null) return null;
        Optional<JsonData> opt = jsonDataRepository.findByEmail(email);
        return opt.orElse(null);
    }


    // New helper: get profile for currently authenticated user
    public JsonData getProfileForCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("User not authenticated — email is null");
        }
        String email = auth.getName();
        return jsonDataRepository.findByEmail(email)
                .orElseThrow(() -> new ProfileNotFoundException(-1L));
    }



    // New: send parsed resume JSON (best-effort) to Django ML resume endpoint
    public void sendResumeJson(JsonData profile) {
        if (profile == null) {
            return;
        }
        String resumeJsonStr = profile.getProfileJson();
        if (resumeJsonStr == null || resumeJsonStr.isBlank()) {
            return;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            // parse profile JSON string to JsonNode so we can embed as an object
            JsonNode parsedJsonNode = mapper.readTree(resumeJsonStr);

            // Build payload: include resume_id when we have a DB id
            Map<String, Object> payload;
            if (profile.getId() != null) {
                payload = Map.of(
                        "resume_id", profile.getId(),
                        "resume_json", parsedJsonNode
                );
            } else {
                payload = Map.of(
                        "parsed_json", parsedJsonNode
                );
            }

            String payloadStr = mapper.writeValueAsString(payload);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(payloadStr, headers);
            RestTemplate restTemplate = new RestTemplate();

            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://fundamentally-historiographic-leif.ngrok-free.dev/api/resume/json/",
                    request,
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                System.out.println("Failed to send resume JSON to Django ML service: "
                        + response.getStatusCode() + " " + response.getBody());
            }

        } catch (Exception e) {
            System.out.println("Failed to send resume JSON to Django ML service: " + e.getMessage());
            // keep it best-effort — do not throw
        }
    }

    // New: send project JSON to Django ML endpoint
    // NOTE: this method no longer sends the owner's resume JSON.
    public void sendProjectAndOwnerResume(ProjectData project) {
        if (project == null) return;

        ObjectMapper mapper = new ObjectMapper();
        try {
            // Prepare required_skills array from comma-separated string
            List<String> requiredSkillsList = List.of();
            if (project.getRequiredSkills() != null && !project.getRequiredSkills().isBlank()) {
                requiredSkillsList = Arrays.stream(project.getRequiredSkills().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
            }

            // domains array (use domain CSV if present)
            List<String> domains = List.of();
            if (project.getDomain() != null && !project.getDomain().isBlank()) {
                domains = Arrays.stream(project.getDomain().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
            }

            // preferred_technologies array (from CSV)
            List<String> preferredTech = List.of();
            if (project.getPreferredTechnologies() != null && !project.getPreferredTechnologies().isBlank()) {
                preferredTech = Arrays.stream(project.getPreferredTechnologies().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
            }

            // Build parsed_json object following the structure in your curl example
            Map<String, Object> parsedJson = Map.of(
                    "title", project.getTitle(),
                    "description", project.getDescription() == null ? "" : project.getDescription(),
                    "required_skills", requiredSkillsList,
                    "preferred_technologies", preferredTech,
                    "domains", domains,
                    "project_type", project.getType(),
                    "team_size", 0, // optional; set 0 if unknown
                    "created_at", project.getCreatedAt()
            );

            // IMPORTANT: send numeric project_id (Long) — Django expects an integer
            Map<String, Object> payload = Map.of(
                    "project_id", project.getId(),
                    "parsed_json", parsedJson
            );

            String projectJson = mapper.writeValueAsString(payload);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(projectJson, headers);
            RestTemplate restTemplate = new RestTemplate();

            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://fundamentally-historiographic-leif.ngrok-free.dev/api/project/embed/",
                    request,
                    String.class
            );
            System.out.println(payload);
            System.out.println("Sent project JSON to Django ML service, response: "
                    + response.getStatusCode() + " - " + response.getBody());

            if (!response.getStatusCode().is2xxSuccessful()) {
                System.out.println("Failed to send project JSON to Django ML service: "
                        + response.getStatusCode() + " - " + response.getBody());
            }

        } catch (Exception e) {
            System.out.println("Failed to send project JSON to Django ML service: " + e.getMessage());
        }

        // Removed: previously the owner's resume JSON was looked up and sent here.
    }

}
