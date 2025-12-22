package com.mvbr.retailstore.customer.infrastructure.adapter.in.rest.controller;

import com.mvbr.retailstore.customer.application.command.ActivateCustomerCommand;
import com.mvbr.retailstore.customer.application.command.BlockCustomerCommand;
import com.mvbr.retailstore.customer.application.command.DeactivateCustomerCommand;
import com.mvbr.retailstore.customer.application.command.GetCustomerCommand;
import com.mvbr.retailstore.customer.application.port.in.*;
import com.mvbr.retailstore.customer.domain.model.Customer;
import com.mvbr.retailstore.customer.domain.model.CustomerId;
import com.mvbr.retailstore.customer.infrastructure.adapter.in.rest.dto.CreateCustomerRequest;
import com.mvbr.retailstore.customer.infrastructure.adapter.in.rest.dto.CustomerIdResponse;
import com.mvbr.retailstore.customer.infrastructure.adapter.in.rest.dto.GetCustomerResponse;
import com.mvbr.retailstore.customer.infrastructure.adapter.in.rest.dto.UpdateCustomerRequest;
import com.mvbr.retailstore.customer.infrastructure.adapter.in.rest.mapper.CustomerRestMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/customers")
@Validated
public class CustomerCommandController {

    private final CreateCustomerUseCase createCustomerUseCase;
    private final UpdateCustomerUseCase updateCustomerUseCase;
    private final ActivateCustomerUseCase activateCustomerUseCase;
    private final DeactivateCustomerUseCase deactivateCustomerUseCase;
    private final BlockCustomerUseCase blockCustomerUseCase;
    private final GetCustomerUseCase getCustomerUseCase;
    private final CustomerRestMapper customerRestMapper;

    public CustomerCommandController(CreateCustomerUseCase createCustomerUseCase,
                                     UpdateCustomerUseCase updateCustomerUseCase,
                                     ActivateCustomerUseCase activateCustomerUseCase,
                                     DeactivateCustomerUseCase deactivateCustomerUseCase,
                                     BlockCustomerUseCase blockCustomerUseCase,
                                     GetCustomerUseCase getCustomerUseCase,
                                     CustomerRestMapper customerRestMapper) {
        this.createCustomerUseCase = createCustomerUseCase;
        this.updateCustomerUseCase = updateCustomerUseCase;
        this.activateCustomerUseCase = activateCustomerUseCase;
        this.deactivateCustomerUseCase = deactivateCustomerUseCase;
        this.blockCustomerUseCase = blockCustomerUseCase;
        this.getCustomerUseCase = getCustomerUseCase;
        this.customerRestMapper = customerRestMapper;
    }

    @PostMapping
    public ResponseEntity<CustomerIdResponse> create(@RequestBody @Valid CreateCustomerRequest request) {
        CustomerId customerId = createCustomerUseCase.create(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CustomerIdResponse(customerId.value()));
    }

    @PutMapping("/{customerId}")
    public ResponseEntity<CustomerIdResponse> update(@PathVariable String customerId,
                                                     @RequestBody @Valid UpdateCustomerRequest request) {
        CustomerId updatedId = updateCustomerUseCase.update(request.toCommand(customerId));
        return ResponseEntity.ok(new CustomerIdResponse(updatedId.value()));
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<GetCustomerResponse> getById(@PathVariable
                                                    @NotBlank(message = "Customer id is required") String customerId) {
        Optional<Customer> customer = getCustomerUseCase.getById(new GetCustomerCommand(customerId));

        return customer
                .map(customerRestMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{customerId}/activate")
    public ResponseEntity<Void> activate(@PathVariable
                                         @NotBlank(message = "Customer id is required") String customerId) {
        activateCustomerUseCase.activate(new ActivateCustomerCommand(customerId));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{customerId}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable
                                           @NotBlank(message = "Customer id is required") String customerId) {
        deactivateCustomerUseCase.deactivate(new DeactivateCustomerCommand(customerId));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{customerId}/block")
    public ResponseEntity<Void> block(@PathVariable
                                      @NotBlank(message = "Customer id is required") String customerId) {
        blockCustomerUseCase.block(new BlockCustomerCommand(customerId));
        return ResponseEntity.noContent().build();
    }

}
