package edu.eci.arsw.RoyalArena.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.arsw.RoyalArena.dto.request.CreateProfileRequestDTO;
import edu.eci.arsw.RoyalArena.dto.request.UpdateProfileRequestDTO;
import edu.eci.arsw.RoyalArena.dto.response.ProfileResponseDTO;
import edu.eci.arsw.RoyalArena.service.ProfileService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    /**
     * Crea un perfil. Por ahora se usa manualmente; en el futuro lo llamará
     * Auth Service internamente al registrar un usuario.
     */
    @PostMapping
    public ResponseEntity<ProfileResponseDTO> createProfile(
            @Valid @RequestBody CreateProfileRequestDTO request) {
        log.info("POST /api/profiles - userId: {}", request.getUserId());
        ProfileResponseDTO response = profileService.createProfile(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Devuelve el perfil del usuario autenticado (X-User-Id del Gateway).
     */
    @GetMapping("/me")
    public ResponseEntity<ProfileResponseDTO> getMyProfile(
            @RequestHeader("X-User-Id") Long userId) {
        log.info("GET /api/profiles/me - userId: {}", userId);
        return ResponseEntity.ok(profileService.getMyProfile(userId));
    }

    /**
     * Devuelve el perfil público de cualquier jugador por su userId.
     */
    @GetMapping("/{userId}")
    public ResponseEntity<ProfileResponseDTO> getProfileByUserId(@PathVariable Long userId) {
        log.info("GET /api/profiles/{}", userId);
        return ResponseEntity.ok(profileService.getProfileByUserId(userId));
    }

    /**
     * Edita el perfil del usuario autenticado. Solo displayName, avatar y carta favorita.
     */
    @PutMapping("/me")
    public ResponseEntity<ProfileResponseDTO> updateMyProfile(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody UpdateProfileRequestDTO request) {
        log.info("PUT /api/profiles/me - userId: {}", userId);
        return ResponseEntity.ok(profileService.updateProfile(userId, request));
    }

    /**
     * Búsqueda de jugadores por nombre (parcial).
     * Ej: /api/profiles/search?name=diego
     */
    @GetMapping("/search")
    public ResponseEntity<List<ProfileResponseDTO>> searchByName(
            @RequestParam String name) {
        log.info("GET /api/profiles/search?name={}", name);
        return ResponseEntity.ok(profileService.searchByDisplayName(name));
    }

    /**
     * Leaderboard global: top N jugadores por trofeos.
     * Ej: /api/profiles/leaderboard?limit=100
     */
    @GetMapping("/leaderboard")
    public ResponseEntity<List<ProfileResponseDTO>> getLeaderboard(
            @RequestParam(defaultValue = "100") int limit) {
        log.info("GET /api/profiles/leaderboard?limit={}", limit);
        return ResponseEntity.ok(profileService.getLeaderboard(limit));
    }
}