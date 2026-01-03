package sasvar.example.chatbot.Database;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "resume")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JsonData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 🔑 Link resume/profile to user
    @Column(nullable = false, unique = true)
    private String email;

    // Basic profile fields (optional)
    private String name;
    private String year;
    private String department;
    private String institution;
    private String availability;

    // Stores the entire parsed JSON as JSONB (nullable until parsing happens)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @JsonIgnore
    private String profileJson;

    @Column(nullable = false)
    private String createdAt;
}
