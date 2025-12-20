package com.mvbr.retailstore.customer.domain.model;

import com.mvbr.retailstore.customer.domain.exception.DomainException;

import java.time.Instant;
import java.util.Objects;

/*

-----------------------------
REFACTOR:
-----------------------------

 Entendi: olhando apenas o domínio do microserviço ms-customer (ms-customer/src/main/java/com/mvbr/retailstore/customer/domain/model), segue o
  diagnóstico (nenhuma alteração feita):

  - Customer: invariantes básicos ok (IDs/nome/doc/email/phone/status/timestamps não nulos; updatedAt não pode ser antes de createdAt). touch() usa
    Instant.now() e pode falhar se o clock do host retroceder. Transições de status são permissivas (deactivate/block só checam idempotência), sem
    máquina de estados explícita. Se serializar direto, VO aparece como { "value": ... }.
  - VOs (CustomerId, Email, Phone): imutáveis e validam nulo/vazio; Email com regex simples e normalização só via normalized(), Phone sem validação
    de formato/tamanho. Serialização direta também vaza { "value": ... }.
  - Documento: DocumentType + document string só validam não-nulo/branco; sem checagem de formato/normalização de CPF/CNPJ.
  - Address: vazio, não blinda nada hoje.
  - CustomerStatus: enum simples, sem regras de transição centralizadas.

  Riscos práticos: se qualquer camada expuser domínio ou eventos sem DTO, consumidores verão VOs aninhados; dados de telefone/documento podem ficar
  inconsistentes; touch() dependente de clock pode causar exceções raras com skew. Se quiser reforçar, o caminho é DTOs de saída, validações de
  documento/telefone, e (se preciso) um clock injetado/máquina de estados mais restritiva.

› tudo que sair do dominio vai ser mapeado para dto...  garantido!!!!!


-------------------------------------------
IMPROVEMENT - FOR DDD PATTERN:
-------------------------------------------

 Visão rápida do domínio do ms-customer sob a ótica de DDD:

  - Tem um agregado claro (Customer) com identidade (CustomerId) e VOs (Email, Phone, CustomerId) que validam invariantes de formato/nulo. Isso está
    alinhado com DDD: entidade raiz + VOs imutáveis.
  - Regras de negócio básicas estão no próprio domínio (validações, idempotência em deactivate/block, checagem de timestamps). Não há vazamento de
    persistência nem dependência de infraestrutura — bom isolamento.
  - Faltam regras de transição de estados mais explícitas: hoje dá para sair de qualquer estado para INACTIVE ou BLOCKED, só com idempotência. Em
    DDD, vale a pena modelar o fluxo permitido ou uma policy que encapsule isso se o negócio exigir.
  - touch() depende de Instant.now() (clock implícito), o que reduz testabilidade e pode quebrar com clock skew. Em DDD costuma-se injetar um clock/
    serviço de tempo ou receber “agora” por parâmetro.
  - Address está vazio; se for parte do modelo, ele não está implementado como VO ainda.
  - Documentos/telefones não são normalizados/validados além de “non-empty”; se o negócio requer, os VOs poderiam incorporar essas regras para manter
    o “modelo rico”.
  - Não há eventos de domínio nem serviços de domínio aqui; se o contexto precisar notificar mudanças relevantes, eventos ou domain services poderiam
    aparecer.

  Em resumo: estrutura e isolamento estão de acordo com DDD (agregado raiz + VOs + invariantes básicas). Pontos a evoluir, se fizer sentido ao
  negócio: regras de transição de status mais restritas, validação/normalização mais forte em VOs, clock injetado para consistência/testabilidade, e
  preencher Address ou removê-lo se não fizer parte do modelo.

 */

public class Customer {

    private final CustomerId customerId;
    private String firstName;
    private String lastName;
    private DocumentType documentType;
    private String document;
    private Email email;
    private Phone phone;
    private CustomerStatus customerStatus;
    private final Instant createdAt;
    private Instant updatedAt;

    // -------------------------------------------------------------------
    // private constructor - force the use of factories...
    // -------------------------------------------------------------------

    private Customer(
            CustomerId customerId,
            String firstName,
            String lastName,
            DocumentType documentType,
            String document,
            Email email,
            Phone phone,
            CustomerStatus customerStatus,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.customerId = require(customerId, "Customer id is required");
        this.firstName = requireText(firstName, "First name is required");
        this.lastName = requireText(lastName, "Last name is required");
        this.documentType = require(documentType, "Document type is required");
        this.document = requireText(document, "Document is required");
        this.email = require(email, "Email is required");
        this.phone = require(phone, "Phone is required");
        this.customerStatus = require(customerStatus, "Customer status is required");
        this.createdAt = require(createdAt, "Created at is required");
        this.updatedAt = require(updatedAt, "Update at is required");

        if (this.updatedAt.isBefore(this.createdAt)) {
            throw new DomainException("Updated at cannot be before Created at");
        }
    }

    // -------------------------------------------------------------------
    // factory - creation of a new customer...
    // -------------------------------------------------------------------

    public static Customer createNew(CustomerId customerId,
                                     String firstName,
                                     String lastName,
                                     DocumentType documentType,
                                     String document,
                                     Email email,
                                     Phone phone,
                                     Instant now) {

        require(now, "now is required");

        return new Customer(
                customerId,
                firstName,
                lastName,
                documentType,
                document,
                email,
                phone,
                CustomerStatus.ACTIVE,
                now,
                now
        );

    }

    // -------------------------------------------------------------------
    // factory - rehydration of customer (db -> domain)...
    // -------------------------------------------------------------------

    public static Customer restore(CustomerId customerId,
                                   String firstName,
                                   String lastName,
                                   DocumentType documentType,
                                   String document,
                                   Email email,
                                   Phone phone,
                                   CustomerStatus status,
                                   Instant createdAt,
                                   Instant updatedAt) {

        return new Customer(customerId,
                firstName,
                lastName,
                documentType,
                document,
                email,
                phone,
                status,
                createdAt,
                updatedAt);

    }

    // -------------------------------------------------------------------
    // rules/behaviors of domain...
    // -------------------------------------------------------------------

    public void changeFirstName(String newFirstName) {
        this.firstName = requireText(newFirstName, "First name is required");
        touch();
    }

    public void changeLastName(String newLastName) {
        this.lastName = requireText(newLastName, "Last name is required");
        touch();
    }

    public void changeDocumentType(DocumentType newDocumentType) {
        this.documentType = require(newDocumentType, "Document type is required");
        touch();
    }

    public void changeDocument(String newDocument) {
        this.document = requireText(newDocument, "Document is required");
        touch();
    }

    public void changeEmail(Email newEmail) {
        this.email = require(newEmail, "Email is required");
        touch();
    }

    public void changePhone(Phone newPhone) {
        this.phone = require(newPhone, "Phone is required");
        touch();
    }

    public void deactivate() {
        if (this.customerStatus == CustomerStatus.INACTIVE) {  // simple idempotence...
            return;
        }
        this.customerStatus = CustomerStatus.INACTIVE;
        touch();
    }

    public void block() {
        if (this.customerStatus == CustomerStatus.BLOCKED) {
            return;
        }
        this.customerStatus = CustomerStatus.BLOCKED;
        touch();
    }

    public boolean isActive() {
        return this.customerStatus == CustomerStatus.ACTIVE;
    }

    private void touch() {
        var now = Instant.now();
        if (now.isBefore(this.updatedAt)) {
            throw new DomainException("Now cannot be before updated at");
        }
        this.updatedAt = now;
    }

    // -------------------------------------------------------------------
    // getters - domain exposes state securely...
    // -------------------------------------------------------------------

    public CustomerId getCustomerId() {
        return customerId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public String getDocument() {
        return document;
    }

    public Email getEmail() {
        return email;
    }

    public Phone getPhone() {
        return phone;
    }

    public CustomerStatus getCustomerStatus() {
        return customerStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }


    // -------------------------------------------------------------------
    // helpers of validation and normalization...
    // -------------------------------------------------------------------

    private static String requireText(String value, String message) {
        if (value == null) {
            throw new DomainException(message);
        }
        var valueCopy = value.trim();
        if (valueCopy.isBlank()) {
            throw new DomainException(message);
        }
        return valueCopy;
    }


    private static <T> T require(T value, String message) {
        return Objects.requireNonNull(value, message);
    }

}
