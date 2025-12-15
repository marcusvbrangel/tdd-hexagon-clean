package com.mvbr.estudo.tdd.infrastructure.adapter.in.web.dto;

public record ApiError(int status, String message, String path) { }
