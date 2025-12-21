package com.mvbr.retailstore.customer.application.command;

public record CreateCustomerCommand(
        String firstName,
        String lastName,
        String documentType,
        String document,
        String email,
        String phone
) {
}
