package sasvar.example.chatbot.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import sasvar.example.chatbot.JsonData;
import sasvar.example.chatbot.Repository.JsonDataRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

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

SON Schema:
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

    public JsonData saveJson(String json) {
        JsonData data = new JsonData();
        data.setProfileJson(json);
        data.setCreatedAt(Instant.now().toString());
        return jsonDataRepository.save(data);
    }

    public JsonData getById(Long id) {
        return jsonDataRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Profile not found"));
    }
}
