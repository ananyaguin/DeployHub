package com.cloudflow.exception;

import com.cloudflow.dto.response.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousUnknownPathRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/missing"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.path").doesNotExist());
    }

    @Test
    void unexpectedErrorHidesCause() {
        String secret = "/var/lib/cloudflow token=secret";
        ResponseEntity<ApiError> response = new GlobalExceptionHandler()
                .handleUnexpected(new IllegalStateException(secret));

        ApiError body = response.getBody();
        assertEquals(500, response.getStatusCode().value());
        assertEquals("Unexpected error", body.message());
        assertFalse(body.message().contains("token"));
        assertFalse(body.message().contains("/var/lib"));
    }
}
