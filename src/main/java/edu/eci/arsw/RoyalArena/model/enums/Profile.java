package edu.eci.arsw.royalarena.profile.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "profiles")
public class Profile {

    /**
     * PK propia de Profile Service, autogenerada por su base de datos.
     * NO es el mismo id que el usuario tiene en Auth Service.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Referencia al id del usuario en Auth Service. NUNCA se autogenera aquí:
     * se recibe del header X-User-Id (que el Gateway extrae del JWT emitido por Auth).
     * unique = true garantiza que un usuario tenga exactamente un perfil.
     */
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /**
     * Nombre visible en batalla. Inicialmente igual al username de Auth,
     * pero el usuario puede cambiarlo sin afectar su username de login.
     */
    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    /**
     * URL del avatar/icono de perfil. Nullable: un jugador nuevo usa un avatar
     * por default gestionado por el frontend hasta que suba el suyo.
     */
    @Column(name = "avatar_url", nullable = true, length = 300)
    private String avatarUrl;

    // ============ Progreso del jugador ============

    /**
     * Nivel del jugador (King Level en CR). Empieza en 1.
     */
    @Column(nullable = false)
    @Builder.Default
    private int level = 1;

    /**
     * Experiencia acumulada hacia el siguiente nivel.
     */
    @Column(nullable = false)
    @Builder.Default
    private int experience = 0;

    /**
     * Trofeos actuales. Suben al ganar, bajan al perder.
     */
    @Column(name = "current_trophies", nullable = false)
    @Builder.Default
    private int currentTrophies = 0;

    /**
     * Récord histórico de trofeos (el "best" que ha alcanzado el jugador).
     * Nunca baja aunque los trofeos actuales bajen.
     */
    @Column(name = "highest_trophies", nullable = false)
    @Builder.Default
    private int highestTrophies = 0;

    /**
     * Arena actual según los trofeos. Empieza en 1.
     * Se maneja como int por flexibilidad (las arenas cambian con updates del juego).
     */
    @Column(name = "current_arena", nullable = false)
    @Builder.Default
    private int currentArena = 1;

    // ============ Estadísticas de batalla ============

    /**
     * Total de partidas ganadas.
     */
    @Column(name = "total_wins", nullable = false)
    @Builder.Default
    private int totalWins = 0;

    /**
     * Total de partidas perdidas.
     */
    @Column(name = "total_losses", nullable = false)
    @Builder.Default
    private int totalLosses = 0;

    /**
     * Total de partidas jugadas. Redundante con wins+losses pero útil para
     * evitar recalcular, y para incluir empates si el juego los tuviera.
     */
    @Column(name = "total_battles", nullable = false)
    @Builder.Default
    private int totalBattles = 0;

    /**
     * Victorias con tres coronas (destruir las 3 torres enemigas).
     * Es una estadística de prestigio en Clash Royale.
     */
    @Column(name = "three_crown_wins", nullable = false)
    @Builder.Default
    private int threeCrownWins = 0;

    // ============ Referencias a otros servicios ============

    /**
     * ID de la carta favorita del jugador. Referencia al catálogo de Deck-and-Cards.
     * Nullable: un jugador nuevo no tiene carta favorita hasta que la elige.
     * Solo guardamos el ID; los detalles de la carta se piden a Deck-and-Cards.
     */
    @Column(name = "favorite_card_id", nullable = true)
    private Long favoriteCardId;

    /**
     * ID del clan al que pertenece el jugador. Referencia a un futuro Clan Service.
     * Nullable y en null por defecto: los clanes aún no están implementados.
     */
    @Column(name = "clan_id", nullable = true)
    private Long clanId;

    // ============ Metadata ============

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}