package com.mvbr.retailstore.customer.infrastructure.adapter.in.rest.exception;

import com.mvbr.retailstore.customer.domain.exception.DomainException;
import com.mvbr.retailstore.customer.domain.exception.InvalidCustomerException;
import com.mvbr.retailstore.customer.infrastructure.adapter.in.rest.dto.CreateCustomerRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.annotation.Validated;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomerExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        ErrorTestController controller = new ErrorTestController(validator);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new CustomerExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void invalidCustomerReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/errors/invalid-customer"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_CUSTOMER"))
                .andExpect(jsonPath("$.message").value("Invalid customer payload"))
                .andExpect(jsonPath("$.path").value("/errors/invalid-customer"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)));
    }

    @Test
    void domainExceptionReturnsUnprocessable() throws Exception {
        mockMvc.perform(get("/errors/domain"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("DOMAIN_ERROR"))
                .andExpect(jsonPath("$.message").value("Domain failure"))
                .andExpect(jsonPath("$.path").value("/errors/domain"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)));
    }

    @Test
    void illegalArgumentReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/errors/illegal"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("Illegal argument"))
                .andExpect(jsonPath("$.path").value("/errors/illegal"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)));
    }

    @Test
    void methodArgumentNotValidReturnsBadRequest() throws Exception {
        String payload = """
                {
                  "firstName": "",
                  "lastName": "Silva",
                  "documentType": "CPF",
                  "document": "123",
                  "email": "joao@example.com",
                  "phone": "11987654321"
                }
                """;

        mockMvc.perform(post("/errors/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("firstName"));
    }

    @Test
    void methodValidationReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/errors/method-validation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("METHOD_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("arg0"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("must not be blank"));
    }

    @Test
    void constraintViolationReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/errors/constraint"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSTRAINT_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("value"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("must not be blank"));
    }

    @Test
    void bindExceptionReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/errors/bind"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BIND_ERROR"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("field"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("must not be blank"));
    }

    @Test
    void typeMismatchReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/errors/type-mismatch").param("value", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TYPE_MISMATCH"))
                .andExpect(jsonPath("$.message").value("Parameter 'value' must be of type Integer"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)));
    }

    @Test
    void missingRequestParameterReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/errors/missing-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
                .andExpect(jsonPath("$.message").value("Missing required parameter 'required'"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)));
    }

    @Test
    void missingPathVariableReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/errors/missing-path"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PATH_VARIABLE"))
                .andExpect(jsonPath("$.message").value("Missing path variable 'customerId'"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)));
    }

    @Test
    void malformedJsonReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/errors/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"))
                .andExpect(jsonPath("$.message").value("Malformed JSON request"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)));
    }

    @Test
    void methodNotSupportedReturnsMethodNotAllowed() throws Exception {
        mockMvc.perform(put("/errors/method"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").value("Method 'PUT' not supported"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)));
    }

    @Test
    void mediaTypeNotSupportedReturnsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/errors/body")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not-json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("text/plain")))
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)));
    }

    @RestController
    @RequestMapping("/errors")
    @Validated
    static class ErrorTestController {

        private final Validator validator;

        ErrorTestController(Validator validator) {
            this.validator = validator;
        }

        @PostMapping("/body")
        public void body(@RequestBody @Valid CreateCustomerRequest request) {
        }

        @GetMapping("/type-mismatch")
        public void typeMismatch(@RequestParam Integer value) {
        }

        @GetMapping("/missing-param")
        public void missingParam(@RequestParam String required) {
        }

        @GetMapping("/invalid-customer")
        public void invalidCustomer() {
            throw new InvalidCustomerException("Invalid customer payload");
        }

        @GetMapping("/domain")
        public void domain() {
            throw new DomainException("Domain failure");
        }

        @GetMapping("/illegal")
        public void illegal() {
            throw new IllegalArgumentException("Illegal argument");
        }

        @GetMapping("/constraint")
        public void constraint() {
            Set<ConstraintViolation<ConstraintTarget>> violations =
                    validator.validate(new ConstraintTarget(""));
            throw new ConstraintViolationException(violations);
        }

        @GetMapping("/bind")
        public void bind() throws BindException {
            BindException ex = new BindException(new Object(), "target");
            ex.addError(new FieldError("target", "field", "must not be blank"));
            throw ex;
        }

        @GetMapping("/missing-path")
        public void missingPath() throws Exception {
            Method method = ErrorTestController.class.getDeclaredMethod("path", String.class);
            MethodParameter parameter = new MethodParameter(method, 0);
            throw new MissingPathVariableException("customerId", parameter);
        }

        @GetMapping("/method-validation")
        public void methodValidation() throws Exception {
            Method method = ErrorTestController.class.getDeclaredMethod("validatedMethod", String.class);
            MethodParameter parameter = new MethodParameter(method, 0);
            DefaultMessageSourceResolvable error =
                    new DefaultMessageSourceResolvable(new String[]{"NotBlank"}, "must not be blank");
            ParameterValidationResult result = new ParameterValidationResult(
                    parameter,
                    "",
                    List.of(error),
                    null,
                    null,
                    null,
                    (resolvable, type) -> null
            );
            MethodValidationResult validationResult =
                    MethodValidationResult.create(this, method, List.of(result));
            throw new HandlerMethodValidationException(validationResult);
        }

        @GetMapping("/method")
        public void method() {
        }

        @GetMapping("/path/{customerId}")
        public void path(@PathVariable @NotBlank String customerId) {
        }

        private void validatedMethod(@NotBlank String customerId) {
        }
    }

    private record ConstraintTarget(@NotBlank String value) {
    }
}
