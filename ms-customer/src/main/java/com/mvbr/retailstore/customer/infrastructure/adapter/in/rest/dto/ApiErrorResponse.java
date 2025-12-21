package com.mvbr.retailstore.customer.infrastructure.adapter.in.rest.dto;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        int status,
        String code,
        String message,
        String path,
        Instant timestamp,
        List<FieldErrorResponse> fieldErrors
) {
}
