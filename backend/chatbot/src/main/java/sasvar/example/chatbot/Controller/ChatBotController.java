package sasvar.example.chatbot.Controller;

import org.springframework.web.bind.annotation.*;
import sasvar.example.chatbot.Service.ChatBotService;
import sasvar.example.chatbot.DTO.ChatbotRequestDTO;
import sasvar.example.chatbot.DTO.ChatbotResponseDTO;
import sasvar.example.chatbot.JsonData;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class ChatBotController {

    private final ChatBotService chatBotService;

    public ChatBotController(ChatBotService chatBotService) {
        this.chatBotService = chatBotService;
    }

    /**
     * 1️⃣ Parse resume → store JSON in DB → return stored ID
     */
    @PostMapping("/parse")
    public ChatbotResponseDTO parseResume(@RequestBody ChatbotRequestDTO request) {
        try {
            // Convert resume text → structured JSON
            String json = chatBotService.convertJSON(request.getResumeText());

            // Save JSON into PostgreSQL
            JsonData saved = chatBotService.saveJson(json);

            // Return success + ID
            return new ChatbotResponseDTO(
                    "Stored successfully with ID: " + saved.getId(),
                    true
            );

        } catch (Exception e) {
            e.printStackTrace();
            return new ChatbotResponseDTO(
                    "Failed to parse and store resume",
                    false
            );
        }
    }

    /**
     * 2️⃣ Fetch stored JSON by ID and display it
     */
    @GetMapping("/profile/{id}")
    public String getStoredProfile(@PathVariable Long id) {
        return chatBotService.getById(id).getProfileJson();
    }

    @PostMapping("/send-to-ml/{id}")
    public String sendResumeToML(@PathVariable Long id) {
        chatBotService.sendjsontodjango(id);
        return "Resume " + id + " sent to ML service";
    }

}
