package com.cloudflow.exception;

import com.cloudflow.dto.request.CreateExecutionRequest;
import com.cloudflow.dto.request.CreateUserRequest;
import com.cloudflow.dto.request.PageQuery;
import com.cloudflow.dto.request.PasswordResetConfirmRequest;
import com.cloudflow.service.ResourceState;
import jakarta.validation.Valid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RequestErrorMvcTest {

    private static final String PASSWORD = "correct-horse-battery";
    private static final String TOKEN = "raw-reset-token-value";

    private final ResourceState resources = new ResourceState();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController(resources))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void invalidInputHidesSubmittedSecrets() throws Exception {
        String body = """
                {"email":"not-an-email","password":"%s","displayName":"","role":"MEMBER"}
                """.formatted(PASSWORD);
        String json = mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Request could not be processed"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertFalse(json.contains(PASSWORD));
    }

    @Test
    void invalidIdentifierAndTokenAreNotEchoed() throws Exception {
        String execution = """
                {"deploymentId":"not-a-uuid","versionId":"550e8400-e29b-41d4-a716-446655440000","taskType":"DEPLOY"}
                """;
        String executionJson = mockMvc.perform(post("/executions").contentType(MediaType.APPLICATION_JSON).content(execution))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertFalse(executionJson.contains("not-a-uuid"));

        String reset = """
                {"token":"%s","password":"12345678"}
                """.formatted(TOKEN);
        String resetJson = mockMvc.perform(post("/resets").contentType(MediaType.APPLICATION_JSON).content(reset))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertFalse(resetJson.contains(TOKEN));
    }

    @Test
    void unknownEnumAndPageStayGeneric() throws Exception {
        String body = """
                {"deploymentId":"550e8400-e29b-41d4-a716-446655440000","versionId":"550e8400-e29b-41d4-a716-446655440000","taskType":"FORK_BOMB"}
                """;
        String json = mockMvc.perform(post("/executions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request could not be processed"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertFalse(json.contains("FORK_BOMB"));

        mockMvc.perform(get("/pages").param("page", "-1").param("size", "20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void missingDuplicateUnauthorizedAndForbiddenUseApiError() throws Exception {
        mockMvc.perform(get("/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource not found"))
                .andExpect(jsonPath("$.errors").doesNotExist());
        mockMvc.perform(get("/duplicates"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Resource already exists"));
        mockMvc.perform(get("/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
        mockMvc.perform(get("/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Operation not permitted"));
    }

    @Test
    void databaseConflictHidesConstraintText() {
        String secret = "Duplicate entry 'reset-token-value' for key 'uq_users_tenant_email'";
        ResponseEntity<com.cloudflow.dto.response.ApiError> response =
                new GlobalExceptionHandler().handleDataConflict(new DataIntegrityViolationException(secret));
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Resource already exists", response.getBody().message());
        assertFalse(response.getBody().message().contains("reset-token"));
        assertFalse(response.getBody().message().contains("uq_users"));
    }

    @RestController
    static class ProbeController {

        private final ResourceState resources;

        ProbeController(ResourceState resources) {
            this.resources = resources;
        }

        @PostMapping("/users")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        void users(@Valid @RequestBody CreateUserRequest request) {}

        @PostMapping("/executions")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        void executions(@Valid @RequestBody CreateExecutionRequest request) {}

        @PostMapping("/resets")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        void resets(@Valid @RequestBody PasswordResetConfirmRequest request) {}

        @GetMapping("/pages")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        void pages(@Valid PageQuery pageQuery) {}

        @GetMapping("/missing")
        void missing() {
            resources.requirePresent(null);
        }

        @GetMapping("/duplicates")
        void duplicates() {
            resources.requireAbsent(new Object());
        }

        @GetMapping("/unauthorized")
        void unauthorized() {
            resources.requireAuthenticated(null);
        }

        @GetMapping("/forbidden")
        void forbidden() {
            resources.requireAllowed(false);
        }
    }
}
