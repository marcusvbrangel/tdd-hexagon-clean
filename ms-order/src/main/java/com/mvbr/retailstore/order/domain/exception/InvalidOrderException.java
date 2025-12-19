package com.mvbr.retailstore.order.domain.exception;

public class InvalidOrderException extends DomainException {
    public InvalidOrderException(String messagem) {
        super(messagem);
    }
}
