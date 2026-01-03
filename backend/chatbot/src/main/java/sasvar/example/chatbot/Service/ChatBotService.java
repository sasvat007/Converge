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

import java.time.Instant;
import java.util.Optional;

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
    "user_id": "",
    "name": "",
    "year": "",
    "department": "",
    "institution": "",
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
      "team_size": 0,
      "completion_status": "completed | ongoing"
    }
  ],

  "interests": {
    "technical": [],
    "problem_domains": [],
    "learning_goals": []
  },

  "collaboration_preferences": {
    "roles_preferred": [],
    "project_types": ["hackathon", "research", "startup", "open_source"],
    "team_size_preference": ""
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

  "embeddings": {
    "skill_embedding_id": "",
    "project_embedding_id": "",
    "interest_embedding_id": ""
  }
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


    public JsonData saveJson(String json,
                             String providedName,
                             String providedYear,
                             String providedDepartment,
                             String providedInstitution,
                             String providedAvailability) {

        // 1️⃣ Get authentication from Spring Security
        var auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("User not authenticated — email is null");
        }

        String email = auth.getName();

        // 2️⃣ Check if resume/profile already exists for this user
        JsonData profile = jsonDataRepository.findByEmail(email)
                .orElse(new JsonData());

        // 3️⃣ Set required fields (keep existing profile fields if present)
        profile.setEmail(email);                // ensure link to user
        profile.setProfileJson(json);
        profile.setCreatedAt(Instant.now().toString());

        // 3.1️⃣ If user provided top-level profile fields, prefer those.
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

        // 3.2️⃣ For any fields NOT provided by user, try to extract from parsed JSON
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
            // ignore extraction errors; JSON still saved
            e.printStackTrace();
        }

        // 4️⃣ Save to DB
        return jsonDataRepository.save(profile);
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

    public JsonData getById(Long id) {
        return jsonDataRepository.findById(id)
                .orElseThrow(() -> new ProfileNotFoundException(id));
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

    public void sendjsontodjango(Long id){
        JsonData data = getById(id);
        String resumejson=data.getProfileJson(); //from table getting the json
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request =
                    new HttpEntity<>(resumejson, headers);

            // 3️⃣ Send to Django ML service
            RestTemplate restTemplate = new RestTemplate();

            ResponseEntity<String> response =
                    restTemplate.postForEntity(
                            "http://localhost:8000/api/process-resume/",
                            request,
                            String.class
                    );
        }catch (Exception e){
        // 4️⃣ Log Django response (for now)
        System.out.println("ML Service not running");
        }

    }

}
