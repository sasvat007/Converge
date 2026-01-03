package sasvar.example.chatbot.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import sasvar.example.chatbot.Database.ProjectData;
import sasvar.example.chatbot.Repository.ProjectRepository;

import java.time.Instant;
import java.util.List;

@Service
public class ProjectService {

    @Autowired
    private ProjectRepository projectRepository;

    public ProjectData createProject(String title,
                                     String type,
                                     String visibility,
                                     String requiredSkillsCsv,
                                     String githubRepo,
                                     String description) {

        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("User not authenticated");
        }
        String email = auth.getName();

        ProjectData project = new ProjectData();
        project.setTitle(title);
        project.setType(type);
        project.setVisibility(visibility);
        project.setRequiredSkills(requiredSkillsCsv);
        project.setGithubRepo(githubRepo);
        project.setDescription(description);
        project.setEmail(email);
        project.setCreatedAt(Instant.now().toString());

        return projectRepository.save(project);
    }

    public List<ProjectData> listProjectsForCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("User not authenticated");
        }
        String email = auth.getName();
        return projectRepository.findAllByEmail(email);
    }

    public List<ProjectData> listAllProjects() {
        return projectRepository.findAll();
    }
}
