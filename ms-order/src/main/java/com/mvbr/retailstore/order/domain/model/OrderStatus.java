package com.mvbr.retailstore.order.domain.model;

public enum OrderStatus {

    /**
     * Pedido ainda não foi colocado (ex.: carrinho/draft).
     * Não iniciou saga.
     */
    DRAFT,

    /**
     * Pedido colocado. Saga de checkout deve processar (inventory/payment/shipping...).
     */
    PLACED,

    /**
     * Checkout concluído com sucesso (pelo orquestrador).
     * Pedido está válido para execução operacional.
     */
    CONFIRMED,

    /**
     * Pedido cancelado (falha na saga, timeout, ou cancelamento permitido).
     * Estado final (no seu estágio atual).
     */
    CANCELED,

    /**
     * Pedido finalizado por completo (seu conceito):
     * pagamento OK + estoque efetivado + envio concluído + nota fiscal emitida/enviada.
     */
    COMPLETED
}
