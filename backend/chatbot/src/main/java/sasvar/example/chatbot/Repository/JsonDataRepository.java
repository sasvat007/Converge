package sasvar.example.chatbot.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sasvar.example.chatbot.JsonData;

public interface JsonDataRepository  extends JpaRepository<JsonData,Long> {
}
