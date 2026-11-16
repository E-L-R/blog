package io.github.elr.blog.auth;

import com.jayway.jsonpath.JsonPath;
import io.github.elr.blog.TestcontainersConfiguration;
import io.github.elr.blog.common.web.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests registration, login and token checking through HTTP. MockMvc sends requests through
 * the security filters and the controller without starting a real server. The {@code auth}
 * module needs {@code user}, so the test starts its direct dependencies too.
 * <p>
 * The database is not emptied between tests, so each test registers names no other test uses.
 */
@ApplicationModuleTest(mode = BootstrapMode.DIRECT_DEPENDENCIES)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthModuleTests.WhoAmIController.class})
@ActiveProfiles("test")
class AuthModuleTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JwtEncoder jwtEncoder;

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
    void logsInWithEmailInAnyCase() {
        var name = newName();
        register(name + "@example.com", name, "correct horse");

        var result = login(name + "@EXAMPLE.com", "correct horse");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.accessToken").asString().isNotBlank();
        assertThat(result).bodyJson().extractingPath("$.tokenType").isEqualTo("Bearer");
        assertThat(result).bodyJson().extractingPath("$.expiresIn").isEqualTo(3600);
    }

    @Test
    void theTokenIdentifiesTheUser() throws Exception {
        var name = newName();
        var userId = registerId(name);
        var token = tokenFor(name);

        var result = mvc.post().uri("/test/whoami").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();

        assertThat(result).hasStatusOk().hasBodyTextEqualTo(userId.toString());
    }

    @Test
    void unknownEmailAndWrongPasswordGetTheSameAnswer() throws Exception {
        var name = newName();
        register(name + "@example.com", name, "correct horse");

        var wrongPassword = login(name + "@example.com", "wrong horse");
        var unknownEmail = login(newName() + "@example.com", "correct horse");

        assertProblem(wrongPassword, 401, "INVALID_CREDENTIALS");
        assertProblem(unknownEmail, 401, "INVALID_CREDENTIALS");
        assertThat(wrongPassword.getResponse().getContentAsString())
                .isEqualTo(unknownEmail.getResponse().getContentAsString());
    }

    @Test
    void loginRequiresEmailAndPassword() {
        var result = mvc.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}").exchange();

        assertProblem(result, 400, "VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray()
                .containsExactly("email", "password");
    }

    @Test
    void theBotCannotLogIn() {
        // The bot has no email or password, so there is nothing it could log in with.
        assertThat(jdbc.queryForObject(
                "SELECT email IS NULL AND password_hash IS NULL FROM users WHERE username = 'blog-ai'", Boolean.class))
                .isTrue();
    }

    @Test
    void writesNeedAToken() {
        var result = mvc.post().uri("/test/whoami").exchange();

        assertProblem(result, 401, "UNAUTHENTICATED");
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
    }

    @Test
    void refusesAnExpiredToken() {
        var userId = registerId(newName());
        var issuedAt = Instant.now().minus(Duration.ofHours(2));
        var token = encode(jwtEncoder, userId, issuedAt, issuedAt.plus(Duration.ofHours(1)));

        assertProblem(whoAmI(token), 401, "UNAUTHENTICATED");
    }

    @Test
    void refusesATokenSignedWithAnotherKey() {
        var userId = registerId(newName());
        var otherKey = new SecretKeySpec(new byte[32], "HmacSHA256");
        var otherEncoder = NimbusJwtEncoder.withSecretKey(otherKey).build();
        var token = encode(otherEncoder, userId, Instant.now(), Instant.now().plus(Duration.ofHours(1)));

        assertProblem(whoAmI(token), 401, "UNAUTHENTICATED");
    }

    @Test
    void refusesTheTokenOfADeletedAccount() throws Exception {
        var name = newName();
        var token = tokenFor(name);
        jdbc.update("UPDATE users SET deleted_at = now(), email = NULL, password_hash = NULL WHERE username = ?", name);

        assertProblem(whoAmI(token), 401, "UNAUTHENTICATED");
        assertProblem(login(name + "@example.com", "correct horse"), 401, "INVALID_CREDENTIALS");
    }

    @Test
    void aBrokenTokenIsRefusedEvenOnAPublicEndpoint() {
        var result = mvc.post().uri("/auth/register")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange();

        assertProblem(result, 401, "UNAUTHENTICATED");
    }

    @Test
    void meReturnsTheCallersAccount() throws Exception {
        var name = newName();
        var token = tokenFor(name);

        var result = mvc.get().uri("/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.username").isEqualTo(name);
        assertThat(result).bodyJson().extractingPath("$.email").isEqualTo(name + "@example.com");
    }

    @Test
    void meNeedsAToken() {
        assertProblem(mvc.get().uri("/me").exchange(), 401, "UNAUTHENTICATED");
    }

    @Test
    void deletingAnAccountNeedsTheRightPassword() throws Exception {
        var name = newName();
        var token = tokenFor(name);

        assertProblem(deleteAccount(token, "wrong horse"), 403, "INVALID_PASSWORD");
        assertProblem(deleteAccount(token, null), 400, "VALIDATION_FAILED");

        assertThat(login(name + "@example.com", "correct horse")).hasStatusOk();
    }

    @Test
    void deletingAnAccountRetiresItForGood() throws Exception {
        var name = newName();
        var token = tokenFor(name);
        var userId = jdbc.queryForObject("SELECT id FROM users WHERE username = ?", UUID.class, name);

        assertThat(deleteAccount(token, "correct horse")).hasStatus(204);

        assertProblem(mvc.get().uri("/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange(),
                401, "UNAUTHENTICATED");
        assertProblem(login(name + "@example.com", "correct horse"), 401, "INVALID_CREDENTIALS");
        assertProblem(mvc.get().uri("/users/{id}", userId).exchange(), 404, "NOT_FOUND");
        // The username stays taken; the email is free for a new account.
        assertProblem(register(newName() + "@example.com", name, "correct horse"), 409, "USERNAME_TAKEN");
        assertThat(register(name + "@example.com", newName(), "correct horse")).hasStatus(201);
    }

    /** An endpoint that needs a token and returns the caller's ID, so tests can check what a token proves. */
    @RestController
    static class WhoAmIController {

        @PostMapping("/test/whoami")
        String whoAmI(@CurrentUser UUID userId) {
            return userId.toString();
        }
    }

    private MvcTestResult deleteAccount(String token, String password) {
        var body = password == null ? "{}" : "{\"password\": \"%s\"}".formatted(password);
        return mvc.post().uri("/me/delete")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult whoAmI(String token) {
        return mvc.post().uri("/test/whoami").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
    }

    private UUID registerId(String name) {
        register(name + "@example.com", name, "correct horse");
        return jdbc.queryForObject("SELECT id FROM users WHERE username = ?", UUID.class, name);
    }

    private String tokenFor(String name) throws Exception {
        if (jdbc.queryForObject("SELECT count(*) FROM users WHERE username = ?", Integer.class, name) == 0) {
            register(name + "@example.com", name, "correct horse");
        }
        var result = login(name + "@example.com", "correct horse");
        assertThat(result).hasStatusOk();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private static String encode(JwtEncoder encoder, UUID userId, Instant issuedAt, Instant expiresAt) {
        var claims = JwtClaimsSet.builder()
                .issuer(JwtConfiguration.ISSUER)
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private MvcTestResult login(String email, String password) {
        return mvc.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password))
                .exchange();
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
