package edu.eci.arsw.RoyalArena.exception;

/**
 * Se lanza cuando se intenta crear un perfil para un userId que ya tiene uno.
 * Un usuario = un perfil.
 */
public class ProfileAlreadyExistsException extends RuntimeException {
    public ProfileAlreadyExistsException(String message) {
        super(message);
    }
}