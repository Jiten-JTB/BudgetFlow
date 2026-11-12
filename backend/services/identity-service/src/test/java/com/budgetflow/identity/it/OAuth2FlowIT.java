package com.budgetflow.identity.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.budgetflow.identity.config.RegisteredClientInitializer;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

/** Authorization Code + PKCE end to end, as the Next.js BFF (confidential client) performs it. */
class OAuth2FlowIT extends IdentityIntegrationTest {

    private static final String CLIENT_ID = "budgetflow-web";
    private static final String CLIENT_SECRET = "test-web-secret";
    private static final String REDIRECT_URI = "http://localhost:3000/api/auth/callback";
    private static final String ISSUER = "https://budgetflow-auth.test";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired RegisteredClientInitializer clientInitializer;

    private String email;
    private String userId;
    private MockHttpSession session;

    @BeforeEach
    void registerAndLogIn() throws Exception {
        email = uniqueEmail();
        userId = JsonPath.read(mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                .content(REGISTER_JSON.formatted(email))).andReturn().getResponse().getContentAsString(), "$.id");
        markEmailVerified(jdbc, email);
        session = new MockHttpSession();
    }

    @Test
    void unauthenticatedAuthorizeRequestRedirectsToLogin() throws Exception {
        String location = mvc.perform(authorize(challenge(verifier())).accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(location).endsWith("/login");
    }

    @Test
    void issuesSignedTokensWithUserIdSubjectAfterLoginAndPkce() throws Exception {
        String verifier = verifier();
        Map<String, Object> tokens = exchange(loginAndGetCode(verifier), verifier);

        assertThat(tokens).containsKeys("access_token", "refresh_token", "id_token");
        assertThat(((Number) tokens.get("expires_in")).longValue()).isBetween(890L, 900L);
        Jwt access = decoder().decode((String) tokens.get("access_token"));
        assertThat(access.getSubject()).isEqualTo(userId);
        assertThat(access.getIssuer().toString()).isEqualTo(ISSUER);
        assertThat(access.getClaimAsStringList("scope")).contains("budgetflow.api", "openid");
        assertThat(Duration.between(access.getIssuedAt(), access.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
        assertThat(decoder().decode((String) tokens.get("id_token")).getSubject()).isEqualTo(userId);
    }

    @Test
    void rejectsAuthorizeRequestWithoutPkce() throws Exception {
        login();
        String location = mvc.perform(get("/oauth2/authorize").session(session)
                        .queryParam("response_type", "code").queryParam("client_id", CLIENT_ID)
                        .queryParam("scope", "openid budgetflow.api").queryParam("redirect_uri", REDIRECT_URI)
                        .queryParam("state", "s1"))
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(location).contains("error=invalid_request");
    }

    @Test
    void rejectsCodeExchangeWithWrongVerifierOrWrongSecret() throws Exception {
        String verifier = verifier();
        String code = loginAndGetCode(verifier);

        mvc.perform(post("/oauth2/token").with(httpBasic(CLIENT_ID, "wrong-secret"))
                        .param("grant_type", "authorization_code").param("code", code)
                        .param("redirect_uri", REDIRECT_URI).param("code_verifier", verifier))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/oauth2/token").with(httpBasic(CLIENT_ID, CLIENT_SECRET))
                        .param("grant_type", "authorization_code").param("code", code)
                        .param("redirect_uri", REDIRECT_URI).param("code_verifier", verifier() ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void rotatesRefreshTokensAndRejectsReuse() throws Exception {
        String verifier = verifier();
        String firstRefresh = (String) exchange(loginAndGetCode(verifier), verifier).get("refresh_token");

        String body = mvc.perform(post("/oauth2/token").with(httpBasic(CLIENT_ID, CLIENT_SECRET))
                        .param("grant_type", "refresh_token").param("refresh_token", firstRefresh))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String secondRefresh = JsonPath.read(body, "$.refresh_token");
        assertThat(secondRefresh).isNotEqualTo(firstRefresh);

        mvc.perform(post("/oauth2/token").with(httpBasic(CLIENT_ID, CLIENT_SECRET))
                        .param("grant_type", "refresh_token").param("refresh_token", firstRefresh))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void accessTokenAuthorisesProfileApi() throws Exception {
        String verifier = verifier();
        String accessToken = (String) exchange(loginAndGetCode(verifier), verifier).get("access_token");

        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.email").value(email));
        mvc.perform(patch("/api/v1/users/me").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"baseCurrency\":\"USD\",\"displayName\":\"Al\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency").value("USD"))
                .andExpect(jsonPath("$.displayName").value("Al"))
                .andExpect(jsonPath("$.timezone").value("Asia/Kolkata"));
        mvc.perform(patch("/api/v1/users/me").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"baseCurrency\":\"NOPE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENCY"));
    }

    @Test
    void publishesDiscoveryDocumentWithConfiguredIssuer() throws Exception {
        mvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value(ISSUER))
                .andExpect(jsonPath("$.code_challenge_methods_supported[0]").value("S256"));
        mvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kid", org.hamcrest.Matchers.startsWith("test-key-ephemeral-")))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());
    }

    @Test
    void clientRegistrationIsIdempotentAndHashesSecret() throws Exception {
        clientInitializer.run(null);

        List<Map<String, Object>> rows = jdbc.sql("SELECT client_secret FROM oauth2_registered_client WHERE client_id = :id")
                .param("id", CLIENT_ID).query().listOfRows();
        assertThat(rows).hasSize(1);
        assertThat((String) rows.getFirst().get("client_secret")).startsWith("{argon2}");
    }

    // --- helpers ---------------------------------------------------------------------------------

    private void login() throws Exception {
        mvc.perform(post("/login").session(session).with(csrf())
                        .param("username", email).param("password", "correct horse battery"))
                .andExpect(status().is3xxRedirection());
    }

    private String loginAndGetCode(String verifier) throws Exception {
        login();
        String location = mvc.perform(authorize(challenge(verifier)))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(location).startsWith(REDIRECT_URI);
        var params = UriComponentsBuilder.fromUri(URI.create(location)).build().getQueryParams();
        assertThat(params.getFirst("state")).isEqualTo("xyz");
        return params.getFirst("code");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authorize(String challenge) {
        return get("/oauth2/authorize").session(session)
                .queryParam("response_type", "code").queryParam("client_id", CLIENT_ID)
                .queryParam("scope", "openid budgetflow.api").queryParam("redirect_uri", REDIRECT_URI)
                .queryParam("state", "xyz")
                .queryParam("code_challenge", challenge).queryParam("code_challenge_method", "S256");
    }

    private Map<String, Object> exchange(String code, String verifier) throws Exception {
        String body = mvc.perform(post("/oauth2/token").with(httpBasic(CLIENT_ID, CLIENT_SECRET))
                        .param("grant_type", "authorization_code").param("code", code)
                        .param("redirect_uri", REDIRECT_URI).param("code_verifier", verifier))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$");
    }

    private NimbusJwtDecoder decoder() throws Exception {
        String jwks = mvc.perform(get("/oauth2/jwks")).andReturn().getResponse().getContentAsString();
        RSAKey key = (RSAKey) JWKSet.parse(jwks).getKeys().getFirst();
        return NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
    }

    private static String verifier() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String challenge(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }
}
