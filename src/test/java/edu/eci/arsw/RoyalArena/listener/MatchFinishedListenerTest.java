package edu.eci.arsw.RoyalArena.listener;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import edu.eci.arsw.RoyalArena.events.MatchFinishedEvent;
import edu.eci.arsw.RoyalArena.exception.ProfileNotFoundException;
import edu.eci.arsw.RoyalArena.service.ProfileService;

/**
 * El consumidor de RabbitMQ.
 *
 * Lo importante aquí es el MANEJO DE ERRORES, que decide si un mensaje se
 * descarta, se reintenta o bloquea la cola para siempre.
 */
class MatchFinishedListenerTest {

    private ProfileService profileService;
    private MatchFinishedListener listener;

    @BeforeEach
    void setUp() {
        profileService = mock(ProfileService.class);
        listener = new MatchFinishedListener(profileService);
    }

    private MatchFinishedEvent event(MatchFinishedEvent.PlayerResult... players) {
        return new MatchFinishedEvent(
                "match-abc", "TEAM_A", 180.0, System.currentTimeMillis(), List.of(players));
    }

    private MatchFinishedEvent.PlayerResult winner(long userId) {
        return new MatchFinishedEvent.PlayerResult(
                userId, "TEAM_A", true, false, 1, 0, false, 30, 30);
    }

    private MatchFinishedEvent.PlayerResult loser(long userId) {
        return new MatchFinishedEvent.PlayerResult(
                userId, "TEAM_B", false, false, 0, 1, false, -30, 10);
    }

    @Test
    @DisplayName("Aplica el resultado a los dos jugadores del evento")
    void appliesResultToAllPlayers() {
        listener.onMatchFinished(event(winner(1L), loser(2L)));

        verify(profileService).recordBattleResult(1L, true, false,  30, false, 30);
        verify(profileService).recordBattleResult(2L, false, false, -30, false, 10);
    }

    @Test
    @DisplayName("Pasa el flag de tres coronas tal como viene en el evento")
    void forwardsThreeCrownFlag() {
        MatchFinishedEvent.PlayerResult perfect = new MatchFinishedEvent.PlayerResult(
                1L, "TEAM_A", true, false, 3, 0, true, 30, 30);

        listener.onMatchFinished(event(perfect));

        verify(profileService).recordBattleResult(1L, true, false,  30, true, 30);
    }

    /**
     * Si un perfil no existe, reintentar NO lo va a arreglar: el mensaje
     * fallaría eternamente y bloquearía la cola (poison message). Por eso se
     * descarta con un warning.
     */
    @Test
    @DisplayName("Un perfil inexistente se descarta sin tumbar el consumo")
    void missingProfileIsSkippedNotRetried() {
        doThrow(new ProfileNotFoundException("no existe"))
                .when(profileService)
                .recordBattleResult(anyLong(), anyBoolean(), anyBoolean(), anyInt(), anyBoolean(), anyInt());

        assertThatCode(() -> listener.onMatchFinished(event(winner(99L))))
                .as("no debe propagar: RabbitMQ reintentaria para siempre")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Si un jugador no tiene perfil, el OTRO igual se actualiza")
    void oneMissingProfileDoesNotBlockTheOther() {
        doThrow(new ProfileNotFoundException("no existe"))
                .when(profileService).recordBattleResult(99L, true, false,  30, false, 30);

        listener.onMatchFinished(event(winner(99L), loser(2L)));

        verify(profileService).recordBattleResult(2L, false, false, -30, false, 10);
    }

    /**
     * En cambio, un fallo de BD SÍ debe propagar: RabbitMQ reintenta y, si
     * persiste, el mensaje termina en la DLQ en vez de perderse en silencio.
     */
    @Test
    @DisplayName("Un error inesperado SI propaga, para que RabbitMQ reintente")
    void unexpectedErrorPropagatesForRetry() {
        doThrow(new RuntimeException("BD caida"))
                .when(profileService)
                .recordBattleResult(anyLong(), anyBoolean(), anyBoolean(), anyInt(), anyBoolean(), anyInt());

        assertThatThrownBy(() -> listener.onMatchFinished(event(winner(1L))))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("BD caida");
    }
}