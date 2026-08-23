package com.assignment.urlshortener.exception;

public class UrlDeactivatedException extends RuntimeException{
    public UrlDeactivatedException(String message){
        super("This URL has been deactivated : "+message);
    }
}
