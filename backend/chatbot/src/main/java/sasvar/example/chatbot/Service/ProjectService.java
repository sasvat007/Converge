package sasvar.example.chatbot.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import sasvar.example.chatbot.Database.ProjectData;
import sasvar.example.chatbot.Repository.ProjectRepository;
import sasvar.example.chatbot.Repository.ProjectTeamRepository; // { added import }
import sasvar.example.chatbot.Database.ProjectTeam; // { added import }

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ChatBotService chatBotService; // injected to send to Django

    @Autowired
    private ProjectTeamRepository projectTeamRepository; // { added repository injection }

    public ProjectData createProject(String title,
                                     String type,
                                     String visibility,
                                     String requiredSkillsCsv,
                                     String githubRepo,
                                     String description,
                                     String domain,
                                     String preferredTechnologiesCsv) { // added param

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
        project.setPreferredTechnologies(preferredTechnologiesCsv); // NEW: persist preferred techs
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

        // 1) Projects owned by the user
        List<ProjectData> owned = projectRepository.findAllByEmail(email);

        // 2) Projects where the user is a teammate
        List<ProjectData> result = new ArrayList<>();
        if (owned != null && !owned.isEmpty()) result.addAll(owned);

        // fetch team rows and project ids
        List<ProjectTeam> teamRows = projectTeamRepository.findAllByMemberEmail(email);
        if (teamRows != null && !teamRows.isEmpty()) {
            List<Long> teammateProjectIds = teamRows.stream()
                    .map(ProjectTeam::getProjectId)
                    .distinct()
                    .collect(Collectors.toList());

            if (!teammateProjectIds.isEmpty()) {
                List<ProjectData> teammateProjects = projectRepository.findAllById(teammateProjectIds);
                // merge avoiding duplicates
                for (ProjectData p : teammateProjects) {
                    boolean exists = result.stream().anyMatch(r -> r.getId() != null && r.getId().equals(p.getId()));
                    if (!exists) result.add(p);
                }
            }
        }

        return result;
    }

    public List<ProjectData> listAllProjects() {
        return projectRepository.findAll();
    }

    // NEW: fetch a single project by id (returns null if not found)
    public ProjectData getProjectById(Long id) {
        if (id == null) return null;
        return projectRepository.findById(id).orElse(null);
    }

    // NEW: delete project (owner only) — used when marking as completed
    public void deleteProjectAsCompleted(Long projectId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("User not authenticated");
        }
        String email = auth.getName();

        ProjectData project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (!email.equals(project.getEmail())) {
            throw new RuntimeException("Only project owner can mark as completed");
        }

        projectRepository.delete(project);
    }
}
