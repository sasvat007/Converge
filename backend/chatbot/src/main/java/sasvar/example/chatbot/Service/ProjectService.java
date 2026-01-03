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

    @Autowired
    private ChatBotService chatBotService; // injected to send to Django

    public ProjectData createProject(String title,
                                     String type,
                                     String visibility,
                                     String requiredSkillsCsv,
                                     String githubRepo,
                                     String description,
                                     String domain) { // added domain param

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
        project.setDomain(domain); // persist domain
        project.setEmail(email);
        project.setCreatedAt(Instant.now().toString());

        ProjectData saved = projectRepository.save(project);

        // Best-effort: send project JSON and owner's resume JSON to Django ML
        try {
            chatBotService.sendProjectAndOwnerResume(saved);
        } catch (Exception e) {
            System.out.println("Failed to send project/resume to Django ML: " + e.getMessage());
        }

        return saved;
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
