package com.vito.auth.infrastructure.adapter.inbound.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    
    @NotBlank(message = "Es obligatorio")
    @Email(message = "Debe ser un correo electrónico válido")
    private String email;
    
    @NotBlank(message = "Es obligatorio")
    private String password;
}
