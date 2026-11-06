package team.projectpulse.security;

import jakarta.persistence.EntityManager;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.system.StatusCode;
import team.projectpulse.user.PeerEvaluationUser;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Consumer;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR-SEC-active-account: a deactivated account is refused on its next request, including with a token issued before
 * the deactivation. Seed ids: John Smith is student 4, Bill Gates is instructor 2.
 */
@DisplayName("Integration tests for the active-account check on authenticated requests")
class ActiveAccountIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    EntityManager entityManager;
    @Autowired
    JwtEncoder jwtEncoder;

    @Value("${api.endpoint.base-url}")
    String baseUrl;


    @Test
    @DisplayName("An active account's token still passes")
    void aValidTokenOfAnActiveAccountPasses() throws Exception {
        String token = login("j.smith@abc.edu");

        this.mockMvc.perform(get(this.baseUrl + "/students/4").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(4));
    }

    @Test
    @DisplayName("A student's token issued before her deactivation is refused on her next request")
    void aDeactivatedStudentsEarlierTokenIsRefused() throws Exception {
        String token = login("j.smith@abc.edu");

        disable("j.smith@abc.edu");

        this.mockMvc.perform(get(this.baseUrl + "/students/4").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("A deactivated instructor's token is refused too")
    void aDeactivatedInstructorsTokenIsRefused() throws Exception {
        String token = login("b.gates@abc.edu");

        disable("b.gates@abc.edu");

        this.mockMvc.perform(get(this.baseUrl + "/instructors/2").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("A deactivated account cannot log in")
    void aDeactivatedAccountCannotLogIn() throws Exception {
        disable("j.smith@abc.edu");

        this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic("j.smith@abc.edu", "123456")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("A signed token naming no account is refused")
    void aTokenNamingNoAccountIsRefused() throws Exception {
        String token = token(claims -> claims.claim("userId", 999999));

        this.mockMvc.perform(get(this.baseUrl + "/students/4").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A signed token with no userId claim is refused")
    void aTokenWithNoUserIdClaimIsRefused() throws Exception {
        String token = token(claims -> {
        });

        this.mockMvc.perform(get(this.baseUrl + "/students/4").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A signed token whose userId claim is not a number is refused")
    void aTokenWithANonNumericUserIdClaimIsRefused() throws Exception {
        String token = token(claims -> claims.claim("userId", "not-a-number"));

        this.mockMvc.perform(get(this.baseUrl + "/students/4").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    private void disable(String email) {
        PeerEvaluationUser user = this.entityManager
                .createQuery("select u from PeerEvaluationUser u where u.email = :email", PeerEvaluationUser.class)
                .setParameter("email", email)
                .getSingleResult();
        user.setEnabled(false);
        this.entityManager.flush();
    }

    private String login(String email) throws Exception {
        MvcResult mvcResult = this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic(email, "123456"))).andReturn();
        return "Bearer " + new JSONObject(mvcResult.getResponse().getContentAsString()).getJSONObject("data").getString("token");
    }

    private String token(Consumer<JwtClaimsSet.Builder> userIdClaim) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("self")
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .subject("j.smith@abc.edu")
                .claim("authorities", "ROLE_student");
        userIdClaim.accept(claims);
        return "Bearer " + this.jwtEncoder.encode(JwtEncoderParameters.from(claims.build())).getTokenValue();
    }

}
