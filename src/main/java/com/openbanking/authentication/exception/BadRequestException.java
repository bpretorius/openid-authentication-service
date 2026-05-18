package com.openbanking.authentication.exception;

public class BadRequestException extends BusinessException {

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(final String errorCode, final String message) {
        super(errorCode, message);
    }
}
