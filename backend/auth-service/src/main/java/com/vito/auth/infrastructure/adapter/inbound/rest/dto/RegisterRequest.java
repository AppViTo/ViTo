package com.vito.auth.infrastructure.adapter.inbound.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    
    // Restricciones del schema RegisterRequest de openapi.yaml
    @NotBlank(message = "Es obligatorio")
    @Size(min = 3, max = 30, message = "Debe tener entre 3 y 30 caracteres")
    private String username;
    
    @NotBlank(message = "Es obligatorio")
    @Email(message = "Debe ser un correo electrónico válido")
    private String email;
    
    @NotBlank(message = "Es obligatorio")
    @Size(min = 8, message = "Mínimo 8 caracteres")
    private String password;
    
    private String displayName;
}
