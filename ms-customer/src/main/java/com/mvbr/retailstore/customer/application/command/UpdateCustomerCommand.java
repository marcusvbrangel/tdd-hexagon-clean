package com.mvbr.retailstore.customer.application.command;

public record UpdateCustomerCommand(
        String customerId,
        String firstName,
        String lastName,
        String documentType,
        String document,
        String email,
        String phone
) {
}
