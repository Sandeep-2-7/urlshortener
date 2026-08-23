package com.assignment.urlshortener.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class Base62Encoder {

//    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
//    private static final int BASE = 62;
//
//    public String encode(long id){
//        StringBuilder sb = new StringBuilder();
//        while (id > 0){
//            sb.append(ALPHABET.charAt((int) (id%BASE)));
//            id=id/BASE;
//        }
//        return sb.reverse().toString();
//    }

    public String encode() {
        String characters =
                "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

        StringBuilder result = new StringBuilder();

        for (int i = 0; i < 7; i++) {
            int index = ThreadLocalRandom.current()
                    .nextInt(characters.length());

            result.append(characters.charAt(index));
        }

        return result.toString();
    }
}
