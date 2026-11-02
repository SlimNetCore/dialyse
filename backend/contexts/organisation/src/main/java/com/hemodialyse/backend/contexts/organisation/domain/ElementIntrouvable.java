package com.hemodialyse.backend.contexts.organisation.domain;

/** Une salle, un créneau, un médecin, une machine ou un protocole du centre n'existe pas (404). */
public class ElementIntrouvable extends IllegalArgumentException {
    public ElementIntrouvable(String message) {
        super(message);
    }
}
