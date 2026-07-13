package edu.eci.arsw.RoyalArena.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import edu.eci.arsw.RoyalArena.dto.response.ProfileResponseDTO;
import edu.eci.arsw.RoyalArena.model.enums.Profile;



@Mapper(componentModel = "spring")
public interface ProfileMapper {

    /**
     * Convierte Profile a ProfileResponseDTO. La mayoría de campos se mapean
     * automáticamente por nombre. El winRate se calcula con una expresión
     * porque no es un campo de la entidad.
     */
    @Mapping(target = "winRate", expression = "java(calculateWinRate(profile))")
    ProfileResponseDTO toDto(Profile profile);

    /**
     * Calcula el ratio de victorias como porcentaje (0.0 a 100.0).
     * Si no ha jugado partidas, devuelve 0 para evitar división por cero.
     */
    default double calculateWinRate(Profile profile) {
        if (profile.getTotalBattles() == 0) {
            return 0.0;
        }
        double rate = (double) profile.getTotalWins() / profile.getTotalBattles() * 100.0;
        // Redondear a 2 decimales
        return Math.round(rate * 100.0) / 100.0;
    }
}