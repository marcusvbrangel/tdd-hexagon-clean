package com.mvbr.retailstore.inventory.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/*
qual o objetivo desta tabela: processed_messages

A tabela processed_messages existe pra resolver um problema inevitável em sistemas com Kafka/saga:

a mesma mensagem pode chegar mais de uma vez (por retry, rebalance, consumer crash depois de processar mas antes do commit, timeouts, etc.). E se você executar o comando duas vezes, você pode:

reservar estoque em dobro (reserved += qty duas vezes)

liberar duas vezes (reserved -= qty e pode ficar negativo)

publicar eventos duplicados e bagunçar a saga

Então o objetivo dela é idempotência: garantir que cada commandId (messageId) seja processado no máximo uma vez.

O que ela previne (na prática)
Cenário clássico

inventory.reserve chega no ms-inventory

você faz reserved += 2 e salva no banco

antes de commitar o offset, o consumer cai

Kafka reentrega a mesma mensagem

sem idempotência: você reserva de novo → estoque errado

Com processed_messages:

na primeira vez você insere message_id = commandId

na segunda vez, a inserção dá “duplicate key”

você entende “já processei” e não mexe no estoque de novo

no máximo você republica o evento (pra saga destravar), mas sem efeitos colaterais

Por que isso não é “igual ao Outbox”?

São coisas diferentes e complementares:

Outbox (outbox_messages)

Garante publicação confiável do que você decidiu dentro da transação.

“Eu já atualizei o estado no DB, agora preciso garantir que vou publicar o evento.”

Processed messages (processed_messages)

Garante processamento idempotente da entrada.

“Eu posso receber o mesmo comando N vezes, mas só vou aplicar uma vez.”

Um cuida da saída, o outro da entrada.

Quais campos fazem sentido nela

No MVP, o essencial é:

message_id (PK) → normalmente o commandId vindo do checkout

message_type → inventory.reserve / inventory.release

aggregate_id → orderId (ajuda debug)

processed_at

Isso te dá:

idempotência forte (PK unique)

rastreabilidade (debug, auditoria)

“Mas eu já tenho uk_outbox_event_id, não resolve?”

Não. Aquilo evita duplicar eventos na outbox (saída).
O problema aqui é duplicar efeitos ao executar o comando de novo (entrada). Sem processed_messages você pode duplicar reserva e ainda assim publicar 1 evento só — e o estado fica errado.

Dica de ouro: qual id usar como PK?

Use commandId do comando Kafka.

No teu checkout, você faz:

String commandId = newCommandId();


Esse é o cara perfeito pra virar processed_messages.message_id.

Se você quiser deixar ainda mais “blindado”, eu te passo um upgrade simples:

processed_messages + uk(orderId, message_type) ou uk(orderId, command_type) dependendo da sua estratégia de retries

e lógica para “republicar o outcome” (reserved/rejected) quando detectar duplicata

Mas do jeito que está, já resolve 90% dos bugs reais de saga com Kafka.

 */



/**
 * Entidade JPA que registra comandos ja processados (idempotencia).
 */
@Entity
@Table(name = "processed_messages")
public class JpaProcessedMessageEntity {

    @Id
    @Column(name = "message_id", length = 64)
    private String messageId;

    @Column(name = "message_type", nullable = false, length = 64)
    private String messageType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected JpaProcessedMessageEntity() {
    }

    public JpaProcessedMessageEntity(String messageId, String messageType, String aggregateId, Instant processedAt) {
        this.messageId = messageId;
        this.messageType = messageType;
        this.aggregateId = aggregateId;
        this.processedAt = processedAt;
    }

    public String getMessageId() { return messageId; }
}
