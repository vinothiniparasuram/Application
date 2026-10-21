package com.lifeorganizer.auth;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = { "server.ssl.enabled=false",
		"app.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "app.rate-limit.max-requests=1000" })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RegistrationIntegrationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void registerReturnsJwtThatAuthenticatesTheUser() throws Exception {
		String email = uniqueEmail();

		String body = mockMvc.perform(register("Vino", email.toUpperCase(), "secret123"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.user.email").value(email))
			.andExpect(jsonPath("$.user.password").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String token = JsonPath.read(body, "$.accessToken");

		mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.name").value("Vino"));
	}

	@Test
	void storesBcryptHashNotThePassword() throws Exception {
		String email = uniqueEmail();
		mockMvc.perform(register("Vino", email, "secret123")).andExpect(status().isCreated());

		String hash = jdbcTemplate.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, email);

		assertThat(hash).startsWith("$2a$12$").doesNotContain("secret123");
	}

	@Test
	void rejectsDuplicateEmailIgnoringCase() throws Exception {
		String email = uniqueEmail();
		mockMvc.perform(register("Vino", email, "secret123")).andExpect(status().isCreated());

		mockMvc.perform(register("Someone", email.toUpperCase(), "another123"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.title").value("Email already registered"));
	}

	@Test
	void rejectsInvalidInputWithFieldErrors() throws Exception {
		mockMvc.perform(register("", "not-an-email", "short"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.name").exists())
			.andExpect(jsonPath("$.errors.email").exists())
			.andExpect(jsonPath("$.errors.password").exists());
	}

	@Test
	void protectedEndpointRequiresToken() throws Exception {
		mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsTamperedToken() throws Exception {
		mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.valid-token"))
			.andExpect(status().isUnauthorized());
	}

	private static org.springframework.test.web.servlet.RequestBuilder register(String name, String email,
			String password) {
		String json = """
				{"name": "%s", "email": "%s", "password": "%s"}
				""".formatted(name, email, password);
		return post("/api/register").contentType(MediaType.APPLICATION_JSON).content(json);
	}

	private static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

}
