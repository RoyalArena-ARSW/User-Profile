package edu.eci.arsw.RoyalArena.listener;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import edu.eci.arsw.RoyalArena.events.MatchFinishedEvent;
import edu.eci.arsw.RoyalArena.exception.ProfileNotFoundException;
import edu.eci.arsw.RoyalArena.service.ProfileService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Consume los eventos de fin de partida y actualiza los perfiles.
 *
 * Manejo de errores: si un perfil no existe, se loguea y se DESCARTA el
 * mensaje. Reencolarlo sería un poison message: fallaría eternamente
 * bloqueando la cola. Cualquier otra excepción sí se propaga → RabbitMQ
 * reintenta y, si persiste, va a la DLQ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchFinishedListener {

    private final ProfileService profileService;

    @RabbitListener(queues = "${royalarena.events.queue.match-finished}")
    public void onMatchFinished(MatchFinishedEvent event) {
        log.info("Received MatchFinishedEvent for match {} ({} players)",
                event.matchId(), event.players().size());

        for (MatchFinishedEvent.PlayerResult result : event.players()) {
            try {
                profileService.recordBattleResult(
                        result.userId(),
                        result.won(),
                        result.trophyChange(),
                        result.threeCrownWin(),
                        result.experienceGained());

                log.info("Updated profile {}: won={}, trophies={}, xp=+{}",
                        result.userId(), result.won(),
                        result.trophyChange(), result.experienceGained());

            } catch (ProfileNotFoundException e) {
                // Descartar: reintentar no lo va a arreglar
                log.warn("Profile not found for user {} in match {}, skipping",
                        result.userId(), event.matchId());
            }
        }
    }
}