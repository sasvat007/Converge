package sasvar.example.chatbot.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sasvar.example.chatbot.Database.JsonData;

import java.util.Optional;

public interface JsonDataRepository  extends JpaRepository<JsonData,Long> {
    Optional<JsonData> findByEmail(String email);
}
