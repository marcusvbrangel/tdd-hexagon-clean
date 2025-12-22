package com.mvbr.retailstore.customer.infrastructure.adapter.in.rest.dto;

public record GetCustomerResponse(String firstName,
                                  String lastName,
                                  String documentType,
                                  String document,
                                  String email,
                                  String phone,
                                  String customerStatus) {
}
