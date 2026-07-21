package edu.eci.arsw.RoyalArena.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateProfileRequestDTO {

    /**
     * ID del usuario en Auth Service. Cuando Auth haga la auto-creación,
     * pasará este valor. Se valida que venga porque es la referencia clave.
     */
    @NotNull(message = "userId is required")
    private Long userId;

    /**
     * Nombre visible inicial. Normalmente será el username de Auth,
     * pero se recibe explícitamente para dar flexibilidad.
     */
    @NotBlank(message = "displayName is required")
    @Size(min = 3, max = 50, message = "displayName must be between 3 and 50 characters")
    private String displayName;
}