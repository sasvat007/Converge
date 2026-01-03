package sasvar.example.chatbot.Controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import sasvar.example.chatbot.Database.JsonData;
import sasvar.example.chatbot.Repository.JsonDataRepository;

@RestController
@RequestMapping("/resume")
@RequiredArgsConstructor
@CrossOrigin
public class ResumeController {

    private final JsonDataRepository jsonDataRepository;

    // ✅ FETCH LOGGED-IN USER'S RESUME
    @GetMapping("/me")
    public ResponseEntity<?> getMyResume() {

        String email = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        JsonData jsonData = jsonDataRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Resume not found"));

        return ResponseEntity.ok(jsonData.getProfileJson());
    }
}
