package sasvar.example.chatbot.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import sasvar.example.chatbot.Database.JsonData;
import sasvar.example.chatbot.Database.ProjectData;
import sasvar.example.chatbot.Exception.ProfileNotFoundException;
import sasvar.example.chatbot.Repository.JsonDataRepository;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatBotService {

    private static final Logger log = LoggerFactory.getLogger(ChatBotService.class);

    private final JsonDataRepository jsonDataRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${gemini.api.key}")
    private String apiKey;

    private static final String GEMMA_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
            + "gemini-2.5-flash:generateContent?key=%s";

    public ChatBotService(JsonDataRepository jsonDataRepository) {
        this.jsonDataRepository = jsonDataRepository;
    }

    /* ------------------------------------------------------------------ */
    /* Resume → JSON via Gemini */
    /* ------------------------------------------------------------------ */

    public String convertJSON(String resumeText) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

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
                  }
                }

                Resume Text:
                \\"\\"\\"  %s  \\"\\"\\"
                """.formatted(resumeText);

        // Proper escaping for the JSON request body
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
            ResponseEntity<String> response = restTemplate.postForEntity(
                    String.format(GEMMA_URL, apiKey), request, String.class);

            String result = extractGeminiReply(response.getBody());

            // Validate JSON before returning
            mapper.readTree(result);
            return result;

        } catch (Exception e) {
            log.error("Gemini resume conversion failed", e);
            return "{}";
        }
    }

    /* ------------------------------------------------------------------ */
    /* Save / Update profile */
    /* ------------------------------------------------------------------ */

    public JsonData saveJsonForEmail(String json,
            String email,
            String providedName,
            String providedYear,
            String providedDepartment,
            String providedInstitution,
            String providedAvailability,
            byte[] resumePdf) {

        if (email == null || email.isBlank()) {
            throw new RuntimeException("Email required to save profile");
        }

        String validJson = validateJson(json);

        JsonData profile = jsonDataRepository.findByEmail(email)
                .orElse(new JsonData());

        profile.setEmail(email);
        profile.setProfileJson(validJson);
        profile.setResumePdf(resumePdf);
        profile.setCreatedAt(Instant.now().toString());

        setIfPresent(providedName, profile::setName);
        setIfPresent(providedYear, profile::setYear);
        setIfPresent(providedDepartment, profile::setDepartment);
        setIfPresent(providedInstitution, profile::setInstitution);
        setIfPresent(providedAvailability, profile::setAvailability);

        fillMissingFromJson(profile, validJson);

        return jsonDataRepository.save(profile);
    }

    public JsonData updateResumeForEmail(String json, String email, byte[] resumePdf) {
        if (email == null || email.isBlank()) {
            throw new RuntimeException("Email required to update profile");
        }

        JsonData profile = jsonDataRepository.findByEmail(email)
                .orElseThrow(() -> new ProfileNotFoundException(
                        "Profile not found for email: " + email));

        String validJson = validateJson(json);

        profile.setProfileJson(validJson);
        if (resumePdf != null) {
            profile.setResumePdf(resumePdf);
        }
        profile.setCreatedAt(Instant.now().toString());

        return jsonDataRepository.save(profile);
    }

    /* ------------------------------------------------------------------ */
    /* Profile lookups */
    /* ------------------------------------------------------------------ */

    public JsonData getProfileByEmail(String email) {
        if (email == null)
            return null;
        return jsonDataRepository.findByEmail(email).orElse(null);
    }

    public JsonData getProfileForCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("User not authenticated — email is null");
        }
        return jsonDataRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ProfileNotFoundException(
                        "Profile not found for current user"));
    }

    public JsonData getProfileById(Long id) {
        if (id == null)
            return null;
        return jsonDataRepository.findById(id).orElse(null);
    }

    public Map<String, Object> getUserProfileById(Long id) {
        if (id == null)
            return null;

        Optional<JsonData> opt = jsonDataRepository.findById(id);
        if (opt.isEmpty())
            return null;

        JsonData p = opt.get();
        Map<String, Object> profile = new HashMap<>();
        profile.put("email", p.getEmail());
        profile.put("name", p.getName());
        profile.put("year", p.getYear());
        profile.put("department", p.getDepartment());
        profile.put("institution", p.getInstitution());
        profile.put("availability", p.getAvailability());
        profile.put("Resume", p.getProfileJson());
        if (p.getResumePdf() != null) {
            profile.put("resumePdfUrl", "/api/resume/download/" + p.getId());
        }
        return profile;
    }

    /* ------------------------------------------------------------------ */
    /* Django ML integration (best-effort) */
    /* ------------------------------------------------------------------ */

    public void sendResumeJson(JsonData profile) {
        if (profile == null)
            return;

        String resumeJsonStr = profile.getProfileJson();
        if (resumeJsonStr == null || resumeJsonStr.isBlank())
            return;

        try {
            JsonNode parsedJsonNode = mapper.readTree(resumeJsonStr);

            Map<String, Object> payload = profile.getId() != null
                    ? Map.of("resume_id", profile.getId(), "resume_json", parsedJsonNode)
                    : Map.of("parsed_json", parsedJsonNode);

            postToDjango("http://localhost:31000/api/resume/json/", payload);

        } catch (Exception e) {
            log.warn("Failed to send resume JSON to Django ML service: {}", e.getMessage());
        }
    }

    public void sendProjectAndOwnerResume(ProjectData project) {
        if (project == null)
            return;

        try {
            List<String> requiredSkillsList = csvToList(project.getRequiredSkills());
            List<String> domains = csvToList(project.getDomain());
            List<String> preferredTech = csvToList(project.getPreferredTechnologies());

            Map<String, Object> parsedJson = Map.of(
                    "title", project.getTitle(),
                    "description", project.getDescription() == null ? "" : project.getDescription(),
                    "required_skills", requiredSkillsList,
                    "preferred_technologies", preferredTech,
                    "domains", domains,
                    "project_type", project.getType(),
                    "team_size", 0,
                    "created_at", project.getCreatedAt());

            Map<String, Object> payload = Map.of(
                    "project_id", project.getId(),
                    "parsed_json", parsedJson);

            postToDjango("http://localhost:31001/api/project/embed/", payload);

        } catch (Exception e) {
            log.warn("Failed to send project JSON to Django ML service: {}", e.getMessage());
        }
    }

    /* ================================================================== */
    /* Private helpers */
    /* ================================================================== */

    private String extractGeminiReply(String responseBody) {
        try {
            JsonNode root = mapper.readTree(responseBody);

            String text = root
                    .path("candidates").get(0)
                    .path("content")
                    .path("parts").get(0)
                    .path("text")
                    .asText();

            if (text.startsWith("```json")) {
                text = text.substring(7, text.length() - 3);
            } else if (text.startsWith("```")) {
                text = text.substring(3, text.length() - 3);
            }
            return text.trim();

        } catch (Exception e) {
            throw new RuntimeException("Error parsing Gemini response", e);
        }
    }

    private String validateJson(String json) {
        if (json == null)
            return "{}";
        try {
            mapper.readTree(json);
            return json;
        } catch (Exception e) {
            log.warn("Invalid JSON received; falling back to empty object: {}", e.getMessage());
            return "{}";
        }
    }

    private void fillMissingFromJson(JsonData profile, String json) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode profileNode = root.path("profile");
            if (profileNode.isMissingNode())
                return;

            if (isBlank(profile.getName()) && profileNode.hasNonNull("name")) {
                profile.setName(profileNode.get("name").asText());
            }
            if (isBlank(profile.getYear()) && profileNode.hasNonNull("year")) {
                profile.setYear(profileNode.get("year").asText());
            }
            if (isBlank(profile.getDepartment()) && profileNode.hasNonNull("department")) {
                profile.setDepartment(profileNode.get("department").asText());
            }
            if (isBlank(profile.getInstitution()) && profileNode.hasNonNull("institution")) {
                profile.setInstitution(profileNode.get("institution").asText());
            }
            if (isBlank(profile.getAvailability()) && profileNode.hasNonNull("availability")) {
                profile.setAvailability(profileNode.get("availability").asText());
            }
        } catch (Exception e) {
            log.debug("Best-effort JSON profile extraction failed: {}", e.getMessage());
        }
    }

    private void postToDjango(String url, Map<String, Object> payload) throws Exception {
        String payloadStr = mapper.writeValueAsString(payload);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(payloadStr, headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.warn("Django ML service returned {}: {}", response.getStatusCode(), response.getBody());
        }
    }

    private List<String> csvToList(String csv) {
        if (csv == null || csv.isBlank())
            return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private void setIfPresent(String value, java.util.function.Consumer<String> setter) {
        if (value != null && !value.isBlank()) {
            setter.accept(value);
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
