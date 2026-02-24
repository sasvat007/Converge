package sasvar.example.chatbot.Config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI convergeOpenAPI() {
        // Define Bearer JWT security scheme
        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Enter your JWT token obtained from /auth/login or /auth/register");

        return new OpenAPI()
                .info(new Info()
                        .title("Converge – Buddy Finder API")
                        .description("""
                                RESTful API for the **Converge** platform – a buddy-finder
                                that matches students and professionals based on their resumes,
                                skills, and project interests.

                                ### Key Features
                                - **Authentication** – Register & login with JWT tokens
                                - **Profile / Resume** – Upload, parse, update, and download resumes
                                - **Projects** – Create, explore, and manage collaborative projects
                                - **Team Requests** – Send, accept, and reject teammate invitations
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Converge Team")
                                .email("sasvat@converge.dev"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", bearerScheme));
    }
}
