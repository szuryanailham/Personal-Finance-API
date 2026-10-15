package com.ilham.personal_finance_api.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import com.ilham.personal_finance_api.exception.DataAlreadyExistedException;
import com.ilham.personal_finance_api.exception.InvalidOtpException;
import com.ilham.personal_finance_api.exception.TooManyRequestsException;
import com.ilham.personal_finance_api.dto.WebResponse;

@RestControllerAdvice
public class ErrorController {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<WebResponse<Object>> handleResponseStatusException(ResponseStatusException ex) {
        WebResponse<Object> body = new WebResponse<>();
        body.setErrors(ex.getReason());

        HttpStatusCode status = ex.getStatusCode();
        return new ResponseEntity<>(body, status);
    }

    @ExceptionHandler(DataAlreadyExistedException.class)
    public ResponseEntity<WebResponse<Object>> handleEmailAlreadyExists(DataAlreadyExistedException ex) {
        WebResponse<Object> body = new WebResponse<>();
        body.setErrors(ex.getMessage());

        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<WebResponse<Object>> handleTooManyRequests(TooManyRequestsException ex) {
        WebResponse<Object> body = new WebResponse<>();
        body.setErrors(ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(body);
    }

    @ExceptionHandler(InvalidOtpException.class)
    public ResponseEntity<WebResponse<Object>> handleInvalidOtp(InvalidOtpException ex) {
        WebResponse<Object> body = new WebResponse<>();
        body.setErrors(ex.getMessage());
        return ResponseEntity.badRequest().body(body);
    }


    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<WebResponse<Object>> handleValidationException(MethodArgumentNotValidException e) {
        String error = e.getBindingResult()
                .getFieldErrors()
                .get(0)
                .getDefaultMessage();


        WebResponse<Object> response = new WebResponse<>();
        response.setErrors(error);

                 return ResponseEntity
                .badRequest()
                .body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<WebResponse<Object>> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
        String error = "Parameter '" + e.getName() + "' has invalid value: " + e.getValue();

        WebResponse<Object> response = new WebResponse<>();
        response.setErrors(error);

        return ResponseEntity
                .badRequest()
                .body(response);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<WebResponse<Object>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        WebResponse<Object> response = new WebResponse<>();
        response.setErrors("File size exceeds the limit");
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(response);
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public ResponseEntity<WebResponse<Object>> handleMissingFile(Exception e) {
        WebResponse<Object> response = new WebResponse<>();
        response.setErrors("File is required");
        return ResponseEntity.badRequest().body(response);
    }

}
