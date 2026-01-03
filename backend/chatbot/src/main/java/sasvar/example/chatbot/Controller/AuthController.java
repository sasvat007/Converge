package sasvar.example.chatbot.Controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import sasvar.example.chatbot.Database.User;
import sasvar.example.chatbot.Repository.UserRepository;
import sasvar.example.chatbot.Utils.JwtUtils;
import sasvar.example.chatbot.Service.ChatBotService;
import sasvar.example.chatbot.Database.JsonData;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final ChatBotService chatBotService; // added

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {

        String email = body.get("email");
        String password = body.get("password");

        if (email == null || password == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Email and password required"));
        }

        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "User already exists"));
        }

        // Ensure resumeText is provided so parsed JSON is always produced & saved at registration
        String resumeText = body.get("resumeText");
        if (resumeText == null || resumeText.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "resumeText is required during registration"));
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));

        userRepository.save(user);

        JsonData savedProfile;
        try {
            // Parse resume text using existing service
            String parsedJson = chatBotService.convertJSON(resumeText);

            // Persist parsed JSON + provided profile fields for the given email
            savedProfile = chatBotService.saveJsonForEmail(
                    parsedJson,
                    email,
                    body.get("name"),
                    body.get("year"),
                    body.get("department"),
                    body.get("institution"),
                    body.get("availability")
            );

        } catch (Exception e) {
            // Rollback user creation if parsing/saving fails
            try {
                userRepository.delete(user);
            } catch (Exception ignored) {}
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to parse and save resume during registration"));
        }

        // Best-effort: send parsed resume JSON to Django ML service (do not fail registration if this fails)
        try {
            chatBotService.sendResumeJson(savedProfile);
        } catch (Exception ignored) {
        }

        String token = jwtUtils.generateToken(user.getEmail());

        Map<String, Object> resp = new HashMap<>();
        resp.put("message", "Registered successfully");
        resp.put("token", token);
        if (savedProfile != null) {
            // Return only profile fields (exclude profileJson)
            Map<String, Object> profile = new HashMap<>();
            profile.put("email", savedProfile.getEmail());
            profile.put("name", savedProfile.getName());
            profile.put("year", savedProfile.getYear());
            profile.put("department", savedProfile.getDepartment());
            profile.put("institution", savedProfile.getInstitution());
            profile.put("availability", savedProfile.getAvailability());
            resp.put("profile", profile);
        }

        return ResponseEntity.ok(resp);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {

        String email = body.get("email");
        String password = body.get("password");

        if (email == null || password == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Email and password required"));
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "User not found"
                        )
                );

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Password mismatch"
            );
        }

        String token = jwtUtils.generateToken(user.getEmail());

        // fetch profile stored during registration (if any)
        JsonData profile = chatBotService.getProfileByEmail(email);

        Map<String, Object> resp = new HashMap<>();
        resp.put("token", token);
        if (profile != null) {
            // Return only top-level profile fields (exclude profileJson)
            Map<String, Object> profileMap = new HashMap<>();
            profileMap.put("email", profile.getEmail());
            profileMap.put("name", profile.getName());
            profileMap.put("year", profile.getYear());
            profileMap.put("department", profile.getDepartment());
            profileMap.put("institution", profile.getInstitution());
            profileMap.put("availability", profile.getAvailability());
            resp.put("profile", profileMap);
        }

        return ResponseEntity.ok(resp);
    }
}
