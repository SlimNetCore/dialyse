package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.ConstantesDesSeancesPubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.LiensDExclusionPublies;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesAvecLeurPlacePubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesPubliees;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Le double en mémoire passe le même contrat que l'adapter réel (création nulle classée comme sur H2). */
class SeancesRealiseesEtConstantesEnMemoireContratTest extends SeancesRealiseesEtConstantesContrat {

    private final SeancesRealiseesEtConstantesEnMemoire seances = new SeancesRealiseesEtConstantesEnMemoire();

    @Override
    protected SeancesRealiseesPubliees realisees() {
        return seances;
    }

    @Override
    protected LiensDExclusionPublies liens() {
        return seances;
    }

    @Override
    protected SeancesRealiseesAvecLeurPlacePubliees avecPlace() {
        return seances;
    }

    @Override
    protected ConstantesDesSeancesPubliees constantes() {
        return seances;
    }

    @Override
    protected void patient(UUID id, UUID centre, String nom, String prenom) {
        seances.patient(id, centre, nom, prenom);
    }

    @Override
    protected void ficheDansLeCentre(UUID patient, UUID centre) {
        seances.ficheDansLeCentre(patient, centre);
    }

    @Override
    protected void seance(UUID id, UUID centre, UUID patient, LocalDate date, String statut, Instant creeLe) {
        seances.seance(id, centre, patient, date, statut, creeLe);
    }

    @Override
    protected void place(UUID seance, UUID salle, UUID creneau, UUID generateur) {
        seances.place(seance, salle, creneau, generateur);
    }

    @Override
    protected void lienDExclusion(UUID seance, UUID absence) {
        seances.lienDExclusion(seance, absence);
    }

    @Override
    protected void volet(UUID centre, UUID seance, BigDecimal poidsAvant, BigDecimal poidsApres, String tensionAvant, String tensionApres,
                         Integer debit, BigDecimal ultrafiltration, Integer duree) {
        seances.volet(centre, seance, poidsAvant, poidsApres, tensionAvant, tensionApres, debit, ultrafiltration, duree);
    }

    @Override
    protected boolean creationNulleEnPremier() {
        return false;
    }
}
