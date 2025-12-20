package com.mvbr.retailstore.customer.domain.model;

import com.mvbr.retailstore.customer.domain.event.CustomerChangedEvent;
import com.mvbr.retailstore.customer.domain.event.CustomerCreatedEvent;
import com.mvbr.retailstore.customer.domain.event.DomainEvent;
import com.mvbr.retailstore.customer.domain.exception.DomainException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// ----------------------------------------------------------------------
// WARNING - LGPD/PII:
// Individual/Corporate Taxpayer Registry (CPF/CNPJ) numbers, email,
// and phone numbers should only be shared if absolutely necessary...
// ----------------------------------------------------------------------

// Entity...
public class Customer {

    private final CustomerId customerId;
    private String firstName;
    private String lastName;
    private Document document;
    private Email email;
    private Phone phone;
    private CustomerStatus customerStatus;
    private final Instant createdAt;
    private Instant updatedAt;

    private final List<DomainEvent> events;

    // -------------------------------------------------------------------
    // private constructor - force the use of factories...
    // -------------------------------------------------------------------

    private Customer(
            CustomerId customerId,
            String firstName,
            String lastName,
            Document document,
            Email email,
            Phone phone,
            CustomerStatus customerStatus,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.customerId = require(customerId, "Customer id is required");
        this.firstName = requireText(firstName, "First name is required");
        this.lastName = requireText(lastName, "Last name is required");
        this.document = require(document, "Document is required");
        this.email = require(email, "Email is required");
        this.phone = require(phone, "Phone is required");
        this.customerStatus = require(customerStatus, "Customer status is required");
        this.createdAt = require(createdAt, "Created at is required");
        this.updatedAt = require(updatedAt, "Update at is required");

        this.events = new ArrayList<>();

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

        var documentValue = new Document(documentType, document);

        var customer = new Customer(
                customerId,
                firstName,
                lastName,
                documentValue,
                email,
                phone,
                CustomerStatus.ACTIVE,
                now,
                now
        );

        customer.registerEvent(CustomerCreatedEvent.of(customerId, now));

        return customer;

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

        var documentValue = new Document(documentType, document);

        return new Customer(customerId,
                firstName,
                lastName,
                documentValue,
                email,
                phone,
                status,
                createdAt,
                updatedAt);

    }

    // -------------------------------------------------------------------
    // rules/behaviors of domain...
    // -------------------------------------------------------------------

    public void changeFirstName(String newFirstName, Instant now) {
        var normalized = requireText(newFirstName, "First name is required");
        if (normalized.equals(this.firstName)) {
            return;
        }
        applyChange(now, () -> this.firstName = normalized);
    }

    public void changeLastName(String newLastName, Instant now) {
        var normalized = requireText(newLastName, "Last name is required");
        if (normalized.equals(this.lastName)) {
            return;
        }
        applyChange(now, () -> this.lastName = normalized);
    }

    public void changeDocument(DocumentType newDocumentType, String newDocument, Instant now) {
        var documentValue = new Document(newDocumentType, newDocument);
        if (documentValue.equals(this.document)) {
            return;
        }
        applyChange(now, () -> this.document = documentValue);
    }

    public void changeEmail(Email newEmail, Instant now) {
        var emailValue = require(newEmail, "Email is required");
        if (emailValue.equals(this.email)) {
            return;
        }
        applyChange(now, () -> this.email = emailValue);
    }

    public void changePhone(Phone newPhone, Instant now) {
        var phoneValue = require(newPhone, "Phone is required");
        if (phoneValue.equals(this.phone)) {
            return;
        }
        applyChange(now, () -> this.phone = phoneValue);
    }

    public void deactivate(Instant now) {
        if (this.customerStatus == CustomerStatus.INACTIVE) {  // simple idempotence...
            return;
        }
        applyChange(now, () -> this.customerStatus = CustomerStatus.INACTIVE);
    }

    public void block(Instant now) {
        if (this.customerStatus == CustomerStatus.BLOCKED) {
            return;
        }
        applyChange(now, () -> this.customerStatus = CustomerStatus.BLOCKED);
    }

    public boolean isActive() {
        return this.customerStatus == CustomerStatus.ACTIVE;
    }

    private void touch(Instant now) {
        this.updatedAt = now;
        this.registerEvent(CustomerChangedEvent.of(customerId, now));
    }

    private void validateNow(Instant now) {
        require(now, "now is required");
        if (this.updatedAt == null) {
            throw new DomainException("Update at is required");
        }
        if (now.isBefore(this.updatedAt)) {
            throw new DomainException("Now cannot be before updated at");
        }
    }

    private void applyChange(Instant now, Runnable mutation) {
        validateNow(now);
        mutation.run();
        touch(now);
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

    public Document getDocument() {
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
        if (value == null) {
            throw new DomainException(message);
        }
        return value;
    }

    // -------------------------------------------------------------------
    // events control...
    // -------------------------------------------------------------------

    private void registerEvent(DomainEvent event) {
        if (event == null) {
            throw new DomainException("Event cannot be null");
        }
        this.events.add(event);
    }

    public List<DomainEvent> pullEvents() {
        List<DomainEvent> copy = List.copyOf(events);
        events.clear();
        return copy;
    }

/*

QUESTION:
------------
01 - Esta instrução de codigo a seguir, esta relacionada a indepotencia?
quais as vantagens eu ganho com esta instrução?
-->>  var normalized = requireText(newFirstName, "First name is required");
      if (normalized.equals(this.firstName)) {
        return;
      }


ANSWER:
----------
Sim — isso está ligado à idempotência e a “no‑op updates”. O trecho:

  var normalized = requireText(newFirstName, "First name is required");
  if (normalized.equals(this.firstName)) {
      return;
  }

  faz com que repetir a mesma atualização não gere efeito. Isso traz vantagens:

  - Evita updatedAt mudar sem necessidade.
  - Evita disparar evento de “changed” sem mudança real (menos ruído em Kafka, logs, auditoria).
  - Menos escrita no banco e menos concorrência/lock.
  - Com normalização (requireText dá trim), evita tratar “ João ” como mudança.

  As empresas valorizam? Sim, especialmente em sistemas com eventos, auditoria e alto volume. É visto como boa
  prática por reduzir ruído e custo.

  É “mais seguro”? Em termos de consistência e previsibilidade, sim. Em termos de segurança (vazamento/
  ataques), não muda muito.

  Observação importante: isso só é bom se você não precisa registrar “tentativas de atualização iguais”. Se a
  regra do negócio exigir “qualquer chamada atualiza o timestamp”, então esse return não serve — você deveria
  sempre chamar touch.

  Resumo: é um “guard clause” para tornar a atualização idempotente e reduzir efeitos colaterais.


QUESTION:
------------
No metodo: private void applyChange(Instant now, Runnable mutation) {    ... para que voce
colocou "Runnable"?  é mais seguro? é boa prática? é para o que?


ANSWER:
----------

Runnable mutation é só um callback: uma interface funcional com run() (sem argumentos e sem
  retorno). Usei porque o applyChange precisa receber apenas a ação de mudar o estado e fazer o
  resto (validar now e chamar touch) sempre igual.

  - Não é “mais seguro”.
  - É para evitar repetição e garantir consistência (ninguém esquece validateNow/touch).
  - Apesar do nome Runnable lembrar threads, aqui não tem thread nenhuma; é só uma lambda.

o call site precisa fazer a mutação fora. Se você fizer:

  this.firstName = normalized;
  applyChange(now);

  e validateNow(now) falhar, você já mudou o estado, mas não atualizou updatedAt nem registrou
  evento — fica inconsistente.

  Então, só seria seguro se você sempre validar antes de mutar:

  validateNow(now);
  this.firstName = normalized;
  touch(now);

  Exemplo do uso:
  applyChange(now, () -> this.firstName = normalized);

O Runnable existia justamente para garantir:
  validar → mutar → touch, sempre.


AVALIAÇAO:
----------------

  A abordagem está seguindo boa prática de DDD.

  Pontos que mostram alinhamento:

    - Agregado claro: Customer é a raiz com identidade (CustomerId);
    - VOs ricos: Document, Email, Phone validam invariantes;
    - Fábricas: createNew e restore separam criação vs. reidratação
    - Regras no domínio: mudanças passam por métodos do agregado, não por setters;
    - Eventos de domínio: CustomerCreatedEvent e CustomerChangedEvent emitidos no próprio agregado;
    - Sem infraestrutura no domínio: não há repositório, ORM, etc. dentro da entidade.

  Então, sim — está alinhado com DDD.




+++++++++++++++++++++++++++++++++++++++++

 Aqui vai um exemplo só do método execute na camada application, seguindo o mesmo padrão do OrderCommandService:

  @Transactional
  public CustomerId execute(CreateCustomerCommand cmd) {
      CustomerId customerId = customerIdGenerator.nextId(); // ou new CustomerId(cmd.customerId())
      Instant now = clock.instant(); // ou Instant.now()

      Customer customer = Customer.createNew(
              customerId,
              cmd.firstName(),
              cmd.lastName(),
              cmd.documentType(), // se vier String, usar DocumentType.valueOf(...)
              cmd.document(),
              new Email(cmd.email()),
              new Phone(cmd.phone()),
              now
      );

      customerRepository.save(customer);

      customer.pullEvents().forEach(eventPublisher::publish);

      return customerId;
  }

+++++++++++++++++++++++++++++++++++++++++

 */

}
