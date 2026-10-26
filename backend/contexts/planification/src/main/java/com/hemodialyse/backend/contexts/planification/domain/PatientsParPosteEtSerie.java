package com.hemodialyse.backend.contexts.planification.domain;

/**
 * Patients suivis par poste et par série, de 1 à {@value #MAXIMUM} : base de la capacité théorique du centre (RG-PLN-003).
 */
public record PatientsParPosteEtSerie(int nombre) {

    public static final int PAR_DEFAUT = 3;
    public static final int MAXIMUM = 10;

    public PatientsParPosteEtSerie {
        if (nombre < 1 || nombre > MAXIMUM) {
            throw new SaisieInvalide("Le nombre de patients par poste et par série doit être compris entre 1 et " + MAXIMUM);
        }
    }

    public static PatientsParPosteEtSerie parDefaut() {
        return new PatientsParPosteEtSerie(PAR_DEFAUT);
    }
}
