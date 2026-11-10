package io.github.elr.blog.auth;

import io.github.elr.blog.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ApplicationModuleTest(mode = BootstrapMode.DIRECT_DEPENDENCIES)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthModuleTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void registersAUser() {
        var name = newName();

        var result = register(name + "@Example.com", name, "correct horse");

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson().extractingPath("$.email").isEqualTo(name + "@example.com");
        assertThat(result).bodyJson().extractingPath("$.username").isEqualTo(name);
        assertThat(result).bodyJson().extractingPath("$.id").asString().isNotBlank();
        assertThat(result).bodyJson().extractingPath("$.createdAt").asString().isNotBlank();
        assertThat(result).bodyJson().doesNotHavePath("$.password");
        assertThat(result).bodyJson().doesNotHavePath("$.passwordHash");
    }

    @Test
    void storesThePasswordAsABcryptHash() {
        var name = newName();

        register(name + "@example.com", name, "correct horse");

        var hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE username = ?", String.class, name);
        assertThat(hash).startsWith("$2a$").doesNotContain("correct horse");
        assertThat(passwordEncoder.matches("correct horse", hash)).isTrue();
    }

    @Test
    void rejectsInvalidFields() {
        var result = register("not-an-email", "a!", "short");

        assertProblem(result, 400, "VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray()
                .containsExactly("email", "password", "username");
    }

    @Test
    void rejectsMissingFields() {
        var result = post("{}");

        assertProblem(result, 400, "VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray()
                .containsExactly("email", "password", "username");
    }

    @Test
    void rejectsPasswordLongerThan72Bytes() {
        var name = newName();
        var password = "€".repeat(25); // 25 characters, but 75 bytes in UTF-8

        var result = register(name + "@example.com", name, password);

        assertProblem(result, 400, "VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("password");
        assertThat(result).bodyJson().extractingPath("$.errors[0].message").isEqualTo("must be at most 72 bytes");
    }

    @Test
    void rejectsEmailThatIsTaken() {
        var name = newName();
        register(name + "@example.com", name, "correct horse");

        var result = register(name + "@EXAMPLE.com", newName(), "correct horse");

        assertProblem(result, 409, "EMAIL_TAKEN");
    }

    @Test
    void rejectsUsernameThatIsTaken() {
        var name = newName();
        register(name + "@example.com", name, "correct horse");

        var result = register(newName() + "@example.com", name.toUpperCase(), "correct horse");

        assertProblem(result, 409, "USERNAME_TAKEN");
    }

    @Test
    void rejectsBodyThatIsNotJson() {
        var result = post("this is not json");

        assertProblem(result, 400, "BAD_REQUEST");
    }

    @Test
    void everythingElseNeedsAuthentication() {
        assertThat(mvc.get().uri("/posts")).hasStatus(401);
        assertThat(mvc.get().uri("/auth/register")).hasStatus(401);
    }

    private MvcTestResult register(String email, String username, String password) {
        return post("""
                {"email": "%s", "username": "%s", "password": "%s"}
                """.formatted(email, username, password));
    }

    private MvcTestResult post(String body) {
        return mvc.post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static void assertProblem(MvcTestResult result, int status, String code) {
        assertThat(result).hasStatus(status).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(status);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    }

    private static String newName() {
        return "user-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
