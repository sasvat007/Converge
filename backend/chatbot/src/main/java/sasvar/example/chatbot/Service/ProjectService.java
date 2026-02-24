package sasvar.example.chatbot.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import sasvar.example.chatbot.Database.ProjectData;
import sasvar.example.chatbot.Database.ProjectTeam;
import sasvar.example.chatbot.Repository.ProjectRepository;
import sasvar.example.chatbot.Repository.ProjectTeamRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository projectRepository;
    private final ChatBotService chatBotService;
    private final ProjectTeamRepository projectTeamRepository;
    private final ProjectTeamService projectTeamService;

    public ProjectService(ProjectRepository projectRepository,
            ChatBotService chatBotService,
            ProjectTeamRepository projectTeamRepository,
            ProjectTeamService projectTeamService) {
        this.projectRepository = projectRepository;
        this.chatBotService = chatBotService;
        this.projectTeamRepository = projectTeamRepository;
        this.projectTeamService = projectTeamService;
    }

    /* ------------------------------------------------------------------ */
    /* Create */
    /* ------------------------------------------------------------------ */

    public ProjectData createProject(String title,
            String type,
            String visibility,
            String requiredSkillsCsv,
            String githubRepo,
            String description,
            String domain,
            String preferredTechnologiesCsv) {

        String email = getAuthenticatedEmail();

        ProjectData project = new ProjectData();
        project.setTitle(title);
        project.setType(type);
        project.setVisibility(visibility);
        project.setRequiredSkills(requiredSkillsCsv);
        project.setGithubRepo(githubRepo);
        project.setDescription(description);
        project.setDomain(domain);
        project.setPreferredTechnologies(preferredTechnologiesCsv);
        project.setEmail(email);
        project.setCreatedAt(Instant.now().toString());

        ProjectData saved = projectRepository.save(project);

        // Best-effort: send project JSON to Django ML
        try {
            chatBotService.sendProjectAndOwnerResume(saved);
        } catch (Exception e) {
            log.warn("Failed to sync project to Django ML: {}", e.getMessage());
        }

        return saved;
    }

    /* ------------------------------------------------------------------ */
    /* List */
    /* ------------------------------------------------------------------ */

    public List<ProjectData> listProjectsForCurrentUser() {
        String email = getAuthenticatedEmail();

        // owned projects
        List<ProjectData> result = new ArrayList<>(
                projectRepository.findAllByEmail(email));

        // projects where user is a teammate
        List<ProjectTeam> teamRows = projectTeamRepository.findAllByMemberEmail(email);
        if (teamRows != null && !teamRows.isEmpty()) {
            List<Long> teammateProjectIds = teamRows.stream()
                    .map(ProjectTeam::getProjectId)
                    .distinct()
                    .collect(Collectors.toList());

            List<ProjectData> teammateProjects = projectRepository.findAllById(teammateProjectIds);
            for (ProjectData p : teammateProjects) {
                boolean exists = result.stream()
                        .anyMatch(r -> r.getId() != null && r.getId().equals(p.getId()));
                if (!exists)
                    result.add(p);
            }
        }

        return result;
    }

    public List<ProjectData> listAllProjects() {
        return projectRepository.findAll();
    }

    /* ------------------------------------------------------------------ */
    /* Lookup */
    /* ------------------------------------------------------------------ */

    public ProjectData getProjectById(Long id) {
        if (id == null)
            return null;
        return projectRepository.findById(id).orElse(null);
    }

    /* ------------------------------------------------------------------ */
    /* Complete */
    /* ------------------------------------------------------------------ */

    public ProjectData completeProject(Long projectId) {
        String email = getAuthenticatedEmail();

        ProjectData project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (!email.equals(project.getEmail())) {
            throw new RuntimeException("Only project owner can mark as completed");
        }

        project.setStatus("COMPLETED");
        ProjectData updatedProject = projectRepository.save(project);

        // Create rating notifications for all members
        projectTeamService.createRatingRequestsForProject(updatedProject);

        return updatedProject;
    }

    /* ------------------------------------------------------------------ */
    /* Helpers */
    /* ------------------------------------------------------------------ */

    private String getAuthenticatedEmail() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("User not authenticated");
        }
        return auth.getName();
    }
}
