package com.assignment.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ShortenRequest {

    @NotBlank(message = "Url should not be blank")
    @Pattern(regexp = "^(https?://).+", message = "URL must start with http:// or https://. Please correct the URL and try again...")
    private String originalUrl;

    private String customAlias;

    private Integer expiryInDays;

}
