package com.mvbr.retailstore.customer.infrastructure.adapter.in.rest.controller;

import com.mvbr.retailstore.customer.application.command.ActivateCustomerCommand;
import com.mvbr.retailstore.customer.application.command.BlockCustomerCommand;
import com.mvbr.retailstore.customer.application.command.CreateCustomerCommand;
import com.mvbr.retailstore.customer.application.command.DeactivateCustomerCommand;
import com.mvbr.retailstore.customer.application.command.UpdateCustomerCommand;
import com.mvbr.retailstore.customer.application.port.in.ActivateCustomerUseCase;
import com.mvbr.retailstore.customer.application.port.in.BlockCustomerUseCase;
import com.mvbr.retailstore.customer.application.port.in.CreateCustomerUseCase;
import com.mvbr.retailstore.customer.application.port.in.DeactivateCustomerUseCase;
import com.mvbr.retailstore.customer.application.port.in.UpdateCustomerUseCase;
import com.mvbr.retailstore.customer.domain.model.CustomerId;
import com.mvbr.retailstore.customer.infrastructure.adapter.in.rest.controller.command.CustomerCommandController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CustomerCommandControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CreateCustomerUseCase createCustomerUseCase;

    @Mock
    private UpdateCustomerUseCase updateCustomerUseCase;

    @Mock
    private ActivateCustomerUseCase activateCustomerUseCase;

    @Mock
    private DeactivateCustomerUseCase deactivateCustomerUseCase;

    @Mock
    private BlockCustomerUseCase blockCustomerUseCase;

    @BeforeEach
    void setUp() {
        CustomerCommandController controller = new CustomerCommandController(
                createCustomerUseCase,
                updateCustomerUseCase,
                activateCustomerUseCase,
                deactivateCustomerUseCase,
                blockCustomerUseCase
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void createReturns201AndPassesCommand() throws Exception {
        when(createCustomerUseCase.create(any())).thenReturn(new CustomerId("cust-1"));

        String payload = """
                {
                  "firstName": "Joao",
                  "lastName": "Silva",
                  "documentType": "CPF",
                  "document": "639.773.199-57",
                  "email": "joao@example.com",
                  "phone": "11987654321"
                }
                """;

        mockMvc.perform(post("/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value("cust-1"));

        ArgumentCaptor<CreateCustomerCommand> captor = ArgumentCaptor.forClass(CreateCustomerCommand.class);
        verify(createCustomerUseCase).create(captor.capture());
        CreateCustomerCommand command = captor.getValue();
        assertEquals("Joao", command.firstName());
        assertEquals("Silva", command.lastName());
        assertEquals("CPF", command.documentType());
        assertEquals("639.773.199-57", command.document());
        assertEquals("joao@example.com", command.email());
        assertEquals("11987654321", command.phone());
    }

    @Test
    void updateReturns200AndPassesCommand() throws Exception {
        when(updateCustomerUseCase.update(any())).thenReturn(new CustomerId("cust-9"));

        String payload = """
                {
                  "firstName": "Maria",
                  "lastName": "Oliveira",
                  "documentType": "CNPJ",
                  "document": "90.383.305/2048-93",
                  "email": "maria@example.com",
                  "phone": "11999998888"
                }
                """;

        mockMvc.perform(put("/customers/cust-9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("cust-9"));

        ArgumentCaptor<UpdateCustomerCommand> captor = ArgumentCaptor.forClass(UpdateCustomerCommand.class);
        verify(updateCustomerUseCase).update(captor.capture());
        UpdateCustomerCommand command = captor.getValue();
        assertEquals("cust-9", command.customerId());
        assertEquals("Maria", command.firstName());
        assertEquals("Oliveira", command.lastName());
        assertEquals("CNPJ", command.documentType());
        assertEquals("90.383.305/2048-93", command.document());
        assertEquals("maria@example.com", command.email());
        assertEquals("11999998888", command.phone());
    }

    @Test
    void activateReturns204AndPassesCommand() throws Exception {
        mockMvc.perform(post("/customers/cust-10/activate"))
                .andExpect(status().isNoContent());

        ArgumentCaptor<ActivateCustomerCommand> captor = ArgumentCaptor.forClass(ActivateCustomerCommand.class);
        verify(activateCustomerUseCase).activate(captor.capture());
        assertEquals("cust-10", captor.getValue().customerId());
    }

    @Test
    void deactivateReturns204AndPassesCommand() throws Exception {
        mockMvc.perform(post("/customers/cust-11/deactivate"))
                .andExpect(status().isNoContent());

        ArgumentCaptor<DeactivateCustomerCommand> captor = ArgumentCaptor.forClass(DeactivateCustomerCommand.class);
        verify(deactivateCustomerUseCase).deactivate(captor.capture());
        assertEquals("cust-11", captor.getValue().customerId());
    }

    @Test
    void blockReturns204AndPassesCommand() throws Exception {
        mockMvc.perform(post("/customers/cust-12/block"))
                .andExpect(status().isNoContent());

        ArgumentCaptor<BlockCustomerCommand> captor = ArgumentCaptor.forClass(BlockCustomerCommand.class);
        verify(blockCustomerUseCase).block(captor.capture());
        assertEquals("cust-12", captor.getValue().customerId());
    }
}
