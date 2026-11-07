package com.hemodialyse.backend.contexts.seance.domain;

/** La séance n'existe pas dans le centre de la session (inconnue ou d'un autre centre). Le legacy répond 400 « Seance introuvable ». */
public class SeanceIntrouvable extends RuntimeException {
    public SeanceIntrouvable() {
        super("Seance introuvable");
    }
}
