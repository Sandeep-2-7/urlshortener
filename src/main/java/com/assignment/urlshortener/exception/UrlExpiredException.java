package com.assignment.urlshortener.exception;

public class UrlExpiredException extends RuntimeException{

    public UrlExpiredException(String shortCode){
        super("URL has expired for the short code "+ shortCode);
    }
}
