package com.assignment.urlshortener.exception;

public class UrlNotFoundException extends RuntimeException{

    public UrlNotFoundException(String shortCode){
        super("No URL found with the short code "+ shortCode);
    }
}
