package com.ilham.personal_finance_api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import com.ilham.personal_finance_api.exception.DataAlreadyExistedException;
import com.ilham.personal_finance_api.model.WebResponse;

@RestControllerAdvice
public class ErrorController {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<WebResponse<Object>> handleResponseStatusException(ResponseStatusException ex) {
        WebResponse<Object> body = WebResponse.builder()
                .data(null)
                .errors(ex.getReason())
                .build();

        HttpStatusCode status = ex.getStatusCode();
        return new ResponseEntity<>(body, status);
    }

    @ExceptionHandler(DataAlreadyExistedException.class)
    public ResponseEntity<WebResponse<Object>> handleEmailAlreadyExists(DataAlreadyExistedException ex) {
        WebResponse<Object> body = WebResponse.builder()
                .data(null)
                .errors(ex.getMessage())
                .build();

        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }


    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<WebResponse<Object>> handleValidationException(MethodArgumentNotValidException e) {
        String error = e.getBindingResult()
                .getFieldErrors()
                .get(0)
                .getDefaultMessage();


        WebResponse<Object> response =
                new WebResponse<>(null, null, error, null);

                 return ResponseEntity
                .badRequest()
                .body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<WebResponse<Object>> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
        String error = "Parameter '" + e.getName() + "' has invalid value: " + e.getValue();

        WebResponse<Object> response =
                new WebResponse<>(null, null, error, null);

        return ResponseEntity
                .badRequest()
                .body(response);
    }

}
