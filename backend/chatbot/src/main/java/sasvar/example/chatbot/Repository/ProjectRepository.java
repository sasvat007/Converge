package sasvar.example.chatbot.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sasvar.example.chatbot.Database.ProjectData;

import java.util.List;

public interface ProjectRepository extends JpaRepository<ProjectData, Long> {
    List<ProjectData> findAllByEmail(String email);
}
