package edu.eci.arsw.RoyalArena.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileResponseDTO {

    private Long id;
    private Long userId;
    private String displayName;
    private String avatarUrl;

    // Progreso
    private int level;
    private int experience;
    private int currentTrophies;
    private int highestTrophies;
    private int currentArena;

    // Estadísticas de batalla
    private int totalWins;
    private int totalLosses;
    private int totalBattles;
    private int threeCrownWins;

    // Calculado: ratio de victorias (no está en la entidad, se calcula al vuelo)
    private double winRate;

    // Referencias
    private Long favoriteCardId;
    private Long clanId;

    private LocalDateTime createdAt;
}