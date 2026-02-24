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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import sasvar.example.chatbot.Database.JsonData;
import sasvar.example.chatbot.Exception.ProfileNotFoundException;
import sasvar.example.chatbot.Service.ChatBotService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Profile & Resume", description = "Upload, view, update, and download user profiles and resumes")
public class ChatBotController {

    private static final Logger log = LoggerFactory.getLogger(ChatBotController.class);

    private final ChatBotService chatBotService;

    public ChatBotController(ChatBotService chatBotService) {
        this.chatBotService = chatBotService;
    }

    /* ------------------------------------------------------------------ */
    /* POST /api/upload */
    /* ------------------------------------------------------------------ */

    @Operation(summary = "Upload & parse resume", description = """
            Accepts resume text (and optional base64-encoded PDF), parses it
            via Gemini AI, and stores the resulting profile JSON in the database.
            Returns the saved profile fields.""")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Resume upload payload", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = Object.class), examples = @ExampleObject(value = """
            {
              "resumeText": "John Doe, 3 years experience in Java...",
              "resumePdf": "<base64-encoded-pdf>",
              "name": "John Doe",
              "year": "3rd Year",
              "department": "Computer Science",
              "institution": "MIT",
              "availability": "high"
            }""")))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resume parsed and profile saved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized – missing or invalid JWT"),
            @ApiResponse(responseCode = "500", description = "Internal error during parsing or saving")
    })
    @PostMapping("/upload")
    public ResponseEntity<?> uploadResume(@RequestBody Map<String, Object> request) {

        String email = getAuthenticatedEmail();

        String resumeText = (String) request.get("resumeText");
        String resumePdf64 = (String) request.get("resumePdf");
        String name = (String) request.get("name");
        String year = (String) request.get("year");
        String department = (String) request.get("department");
        String institution = (String) request.get("institution");
        String availability = (String) request.get("availability");

        String json = "{}";
        if (resumeText != null && !resumeText.isBlank()) {
            json = chatBotService.convertJSON(resumeText);
        }

        byte[] pdfBytes = decodePdf(resumePdf64);

        JsonData saved = chatBotService.saveJsonForEmail(
                json, email, name, year, department, institution, availability, pdfBytes);

        sendResumeBestEffort(saved);

        return ResponseEntity.ok(buildProfileMap(saved));
    }

    /* ------------------------------------------------------------------ */
    /* GET /api/profile */
    /* ------------------------------------------------------------------ */

    @Operation(summary = "Get current user's profile", description = "Returns the authenticated user's profile fields including the parsed resume JSON.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Profile not found for the current user"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/profile")
    public ResponseEntity<?> getCurrentUserProfile() {

        JsonData data = chatBotService.getProfileForCurrentUser();

        Map<String, Object> profile = buildProfileMap(data);
        profile.put("id", data.getId());
        profile.put("Resume", data.getProfileJson());

        return ResponseEntity.ok(profile);
    }

    /* ------------------------------------------------------------------ */
    /* GET /api/resume/download/{id} */
    /* ------------------------------------------------------------------ */

    @Operation(summary = "Download resume PDF by profile ID", description = "Returns the stored resume PDF as a downloadable binary file.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF file returned", content = @Content(mediaType = "application/pdf")),
            @ApiResponse(responseCode = "404", description = "Resume PDF not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/resume/download/{id}")
    public ResponseEntity<?> downloadResumePdf(
            @Parameter(description = "Profile ID") @PathVariable Long id) {

        JsonData data = chatBotService.getProfileById(id);
        if (data == null || data.getResumePdf() == null) {
            throw new ProfileNotFoundException("Resume PDF not found for id: " + id);
        }

        return buildPdfResponse(data);
    }

    /* ------------------------------------------------------------------ */
    /* GET /api/resume/download */
    /* ------------------------------------------------------------------ */

    @Operation(summary = "Download current user's resume PDF", description = "Returns the authenticated user's stored resume PDF as a downloadable file.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF file returned", content = @Content(mediaType = "application/pdf")),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Profile or resume PDF not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/resume/download")
    public ResponseEntity<?> downloadMyResumePdf() {

        JsonData data = chatBotService.getProfileForCurrentUser();
        if (data.getResumePdf() == null) {
            throw new ProfileNotFoundException("Resume PDF not found for current user");
        }

        return buildPdfResponse(data);
    }

    /* ------------------------------------------------------------------ */
    /* PUT /api/resume/update */
    /* ------------------------------------------------------------------ */

    @Operation(summary = "Update existing resume", description = """
            Re-parses the supplied resume text and replaces the stored PDF.
            Only updates the resume content – other profile fields are unchanged.""")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Resume update payload", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = Object.class), examples = @ExampleObject(value = """
            {
              "resumeText": "Updated resume content...",
              "resumePdf": "<base64-encoded-pdf>"
            }""")))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resume updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid PDF format"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Profile not found – upload a resume first"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PutMapping("/resume/update")
    public ResponseEntity<?> updateResume(@RequestBody Map<String, Object> request) {

        String email = getAuthenticatedEmail();

        String resumeText = (String) request.get("resumeText");
        String resumePdf64 = (String) request.get("resumePdf");

        String json = "{}";
        if (resumeText != null && !resumeText.isBlank()) {
            json = chatBotService.convertJSON(resumeText);
        }

        byte[] pdfBytes = decodePdfStrict(resumePdf64);

        JsonData updated = chatBotService.updateResumeForEmail(json, email, pdfBytes);

        sendResumeBestEffort(updated);

        Map<String, Object> profile = buildProfileMap(updated);
        profile.put("id", updated.getId());

        return ResponseEntity.ok(Map.of(
                "message", "Resume updated successfully",
                "profile", profile));
    }

    /* ------------------------------------------------------------------ */
    /* GET /api/profile/{id} */
    /* ------------------------------------------------------------------ */

    @Operation(summary = "Get a user's profile by ID", description = "Returns the profile of any user by their profile ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile returned successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/profile/{id}")
    public ResponseEntity<?> getUserProfileById(
            @Parameter(description = "Profile ID of the user to look up") @PathVariable Long id) {

        var profile = chatBotService.getUserProfileById(id);
        if (profile == null) {
            throw new ProfileNotFoundException(id);
        }
        return ResponseEntity.ok(profile);
    }

    /* ================================================================== */
    /* Private helpers */
    /* ================================================================== */

    /** Extract the email of the currently authenticated user. */
    private String getAuthenticatedEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("Unauthorized");
        }
        return auth.getName();
    }

    /** Build a standard profile map from a {@link JsonData} entity. */
    private Map<String, Object> buildProfileMap(JsonData data) {
        Map<String, Object> map = new HashMap<>();
        map.put("email", data.getEmail());
        map.put("name", data.getName());
        map.put("year", data.getYear());
        map.put("department", data.getDepartment());
        map.put("institution", data.getInstitution());
        map.put("availability", data.getAvailability());
        if (data.getResumePdf() != null) {
            map.put("resumePdfUrl", "/api/resume/download/" + data.getId());
        }
        return map;
    }

    /** Build a PDF download response. */
    private ResponseEntity<byte[]> buildPdfResponse(JsonData data) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "resume_" + data.getId() + ".pdf");
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

        return ResponseEntity.ok()
                .headers(headers)
                .body(data.getResumePdf());
    }

    /** Decode base64 PDF – silently returns null on failure (for upload). */
    private byte[] decodePdf(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        try {
            return java.util.Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid base64 PDF: {}", ex.getMessage());
            return null;
        }
    }

    /** Decode base64 PDF – throws on invalid input (for update). */
    private byte[] decodePdfStrict(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        try {
            return java.util.Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid PDF format");
        }
    }

    /** Best-effort resume sync to Django ML service. */
    private void sendResumeBestEffort(JsonData profile) {
        try {
            chatBotService.sendResumeJson(profile);
        } catch (Exception ex) {
            log.warn("Best-effort resume sync failed: {}", ex.getMessage());
        }
    }
}
