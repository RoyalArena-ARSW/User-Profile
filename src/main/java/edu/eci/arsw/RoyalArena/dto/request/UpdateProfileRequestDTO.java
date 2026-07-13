package edu.eci.arsw.royalarena.profile.dto.request;

import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileRequestDTO {

    /**
     * Nuevo nombre visible. Opcional: si viene null, no se cambia.
     */
    @Size(min = 3, max = 50, message = "displayName must be between 3 and 50 characters")
    private String displayName;

    /**
     * Nueva URL de avatar. Opcional: si viene null, no se cambia.
     */
    @Size(max = 300)
    private String avatarUrl;

    /**
     * Nueva carta favorita (ID del catálogo de Deck-and-Cards). Opcional.
     */
    private Long favoriteCardId;
}