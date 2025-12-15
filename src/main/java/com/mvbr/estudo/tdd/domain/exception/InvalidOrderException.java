package com.mvbr.estudo.tdd.domain.exception;

public class InvalidOrderException extends RuntimeException {
    public InvalidOrderException(String messagem) {
        super(messagem);
    }
}
