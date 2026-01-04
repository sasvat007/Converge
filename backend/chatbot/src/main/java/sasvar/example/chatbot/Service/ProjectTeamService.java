package sasvar.example.chatbot.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import sasvar.example.chatbot.Database.ProjectData;
import sasvar.example.chatbot.Database.ProjectTeam;
import sasvar.example.chatbot.Database.ProjectTeamRequest;
import sasvar.example.chatbot.Database.JsonData;
import sasvar.example.chatbot.Repository.ProjectRepository;
import sasvar.example.chatbot.Repository.ProjectTeamRepository;
import sasvar.example.chatbot.Repository.JsonDataRepository;
import sasvar.example.chatbot.Repository.ProjectTeamRequestRepository;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProjectTeamService {

    @Autowired
    private ProjectTeamRepository projectTeamRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private JsonDataRepository jsonDataRepository;

    @Autowired
    private ProjectTeamRequestRepository projectTeamRequestRepository;

    // Add teammate: only project owner can add
    public ProjectTeam addTeammate(Long projectId, String memberEmail) {
        // auth
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("User not authenticated");
        }
        String requesterEmail = auth.getName();

        ProjectData project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (!requesterEmail.equals(project.getEmail())) {
            throw new RuntimeException("Only project owner can add teammates");
        }

        // prevent duplicates
        if (projectTeamRepository.existsByProjectIdAndMemberEmail(projectId, memberEmail)) {
            throw new RuntimeException("Member already added to project");
        }

        // verify member exists (profile)
        Optional<JsonData> memberProfile = jsonDataRepository.findByEmail(memberEmail);
        if (memberProfile.isEmpty()) {
            throw new RuntimeException("Member profile not found");
        }

        ProjectTeam pt = new ProjectTeam();
        pt.setProjectId(projectId);
        pt.setMemberEmail(memberEmail);
        pt.setAddedAt(Instant.now().toString());
        return projectTeamRepository.save(pt);
    }

    // Create a teammate request (owner initiates) — returns the saved request
    public ProjectTeamRequest createTeammateRequest(Long projectId, String targetEmail) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("User not authenticated");
        }
        String requesterEmail = auth.getName();

        ProjectData project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (!requesterEmail.equals(project.getEmail())) {
            throw new RuntimeException("Only project owner can send teammate requests");
        }

        if (requesterEmail.equalsIgnoreCase(targetEmail)) {
            throw new RuntimeException("Cannot send request to yourself");
        }

        // ensure candidate exists
        Optional<JsonData> candidate = jsonDataRepository.findByEmail(targetEmail);
        if (candidate.isEmpty()) {
            throw new RuntimeException("Target user profile not found");
        }

        // ensure not already teammate
        if (projectTeamRepository.existsByProjectIdAndMemberEmail(projectId, targetEmail)) {
            throw new RuntimeException("User already a teammate");
        }

        // ensure no existing pending request
        var existing = projectTeamRequestRepository.findByProjectIdAndTargetEmailAndStatus(projectId, targetEmail, "PENDING");
        if (existing.isPresent()) {
            return existing.get();
        }

        ProjectTeamRequest req = new ProjectTeamRequest();
        req.setProjectId(projectId);
        req.setRequesterEmail(requesterEmail);
        req.setTargetEmail(targetEmail);
        req.setStatus("PENDING");
        req.setCreatedAt(Instant.now().toString());
        req.setUpdatedAt(Instant.now().toString());

        return projectTeamRequestRepository.save(req);
    }

    // Target user accepts a pending request — creates ProjectTeam row and deletes the request
    public ProjectTeam acceptTeammateRequest(Long requestId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("User not authenticated");
        }
        String actorEmail = auth.getName();

        ProjectTeamRequest req = projectTeamRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (!"PENDING".equals(req.getStatus())) {
            // remove non-pending request to avoid it showing again
            projectTeamRequestRepository.delete(req);
            throw new RuntimeException("Request is not pending");
        }

        if (!actorEmail.equalsIgnoreCase(req.getTargetEmail())) {
            throw new RuntimeException("Only the requested user can accept this request");
        }

        Long projectId = req.getProjectId();

        // ensure not already teammate
        if (projectTeamRepository.existsByProjectIdAndMemberEmail(projectId, req.getTargetEmail())) {
            // delete request so it won't appear again, then inform caller
            projectTeamRequestRepository.delete(req);
            throw new RuntimeException("User already a teammate");
        }

        // create teammate row (owner is request.requesterEmail)
        ProjectTeam pt = new ProjectTeam();
        pt.setProjectId(projectId);
        pt.setMemberEmail(req.getTargetEmail());
        pt.setAddedAt(Instant.now().toString());
        ProjectTeam saved = projectTeamRepository.save(pt);

        // delete the request entry now that it has been accepted
        projectTeamRequestRepository.delete(req);

        return saved;
    }

    // New: target user rejects a pending request — deletes the request
    public void rejectTeammateRequest(Long requestId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("User not authenticated");
        }
        String actorEmail = auth.getName();

        ProjectTeamRequest req = projectTeamRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (!"PENDING".equals(req.getStatus())) {
            // already processed — ensure it doesn't remain
            projectTeamRequestRepository.delete(req);
            throw new RuntimeException("Request is not pending");
        }

        if (!actorEmail.equalsIgnoreCase(req.getTargetEmail())) {
            throw new RuntimeException("Only the requested user can reject this request");
        }

        // delete the pending request (reject)
        projectTeamRequestRepository.delete(req);
    }

    // List incoming requests for a user (target)
    public List<ProjectTeamRequest> listIncomingRequestsForCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("User not authenticated");
        }
        String email = auth.getName();
        return projectTeamRequestRepository.findAllByTargetEmail(email);
    }

    // List teammates with basic profile fields
    public List<Map<String, Object>> listTeammatesForProject(Long projectId) {
        List<ProjectTeam> rows = projectTeamRepository.findAllByProjectId(projectId);
        if (rows == null || rows.isEmpty()) return List.of();

        // For each row, fetch JsonData by email and map to minimal profile
        return rows.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            // try to find profile
            Optional<JsonData> opt = jsonDataRepository.findByEmail(r.getMemberEmail());
            if (opt.isPresent()) {
                JsonData p = opt.get();
                m.put("id", p.getId());              // ✅ ADD: teammate's JsonData.id
                m.put("email", p.getEmail());
                m.put("name", p.getName());
                m.put("year", p.getYear());
                m.put("department", p.getDepartment());
                m.put("institution", p.getInstitution());
                m.put("availability", p.getAvailability());
            } else {
                m.put("email", r.getMemberEmail());
                m.put("name", null);
            }
            m.put("addedAt", r.getAddedAt());
            return m;
        }).collect(Collectors.toList());
    }
}
