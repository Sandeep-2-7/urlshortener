package com.assignment.urlshortener.exception;

public class AliasAlreadyExistsException extends RuntimeException{

    public AliasAlreadyExistsException(String message){
        super("Custom alias already exists: " + message);
    }
}
