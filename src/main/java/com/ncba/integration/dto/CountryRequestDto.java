package com.ncba.integration.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CountryRequestDto {
    @NotBlank(message = "Country name field is required")
    private String name;
}