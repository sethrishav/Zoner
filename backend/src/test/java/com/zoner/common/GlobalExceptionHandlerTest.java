package com.zoner.common;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zoner.common.error.BusinessRuleException;
import com.zoner.common.error.ConflictException;
import com.zoner.common.error.ForbiddenException;
import com.zoner.common.error.GlobalExceptionHandler;
import com.zoner.common.error.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Pure unit test (no Spring context, no DB): every failure maps to the one ApiError shape. */
class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler(Clock.systemUTC()))
                .build();
    }

    @Test
    void notFoundMapsTo404() throws Exception {
        mvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Event not found"));
    }

    @Test
    void forbiddenMapsTo403() throws Exception {
        mvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void conflictMapsTo409() throws Exception {
        mvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void businessRuleMapsTo422() throws Exception {
        mvc.perform(get("/test/rule"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("End must be after start"));
    }

    @Test
    void validationErrorsListTheFields() throws Exception {
        mvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("title"));
    }

    @Test
    void malformedJsonMapsTo400WithoutLeakingParserDetails() throws Exception {
        mvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(content().string(not(containsString("Unexpected"))));
    }

    @Test
    void unexpectedErrorsReturn500AndHideInternals() throws Exception {
        mvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("secret internal detail"))));
    }

    @RestController
    @RequestMapping("/test")
    static class ThrowingController {

        record Payload(@NotBlank String title) {}

        @GetMapping("/not-found")
        void notFound() {
            throw new NotFoundException("Event not found");
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new ForbiddenException();
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ConflictException();
        }

        @GetMapping("/rule")
        void rule() {
            throw new BusinessRuleException("End must be after start");
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("secret internal detail");
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody Payload payload) {}
    }
}
