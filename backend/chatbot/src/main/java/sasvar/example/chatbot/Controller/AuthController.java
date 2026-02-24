package sasvar.example.chatbot.Controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import sasvar.example.chatbot.Database.JsonData;
import sasvar.example.chatbot.Database.User;
import sasvar.example.chatbot.Repository.UserRepository;
import sasvar.example.chatbot.Service.ChatBotService;
import sasvar.example.chatbot.Utils.JwtUtils;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User registration and login endpoints")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final ChatBotService chatBotService;

    /* ------------------------------------------------------------------ */
    /* POST /auth/register */
    /* ------------------------------------------------------------------ */

    @Operation(summary = "Register a new user", description = """
            Creates a new user account with email/password credentials,
            parses the supplied resume text via Gemini AI, and stores the
            profile along with an optional PDF attachment. Returns a JWT
            token on success.""")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Registration payload", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = Object.class), examples = @ExampleObject(value = """
            {
              "email": "user@example.com",
              "password": "securePassword123",
              "resumeText": "John Doe, Software Engineer at XYZ...",
              "resumePdf": "<base64-encoded-pdf>",
              "name": "John Doe",
              "year": "3rd Year",
              "department": "Computer Science",
              "institution": "MIT",
              "availability": "high"
            }""")))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Registered successfully; returns JWT token and profile"),
            @ApiResponse(responseCode = "400", description = "Missing required fields (email, password, or resumeText)"),
            @ApiResponse(responseCode = "409", description = "User with the given email already exists"),
            @ApiResponse(responseCode = "500", description = "Internal error during resume parsing or DB save")
    })
    @PostMapping(path = "/register", consumes = "application/json")
    public ResponseEntity<?> register(@RequestBody Map<String, Object> body) {

        String email = (String) body.getOrDefault("email", "");
        String password = (String) body.getOrDefault("password", "");
        String resumeText = (String) body.getOrDefault("resumeText",
                body.getOrDefault("resume_text", ""));
        String resumePdf64 = (String) body.getOrDefault("resumePdf",
                body.getOrDefault("resume_pdf", ""));
        String name = (String) body.getOrDefault("name", null);
        String year = (String) body.getOrDefault("year", null);
        String department = (String) body.getOrDefault("department", null);
        String institution = (String) body.getOrDefault("institution", null);
        String availability = (String) body.getOrDefault("availability", null);

        // --- validation ---
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Email and password required"));
        }
        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "User already exists"));
        }
        if (resumeText == null || resumeText.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "resumeText is required"));
        }

        // --- create user ---
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);

        // --- parse resume & build profile ---
        JsonData savedProfile;
        try {
            String parsedJson = chatBotService.convertJSON(resumeText);
            byte[] pdfBytes = decodePdf(resumePdf64);

            savedProfile = chatBotService.saveJsonForEmail(
                    parsedJson, email, name, year,
                    department, institution, availability, pdfBytes);

        } catch (Exception ex) {
            log.error("Failed to parse/save resume during registration for {}", email, ex);
            safeDeleteUser(user);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to parse and save resume during registration"));
        }

        sendResumeBestEffort(savedProfile);

        String token = jwtUtils.generateToken(user.getEmail());

        Map<String, Object> resp = new HashMap<>();
        resp.put("message", "Registered successfully");
        resp.put("token", token);
        if (savedProfile != null) {
            resp.put("profile", buildProfileMap(savedProfile));
        }
        return ResponseEntity.ok(resp);
    }

    /* ------------------------------------------------------------------ */
    /* POST /auth/login */
    /* ------------------------------------------------------------------ */

    @Operation(summary = "Login an existing user", description = "Authenticates using email/password and returns a JWT token along with the user profile.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Login credentials", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = Object.class), examples = @ExampleObject(value = """
            {
              "email": "user@example.com",
              "password": "securePassword123"
            }""")))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful; returns JWT token and profile"),
            @ApiResponse(responseCode = "400", description = "Email or password missing"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {

        String email = body.get("email");
        String password = body.get("password");

        if (email == null || password == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Email and password required"));
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "User not found"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Password mismatch");
        }

        String token = jwtUtils.generateToken(user.getEmail());

        Map<String, Object> resp = new HashMap<>();
        resp.put("token", token);

        JsonData profile = chatBotService.getProfileByEmail(email);
        if (profile != null) {
            resp.put("profile", buildProfileMap(profile));
        }

        return ResponseEntity.ok(resp);
    }

    /* ------------------------------------------------------------------ */
    /* Private helpers */
    /* ------------------------------------------------------------------ */

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

    private void sendResumeBestEffort(JsonData profile) {
        try {
            chatBotService.sendResumeJson(profile);
        } catch (Exception ex) {
            log.warn("Best-effort resume sync failed: {}", ex.getMessage());
        }
    }

    private void safeDeleteUser(User user) {
        try {
            userRepository.delete(user);
        } catch (Exception ex) {
            log.warn("Failed to rollback user after registration error: {}", ex.getMessage());
        }
    }
}
