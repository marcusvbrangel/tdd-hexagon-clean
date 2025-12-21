package com.mvbr.retailstore.customer.infrastructure.adapter.in.rest.controller.command;

import com.mvbr.retailstore.customer.application.command.ActivateCustomerCommand;
import com.mvbr.retailstore.customer.application.command.BlockCustomerCommand;
import com.mvbr.retailstore.customer.application.command.DeactivateCustomerCommand;
import com.mvbr.retailstore.customer.application.port.in.ActivateCustomerUseCase;
import com.mvbr.retailstore.customer.application.port.in.BlockCustomerUseCase;
import com.mvbr.retailstore.customer.application.port.in.CreateCustomerUseCase;
import com.mvbr.retailstore.customer.application.port.in.DeactivateCustomerUseCase;
import com.mvbr.retailstore.customer.application.port.in.UpdateCustomerUseCase;
import com.mvbr.retailstore.customer.domain.model.CustomerId;
import com.mvbr.retailstore.customer.infrastructure.adapter.in.rest.dto.CreateCustomerRequest;
import com.mvbr.retailstore.customer.infrastructure.adapter.in.rest.dto.CustomerIdResponse;
import com.mvbr.retailstore.customer.infrastructure.adapter.in.rest.dto.UpdateCustomerRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers")
@Validated
public class CustomerCommandController {

    private final CreateCustomerUseCase createCustomerUseCase;
    private final UpdateCustomerUseCase updateCustomerUseCase;
    private final ActivateCustomerUseCase activateCustomerUseCase;
    private final DeactivateCustomerUseCase deactivateCustomerUseCase;
    private final BlockCustomerUseCase blockCustomerUseCase;

    public CustomerCommandController(CreateCustomerUseCase createCustomerUseCase,
                                     UpdateCustomerUseCase updateCustomerUseCase,
                                     ActivateCustomerUseCase activateCustomerUseCase,
                                     DeactivateCustomerUseCase deactivateCustomerUseCase,
                                     BlockCustomerUseCase blockCustomerUseCase) {
        this.createCustomerUseCase = createCustomerUseCase;
        this.updateCustomerUseCase = updateCustomerUseCase;
        this.activateCustomerUseCase = activateCustomerUseCase;
        this.deactivateCustomerUseCase = deactivateCustomerUseCase;
        this.blockCustomerUseCase = blockCustomerUseCase;
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

    @PostMapping("/{customerId}/activate")
    public ResponseEntity<Void> activate(@PathVariable @NotBlank(message = "Customer id is required") String customerId) {
        activateCustomerUseCase.activate(new ActivateCustomerCommand(customerId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{customerId}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable @NotBlank(message = "Customer id is required") String customerId) {
        deactivateCustomerUseCase.deactivate(new DeactivateCustomerCommand(customerId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{customerId}/block")
    public ResponseEntity<Void> block(@PathVariable @NotBlank(message = "Customer id is required") String customerId) {
        blockCustomerUseCase.block(new BlockCustomerCommand(customerId));
        return ResponseEntity.noContent().build();
    }

}
