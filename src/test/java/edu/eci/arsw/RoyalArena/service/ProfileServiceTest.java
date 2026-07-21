package edu.eci.arsw.RoyalArena.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import edu.eci.arsw.RoyalArena.dto.request.CreateProfileRequestDTO;
import edu.eci.arsw.RoyalArena.dto.request.UpdateProfileRequestDTO;
import edu.eci.arsw.RoyalArena.exception.ProfileAlreadyExistsException;
import edu.eci.arsw.RoyalArena.exception.ProfileNotFoundException;
import edu.eci.arsw.RoyalArena.mappers.ProfileMapperImpl;
import edu.eci.arsw.RoyalArena.model.enums.Profile;
import edu.eci.arsw.RoyalArena.repository.ProfileRepository;

/**
 * Tests del progreso del jugador.
 *
 * recordBattleResult es el método más delicado del servicio: NO se expone por
 * REST (ningún usuario debe poder darse trofeos) y solo llega por el evento de
 * RabbitMQ. Si su lógica se rompe, el ranking entero pierde sentido.
 */
class ProfileServiceTest {

    private ProfileRepository profileRepository;
    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        profileRepository = mock(ProfileRepository.class);
        profileService = new ProfileService(profileRepository, new ProfileMapperImpl());

        // save devuelve el mismo objeto que recibe (como haría JPA)
        when(profileRepository.save(any(Profile.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    /** Perfil recién creado: todo en cero. */
    private Profile freshProfile() {
        return Profile.builder()
                .id(1L).userId(1L).displayName("Diego")
                .build(); // los @Builder.Default ponen level=1, arena=1, resto 0
    }

    private Profile profileWith(int trophies, int highest) {
        Profile p = freshProfile();
        p.setCurrentTrophies(trophies);
        p.setHighestTrophies(highest);
        return p;
    }

    private void existing(Profile profile) {
        when(profileRepository.findByUserId(profile.getUserId()))
                .thenReturn(Optional.of(profile));
    }

    // ===== Creación =====

    @Test
    @DisplayName("Un perfil nuevo nace en nivel 1, arena 1 y sin trofeos")
    void newProfileStartsAtZero() {
        when(profileRepository.existsByUserId(1L)).thenReturn(false);

        profileService.createProfile(
                CreateProfileRequestDTO.builder().userId(1L).displayName("Diego").build());

        ArgumentCaptor<Profile> captor = ArgumentCaptor.forClass(Profile.class);
        verify(profileRepository).save(captor.capture());
        Profile saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getLevel()).isEqualTo(1);
        assertThat(saved.getCurrentArena()).isEqualTo(1);
        assertThat(saved.getCurrentTrophies()).isZero();
        assertThat(saved.getTotalBattles()).isZero();
        assertThat(saved.getFavoriteCardId()).isNull();
        assertThat(saved.getClanId()).isNull();
    }

    @Test
    @DisplayName("Un usuario no puede tener dos perfiles")
    void cannotCreateDuplicateProfile() {
        when(profileRepository.existsByUserId(1L)).thenReturn(true);

        assertThatThrownBy(() -> profileService.createProfile(
                CreateProfileRequestDTO.builder().userId(1L).displayName("X").build()))
                .isInstanceOf(ProfileAlreadyExistsException.class)
                .hasMessageContaining("1");

        verify(profileRepository, never()).save(any());
    }

    @Test
    @DisplayName("Consultar un perfil inexistente lanza 404")
    void missingProfileThrows() {
        when(profileRepository.findByUserId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getMyProfile(99L))
                .isInstanceOf(ProfileNotFoundException.class);
    }

    // ===== Edición por el usuario =====

    @Test
    @DisplayName("La edicion es parcial: los campos null no tocan nada")
    void updateIsPartial() {
        Profile profile = freshProfile();
        profile.setAvatarUrl("http://viejo.png");
        profile.setFavoriteCardId(5L);
        existing(profile);

        // Solo cambia el displayName
        profileService.updateProfile(1L, UpdateProfileRequestDTO.builder()
                .displayName("DiegoTheKing")
                .build());

        assertThat(profile.getDisplayName()).isEqualTo("DiegoTheKing");
        assertThat(profile.getAvatarUrl()).isEqualTo("http://viejo.png");
        assertThat(profile.getFavoriteCardId()).isEqualTo(5L);
    }

    /**
     * El UpdateProfileRequestDTO NO tiene campos de trofeos ni stats — a
     * propósito. Si alguien los agregara "por comodidad", cualquier jugador
     * podría ponerse 10000 trofeos con un PUT y el ranking se arruinaría.
     */
    @Test
    @DisplayName("El usuario NO puede editar sus trofeos ni sus stats")
    void userCannotEditTrophiesOrStats() {
        String[] fields = java.util.Arrays.stream(
                        UpdateProfileRequestDTO.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .toArray(String[]::new);

        assertThat(fields)
                .as("editar trofeos por API seria un agujero de seguridad")
                .doesNotContain("currentTrophies", "highestTrophies", "level",
                        "experience", "currentArena", "totalWins", "totalLosses",
                        "totalBattles", "threeCrownWins", "userId");
    }

    // ===== recordBattleResult: trofeos =====

    @Test
    @DisplayName("Ganar suma trofeos y victorias")
    void winningAddsTrophiesAndWins() {
        Profile profile = profileWith(500, 500);
        existing(profile);

        profileService.recordBattleResult(1L, true, false,  30, false, 30);

        assertThat(profile.getCurrentTrophies()).isEqualTo(530);
        assertThat(profile.getTotalWins()).isEqualTo(1);
        assertThat(profile.getTotalLosses()).isZero();
        assertThat(profile.getTotalBattles()).isEqualTo(1);
    }

    @Test
    @DisplayName("Perder resta trofeos y suma derrotas")
    void losingSubtractsTrophies() {
        Profile profile = profileWith(500, 500);
        existing(profile);

        profileService.recordBattleResult(1L, false, false, -30, false, 10);

        assertThat(profile.getCurrentTrophies()).isEqualTo(470);
        assertThat(profile.getTotalWins()).isZero();
        assertThat(profile.getTotalLosses()).isEqualTo(1);
        assertThat(profile.getTotalBattles()).isEqualTo(1);
    }

    /**
     * Es justo lo que viste en tu perfil de prueba: jugador 2 perdió con 0
     * trofeos y quedó en 0, no en -30.
     */
    @Test
    @DisplayName("Los trofeos nunca bajan de cero")
    void trophiesNeverGoNegative() {
        Profile profile = profileWith(10, 100);
        existing(profile);

        profileService.recordBattleResult(1L, false, false, -30, false, 10);

        assertThat(profile.getCurrentTrophies()).isZero();
    }

    @Test
    @DisplayName("El record historico sube al superar el maximo")
    void highestTrophiesTracksNewPeak() {
        Profile profile = profileWith(990, 990);
        existing(profile);

        profileService.recordBattleResult(1L, true, false,  30, false, 30);

        assertThat(profile.getCurrentTrophies()).isEqualTo(1020);
        assertThat(profile.getHighestTrophies()).isEqualTo(1020);
    }

    @Test
    @DisplayName("El record historico NUNCA baja, aunque los trofeos actuales caigan")
    void highestTrophiesNeverDecreases() {
        Profile profile = profileWith(1000, 1500); // ya tuvo un pico de 1500
        existing(profile);

        profileService.recordBattleResult(1L, false, false,  -30, false, 10);

        assertThat(profile.getCurrentTrophies()).isEqualTo(970);
        assertThat(profile.getHighestTrophies())
                .as("el 'best' es historico: no se pierde")
                .isEqualTo(1500);
    }

    // ===== recordBattleResult: coronas, nivel, arena =====

    @Test
    @DisplayName("Las tres coronas solo cuentan si ademas ganaste")
    void threeCrownOnlyCountsOnWin() {
        Profile profile = freshProfile();
        existing(profile);

        profileService.recordBattleResult(1L, true, false,  30, true, 30);
        assertThat(profile.getThreeCrownWins()).isEqualTo(1);

        // Combinación imposible, pero si llegara: perder no da corona
        profileService.recordBattleResult(1L, false, false, -30, true, 10);
        assertThat(profile.getThreeCrownWins()).isEqualTo(1);
    }

    @Test
    @DisplayName("El nivel sube cada 1000 de experiencia")
    void levelIsDerivedFromExperience() {
        Profile profile = freshProfile();
        existing(profile);

        profileService.recordBattleResult(1L, true, false,  30, false, 500);
        assertThat(profile.getLevel()).as("500 XP: sigue nivel 1").isEqualTo(1);

        profileService.recordBattleResult(1L, true, false, 30, false, 500);
        assertThat(profile.getExperience()).isEqualTo(1000);
        assertThat(profile.getLevel()).as("1000 XP: nivel 2").isEqualTo(2);

        profileService.recordBattleResult(1L, true, false, 30, false, 2000);
        assertThat(profile.getLevel()).as("3000 XP: nivel 4").isEqualTo(4);
    }

    @Test
    @DisplayName("La arena se recalcula segun los trofeos")
    void arenaFollowsTrophies() {
        Profile profile = profileWith(140, 140);
        existing(profile);

        // 140 → +30 = 170, cruza el umbral de arena 2 (150)
        profileService.recordBattleResult(1L, true, false, 30, false, 0);
        assertThat(profile.getCurrentArena()).isEqualTo(2);
    }

    @Test
    @DisplayName("La arena baja si los trofeos bajan")
    void arenaDropsWithTrophies() {
        Profile profile = profileWith(160, 500);
        existing(profile);

        // 160 → -30 = 130, cae por debajo de 150 → vuelve a arena 1
        profileService.recordBattleResult(1L, false, false, -30, false, 10);
        assertThat(profile.getCurrentArena()).isEqualTo(1);
    }

    @Test
    @DisplayName("Registrar el resultado de un perfil inexistente lanza excepcion")
    void recordBattleResultThrowsForMissingProfile() {
        when(profileRepository.findByUserId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.recordBattleResult(99L, true, false, 30, false, 30))
                .isInstanceOf(ProfileNotFoundException.class);
    }

    // ===== ⚠️ El empate =====


    // ===== Leaderboard =====

    @Test
    @DisplayName("El leaderboard pide solo el top N a la BD, no todos los perfiles")
    void leaderboardLimitsAtTheQuery() {
        when(profileRepository.findAllByOrderByCurrentTrophiesDesc(any(Pageable.class)))
                .thenReturn(List.of(freshProfile()));

        profileService.getLeaderboard(10);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(profileRepository).findAllByOrderByCurrentTrophiesDesc(captor.capture());

        // El límite viaja a la query: con millones de jugadores, traerlos todos
        // y cortar en memoria tumbaría el servicio.
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
        assertThat(captor.getValue().getPageNumber()).isZero();
    }

    @Test
    @DisplayName("La busqueda por nombre delega en el repositorio")
    void searchDelegatesToRepository() {
        when(profileRepository.findByDisplayNameContainingIgnoreCase("die"))
                .thenReturn(List.of(freshProfile()));

        assertThat(profileService.searchByDisplayName("die")).hasSize(1);
        verify(profileRepository).findByDisplayNameContainingIgnoreCase("die");
    }
}