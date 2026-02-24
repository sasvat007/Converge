package sasvar.example.chatbot.Exception;

public class ProfileNotFoundException extends RuntimeException {

    public ProfileNotFoundException(Long id) {
        super("Profile not found with id: " + id);
    }

    public ProfileNotFoundException(String message) {
        super(message);
    }
}
