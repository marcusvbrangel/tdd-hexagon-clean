package com.mvbr.estudo.tdd.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateOrderItemRequest(
        @NotBlank String productId,
        @Min(1) int quantity,
        @NotNull @Positive BigDecimal price
) {}
