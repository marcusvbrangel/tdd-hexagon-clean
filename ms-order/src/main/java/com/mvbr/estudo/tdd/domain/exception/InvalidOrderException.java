package com.mvbr.estudo.tdd.domain.exception;

public class InvalidOrderException extends DomainException {
    public InvalidOrderException(String messagem) {
        super(messagem);
    }
}
