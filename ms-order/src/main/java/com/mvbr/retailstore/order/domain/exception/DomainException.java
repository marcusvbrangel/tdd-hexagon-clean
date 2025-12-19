package com.mvbr.retailstore.order.domain.exception;

public class DomainException extends RuntimeException {
    public DomainException(String messagem) {
        super(messagem);
    }
}