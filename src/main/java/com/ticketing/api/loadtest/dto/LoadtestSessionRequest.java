package com.ticketing.api.loadtest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class LoadtestSessionRequest {

    @NotBlank(message = "email must not be blank")
    @Email(message = "email must be valid")
    private String email;
}
