package edu.eci.arsw.RoyalArena.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.eci.arsw.RoyalArena.dto.request.CreateProfileRequestDTO;
import edu.eci.arsw.RoyalArena.dto.request.UpdateProfileRequestDTO;
import edu.eci.arsw.RoyalArena.dto.response.ProfileResponseDTO;
import edu.eci.arsw.RoyalArena.exception.ProfileAlreadyExistsException;
import edu.eci.arsw.RoyalArena.exception.ProfileNotFoundException;
import edu.eci.arsw.RoyalArena.mappers.ProfileMapper;
import edu.eci.arsw.RoyalArena.model.enums.Profile;
import edu.eci.arsw.RoyalArena.repository.ProfileRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final ProfileMapper profileMapper;

    // ============ Creación ============

    /**
     * Crea un perfil para un usuario. Por ahora se llama manualmente;
     * en el futuro Auth Service lo llamará automáticamente al registrar.
     * Un usuario = un perfil (validado por unicidad de userId).
     */
    @Transactional
    public ProfileResponseDTO createProfile(CreateProfileRequestDTO request) {
        log.info("Creating profile for userId: {}", request.getUserId());

        if (profileRepository.existsByUserId(request.getUserId())) {
            throw new ProfileAlreadyExistsException(
                "A profile already exists for userId " + request.getUserId());
        }

        Profile profile = Profile.builder()
                .userId(request.getUserId())
                .displayName(request.getDisplayName())
                // Los demás campos usan sus valores por default (@Builder.Default):
                // level=1, experience=0, trofeos=0, arena=1, stats de batalla=0.
                .build();

        Profile saved = profileRepository.save(profile);
        log.info("Profile created with id {} for userId {}", saved.getId(), saved.getUserId());
        return profileMapper.toDto(saved);
    }

    // ============ Consultas ============

    /**
     * Obtiene el perfil del usuario autenticado (por su userId del header X-User-Id).
     */
    @Transactional(readOnly = true)
    public ProfileResponseDTO getMyProfile(Long userId) {
        Profile profile = findByUserIdOrThrow(userId);
        return profileMapper.toDto(profile);
    }

    /**
     * Obtiene el perfil público de cualquier jugador por su userId.
     * Se usa para ver el perfil de otros jugadores.
     */
    @Transactional(readOnly = true)
    public ProfileResponseDTO getProfileByUserId(Long userId) {
        Profile profile = findByUserIdOrThrow(userId);
        return profileMapper.toDto(profile);
    }

    /**
     * Búsqueda de jugadores por nombre (parcial, case-insensitive).
     */
    @Transactional(readOnly = true)
    public List<ProfileResponseDTO> searchByDisplayName(String displayName) {
        return profileRepository.findByDisplayNameContainingIgnoreCase(displayName).stream()
                .map(profileMapper::toDto)
                .toList();
    }

    /**
     * Leaderboard global: top N jugadores por trofeos.
     * Usamos Pageable para limitar el resultado y no traer millones de filas.
     */
    @Transactional(readOnly = true)
    public List<ProfileResponseDTO> getLeaderboard(int limit) {
        Pageable topN = PageRequest.of(0, limit);
        return profileRepository.findAllByOrderByCurrentTrophiesDesc(topN).stream()
                .map(profileMapper::toDto)
                .toList();
    }

    // ============ Edición por el usuario ============

    /**
     * Actualiza los campos que el usuario puede editar: displayName, avatar,
     * carta favorita. Patrón PATCH parcial: solo cambia los campos que vienen
     * con valor (no null). El usuario NO puede tocar trofeos ni stats.
     */
    @Transactional
    public ProfileResponseDTO updateProfile(Long userId, UpdateProfileRequestDTO request) {
        log.info("Updating profile for userId: {}", userId);
        Profile profile = findByUserIdOrThrow(userId);

        if (request.getDisplayName() != null) {
            profile.setDisplayName(request.getDisplayName());
        }
        if (request.getAvatarUrl() != null) {
            profile.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getFavoriteCardId() != null) {
            profile.setFavoriteCardId(request.getFavoriteCardId());
        }

        Profile saved = profileRepository.save(profile);
        log.info("Profile updated for userId {}", userId);
        return profileMapper.toDto(saved);
    }

    // ============ Actualización de stats por el SISTEMA (Game Engine) ============

    /**
     * Registra el resultado de una partida. Este método NO lo llama el usuario:
     * lo llamará el Game Engine cuando termine una partida (vía REST interno
     * o evento RabbitMQ). Por eso actualiza trofeos y stats que el usuario
     * no puede tocar directamente.
     *
     * @param userId         jugador afectado
     * @param won            true si ganó, false si perdió
     * @param trophyChange   trofeos ganados (+) o perdidos (-)
     * @param threeCrown     true si fue victoria de tres coronas
     * @param experienceGain experiencia ganada en la partida
     */
    @Transactional
    public ProfileResponseDTO recordBattleResult(Long userId, boolean won, int trophyChange,
                                                  boolean threeCrown, int experienceGain) {
        log.info("Recording battle result for userId {}: won={}, trophyChange={}",
                userId, won, trophyChange);
        Profile profile = findByUserIdOrThrow(userId);

        // Actualizar contadores de batalla
        profile.setTotalBattles(profile.getTotalBattles() + 1);
        if (won) {
            profile.setTotalWins(profile.getTotalWins() + 1);
            if (threeCrown) {
                profile.setThreeCrownWins(profile.getThreeCrownWins() + 1);
            }
        } else {
            profile.setTotalLosses(profile.getTotalLosses() + 1);
        }

        // Actualizar trofeos (no bajar de 0)
        int newTrophies = Math.max(0, profile.getCurrentTrophies() + trophyChange);
        profile.setCurrentTrophies(newTrophies);

        // Actualizar récord histórico si superó su máximo
        if (newTrophies > profile.getHighestTrophies()) {
            profile.setHighestTrophies(newTrophies);
        }

        // Actualizar experiencia y nivel
        profile.setExperience(profile.getExperience() + experienceGain);
        // Lógica simple de subida de nivel: cada 1000 XP = 1 nivel.
        // (Ajustable según cómo quieras el balance del juego.)
        int newLevel = 1 + (profile.getExperience() / 1000);
        profile.setLevel(newLevel);

        // Actualizar arena según trofeos (umbrales simplificados)
        profile.setCurrentArena(calculateArena(newTrophies));

        Profile saved = profileRepository.save(profile);
        log.info("Battle result recorded for userId {}: trophies={}, level={}",
                userId, newTrophies, newLevel);
        return profileMapper.toDto(saved);
    }

    // ============ Helpers ============

    private Profile findByUserIdOrThrow(Long userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ProfileNotFoundException(
                    "Profile not found for userId " + userId));
    }

    /**
     * Determina la arena según trofeos. Umbrales simplificados basados en
     * Clash Royale. Ajustables según el balance que quieras.
     */
    private int calculateArena(int trophies) {
        if (trophies >= 3000) return 15;
        if (trophies >= 2600) return 14;
        if (trophies >= 2300) return 13;
        if (trophies >= 2000) return 12;
        if (trophies >= 1800) return 11;
        if (trophies >= 1600) return 10;
        if (trophies >= 1400) return 9;
        if (trophies >= 1200) return 8;
        if (trophies >= 1000) return 7;
        if (trophies >= 800) return 6;
        if (trophies >= 600) return 5;
        if (trophies >= 400) return 4;
        if (trophies >= 300) return 3;
        if (trophies >= 150) return 2;
        return 1;
    }
}