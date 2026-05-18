package com.openbanking.authentication.exception;

import com.openbanking.authentication.config.AppConfig;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final MessageSource messageSource;

    @Autowired
    private AppConfig appConfig;

    @Autowired
    public GlobalExceptionHandler(final MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setMessage(ex.getMessage());
        errorResponse.setCode("400");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setMessage(ex.getMessage());
        errorResponse.setCode("400");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleMissingPathVariable(MissingPathVariableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setMessage(ex.getMessage());
        errorResponse.setCode("400");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setMessage(ex.getMessage());
        errorResponse.setCode("400");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(NoHandlerFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setMessage(ex.getMessage());
        errorResponse.setCode("400");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setMessage(ex.getMessage());
        errorResponse.setCode("400");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestPart(MissingServletRequestPartException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setMessage(ex.getMessage());
        errorResponse.setCode("400");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(ServletRequestBindingException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setMessage(ex.getMessage());
        errorResponse.setCode("400");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setMessage(ex.getMessage());
        errorResponse.setCode("400");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setMessage(ex.getMessage());
        errorResponse.setCode("400");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @Override
    public ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        BindingResult bindingResult = ex.getBindingResult();
        List<FieldError> fieldErrors = bindingResult.getFieldErrors();

        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setMessage(extractErrorMessage(fieldErrors));
        errorResponse.setCode("400");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    private String extractErrorMessage(List<FieldError> fieldErrors) {
        StringBuilder errorMessages = new StringBuilder();
        for (FieldError fieldError : fieldErrors) {
            errorMessages.append(fieldError.getField());
            errorMessages.append(" ");
            errorMessages.append(fieldError.getDefaultMessage());
            errorMessages.append(", ");
        }
        return errorMessages.toString();
    }

    @ExceptionHandler(value = {BusinessException.class})
    protected ResponseEntity<Object> businessException(final BusinessException ex, WebRequest request) {

        ErrorResponse businessError = new ErrorResponse();

        if (StringUtils.isNotBlank(ex.getErrorCode())) {
            try {
                String[] replacementValues = null;
                if (appConfig != null && appConfig.getValidationErrorCodes() != null) {
                    replacementValues = appConfig.getValidationErrorCodes().get(ex.getErrorCode());
                }
                final String mappedMessage = this.messageSource.getMessage(ex.getErrorCode(), replacementValues, Locale.getDefault());

                log.warn(
                        "handleBusinessException({}) ticketRef={}, errorCode={}, message={}",
                        request,
                        ex.getErrorCode(),
                        mappedMessage);
                businessError.setCode(ex.getErrorCode());
                businessError.setMessage(mappedMessage);
                return new ResponseEntity<>(businessError, HttpStatus.CONFLICT);
            } catch (NoSuchMessageException e) {
                log.error("handleBusinessException({}) Unmapped errorCode={}", ex.getMessage(), ex.getErrorCode(), ex);
                businessError.setCode(ex.getErrorCode());
                businessError.setMessage(e.getMessage());
                return new ResponseEntity<>(businessError, HttpStatus.CONFLICT);
            }
        } else {
            log.warn("handleBusinessException({}) ticketRef={} {}", ex);
        }
        businessError.setCode("409");
        businessError.setMessage(ex.getMessage());
        return new ResponseEntity<>(businessError, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(
            value = {
                BadRequestException.class,
                ConstraintViolationException.class,
                IllegalArgumentException.class,
                IllegalStateException.class,
                URISyntaxException.class,
                    DataIntegrityViolationException.class
            })
    protected ResponseEntity<Object> handleConflict(RuntimeException ex, WebRequest request) {

        String mappedMessage = ex.getMessage();
        String errorCode = "400";
        ErrorResponse vaildationErrors = new ErrorResponse();
        if (ex instanceof BadRequestException bre) {
            errorCode = (bre.getErrorCode() != null) ? bre.getErrorCode() : errorCode;
            if (StringUtils.isNotBlank(bre.getErrorCode())) {
                try {
                    mappedMessage = this.messageSource.getMessage(bre.getErrorCode(), null, Locale.getDefault());

                    vaildationErrors.setCode(errorCode);
                    vaildationErrors.setMessage(mappedMessage);

                    return handleExceptionInternal(
                            bre, vaildationErrors, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);

                } catch (NoSuchMessageException e) {
                    log.error("handleBusinessException({}) Unmapped errorCode={}", bre.getMessage(), errorCode, bre);

                    vaildationErrors.setCode(errorCode);
                    vaildationErrors.setMessage(e.getMessage());

                    return handleExceptionInternal(
                            bre, vaildationErrors, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
                }
            }
        }

        vaildationErrors.setCode(errorCode);
        vaildationErrors.setMessage(mappedMessage);

        return new ResponseEntity<>(vaildationErrors, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(value = {InternalErrorException.class, Exception.class, Throwable.class})
    protected ResponseEntity<Object> handleInternalErrorException(RuntimeException ex, WebRequest request) {
        log.error("handleInternalErrorException({}) Unmapped errorCode={}", ex.getMessage(), 500, ex);
        return handleExceptionInternal(ex, "", new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }
}
