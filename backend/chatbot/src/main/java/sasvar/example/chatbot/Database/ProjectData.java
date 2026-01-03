package sasvar.example.chatbot.Database;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "projects")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Project fields
    @Column(nullable = false)
    private String title;

    @Column(name = "project_type", nullable = false)
    private String type;

    @Column(nullable = false)
    private String visibility; // e.g., public | private

    @Column(name = "required_skills")
    private String requiredSkills; // comma-separated list

    @Column(name = "github_repo")
    private String githubRepo; // optional

    @Column(columnDefinition = "TEXT")
    private String description;

    // domain stored as comma-separated list
    @Column(name = "domain")
    private String domain;

    // new: preferred technologies stored as comma-separated list
    @Column(name = "preferred_technologies")
    private String preferredTechnologies;

    // owner email (link to User)
    @Column(nullable = false)
    private String email;

    @Column(name = "created_at")
    private String createdAt = Instant.now().toString();
}
