package edu.eci.arsw.RoyalArena.mappers;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import edu.eci.arsw.RoyalArena.dto.response.ProfileResponseDTO;
import edu.eci.arsw.RoyalArena.model.enums.Profile;

/**
 * El winRate NO es un campo de la entidad: se calcula al vuelo. Por eso vive
 * aquí y no en la BD — sería data redundante que habría que mantener
 * sincronizada en cada partida.
 */
class ProfileMapperTest {

    private static final Offset<Double> EPS = Offset.offset(0.01);

    private final ProfileMapper mapper = new ProfileMapperImpl();

    private Profile withRecord(int wins, int battles) {
        return Profile.builder()
                .id(1L).userId(1L).displayName("Diego")
                .totalWins(wins)
                .totalLosses(battles - wins)
                .totalBattles(battles)
                .build();
    }

    @Test
    @DisplayName("Sin partidas jugadas el winRate es 0, no NaN ni division por cero")
    void zeroBattlesGivesZeroWinRate() {
        ProfileResponseDTO dto = mapper.toDto(withRecord(0, 0));

        assertThat(dto.getWinRate()).isZero();
        assertThat(Double.isNaN(dto.getWinRate())).isFalse();
    }

    @Test
    @DisplayName("El winRate es un porcentaje entre 0 y 100")
    void winRateIsAPercentage() {
        assertThat(mapper.toDto(withRecord(7, 10)).getWinRate()).isCloseTo(70.0, EPS);
        assertThat(mapper.toDto(withRecord(10, 10)).getWinRate()).isCloseTo(100.0, EPS);
        assertThat(mapper.toDto(withRecord(0, 10)).getWinRate()).isZero();
    }

    @Test
    @DisplayName("El winRate se redondea a dos decimales")
    void winRateIsRoundedToTwoDecimals() {
        // 1/3 = 33.3333... → 33.33
        assertThat(mapper.toDto(withRecord(1, 3)).getWinRate()).isEqualTo(33.33);
        // 2/3 = 66.6666... → 66.67
        assertThat(mapper.toDto(withRecord(2, 3)).getWinRate()).isEqualTo(66.67);
    }

    @Test
    @DisplayName("Mapea el resto de campos del perfil")
    void mapsAllFields() {
        Profile profile = Profile.builder()
                .id(3L).userId(2L).displayName("Jugador2")
                .avatarUrl("http://x/a.png")
                .level(5).experience(4200)
                .currentTrophies(1250).highestTrophies(1400).currentArena(8)
                .totalWins(30).totalLosses(20).totalBattles(50).threeCrownWins(4)
                .favoriteCardId(11L).clanId(null)
                .build();

        ProfileResponseDTO dto = mapper.toDto(profile);

        assertThat(dto.getId()).isEqualTo(3L);
        assertThat(dto.getUserId()).isEqualTo(2L);
        assertThat(dto.getCurrentTrophies()).isEqualTo(1250);
        assertThat(dto.getHighestTrophies()).isEqualTo(1400);
        assertThat(dto.getFavoriteCardId()).isEqualTo(11L);
        assertThat(dto.getClanId()).isNull();
        assertThat(dto.getWinRate()).isCloseTo(60.0, EPS);
    }
}