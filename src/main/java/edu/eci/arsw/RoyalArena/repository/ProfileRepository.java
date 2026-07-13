package edu.eci.arsw.RoyalArena.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import edu.eci.arsw.RoyalArena.model.enums.Profile;



@Repository
public interface ProfileRepository extends JpaRepository<Profile, Long> {

    /**
     * Busca el perfil por el userId (referencia a Auth). Este es el método
     * más usado: cuando llega una petición con X-User-Id, buscamos el perfil
     * de ese usuario.
     */
    Optional<Profile> findByUserId(Long userId);

    /**
     * Verifica si ya existe un perfil para ese userId. Se usa al crear un perfil
     * para evitar duplicados (un usuario = un perfil).
     */
    boolean existsByUserId(Long userId);

    /**
     * Busca perfiles por displayName (búsqueda de jugadores por nombre).
     * Útil para funciones sociales o de búsqueda de amigos.
     */
    List<Profile> findByDisplayNameContainingIgnoreCase(String displayName);

    /**
     * Devuelve los perfiles con más trofeos, ordenados de mayor a menor.
     * Base para el leaderboard/ranking global.
     * El parámetro Pageable permite limitar (ej. top 100).
     */
    List<Profile> findAllByOrderByCurrentTrophiesDesc();

    /**
     * Perfiles de una arena específica, ordenados por trofeos.
     * Útil para rankings por arena.
     */
    List<Profile> findByCurrentArenaOrderByCurrentTrophiesDesc(int currentArena);
}