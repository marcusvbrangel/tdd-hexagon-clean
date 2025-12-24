package com.mvbr.retailstore.checkout.domain.exception;

/**
 * Excecao de dominio para violacoes de regra na saga de checkout.
 * Lancada pelas entidades do dominio quando uma transicao e invalida.
 */
public class SagaDomainException extends RuntimeException {
    /**
     * Cria a excecao com a mensagem que explica a regra violada.
     */
    public SagaDomainException(String message) {
        super(message);
    }
}
