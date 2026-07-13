package edu.eci.arsw.RoyalArena.exception;

/**
 * Se lanza cuando se busca un perfil que no existe (por id o por userId).
 */
public class ProfileNotFoundException extends RuntimeException {
    public ProfileNotFoundException(String message) {
        super(message);
    }
}