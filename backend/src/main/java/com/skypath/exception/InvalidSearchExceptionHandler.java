package com.skypath.exception;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

import java.util.Map;

/** Turns search-input validation failures (bad airport codes, etc.) into a clean 400 instead of a 500. */
@Produces
@Singleton
@Requires(classes = IllegalArgumentException.class)
public class InvalidSearchExceptionHandler implements ExceptionHandler<IllegalArgumentException, HttpResponse<Map<String, String>>> {

    @Override
    public HttpResponse<Map<String, String>> handle(HttpRequest request, IllegalArgumentException exception) {
        return HttpResponse.status(HttpStatus.BAD_REQUEST).body(Map.of("error", exception.getMessage()));
    }
}
