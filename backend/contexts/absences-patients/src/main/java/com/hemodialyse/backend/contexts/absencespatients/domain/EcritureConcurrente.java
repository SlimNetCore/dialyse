package com.hemodialyse.backend.contexts.absencespatients.domain;

/**
 * Une autre écriture a pris la même absence (ou le même jour du même patient) avant celle-ci : 409
 * {@code ABSENCE_ECRITURE_CONCURRENTE}. Le client relit puis recommence (décision du 21/10 : plus de « dernière écriture
 * gagnante »).
 */
public class EcritureConcurrente extends RuntimeException {
    public EcritureConcurrente() {
        super("Cette absence vient d'être modifiée par une autre écriture : relire puis recommencer");
    }
}
