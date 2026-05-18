package com.openbanking.authentication.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final String errorCode;

    public BusinessException(final String errorCode) {
        this.errorCode = errorCode;
    }

    public BusinessException(final String errorCode, final String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(final String errorCode, final Throwable cause) {
        super(cause);
        this.errorCode = errorCode;
    }

    public BusinessException(final String errorCode, final String message, final Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
