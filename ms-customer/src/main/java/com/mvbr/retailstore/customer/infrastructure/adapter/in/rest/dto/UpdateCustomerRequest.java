package com.mvbr.retailstore.customer.infrastructure.adapter.in.rest.dto;

import com.mvbr.retailstore.customer.application.command.UpdateCustomerCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateCustomerRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank
        @Pattern(regexp = "CPF|CNPJ", flags = Pattern.Flag.CASE_INSENSITIVE)
        String documentType,
        @NotBlank String document,
        @NotBlank
        @Email
        String email,
        @NotBlank
        @Pattern(regexp = "^[0-9+()\\s-]{8,20}$")
        String phone
) {
    public UpdateCustomerCommand toCommand(String customerId) {
        return new UpdateCustomerCommand(customerId, firstName, lastName, documentType, document, email, phone);
    }
}
