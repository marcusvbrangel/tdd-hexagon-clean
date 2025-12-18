package com.mvbr.estudo.tdd.domain.exception;

public class DomainException extends RuntimeException {
    public DomainException(String messagem) {
        super(messagem);
    }
}