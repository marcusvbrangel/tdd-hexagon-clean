package com.mvbr.retailstore.customer.infrastructure.adapter.in.rest.mapper;

import com.mvbr.retailstore.customer.domain.model.Customer;
import com.mvbr.retailstore.customer.domain.model.CustomerStatus;
import com.mvbr.retailstore.customer.infrastructure.adapter.in.rest.dto.GetCustomerResponse;
import org.springframework.stereotype.Component;

@Component
public class CustomerRestMapper {

    public GetCustomerResponse toResponse(Customer customer) {
        return new GetCustomerResponse(
                customer.getFirstName(),
                customer.getLastName(),
                customer.getDocument().type().name(),
                customer.getDocument().value(),
                customer.getEmail().normalized(),
                customer.getPhone().toString(),
                customer.getCustomerStatus().toString()
        );
    }
}
