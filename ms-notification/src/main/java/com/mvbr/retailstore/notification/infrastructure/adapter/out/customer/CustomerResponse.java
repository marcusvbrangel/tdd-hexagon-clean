package com.mvbr.retailstore.notification.infrastructure.adapter.out.customer;

public record CustomerResponse(
        String firstName,
        String lastName,
        String documentType,
        String document,
        String email,
        String phone,
        String customerStatus
) {
}
