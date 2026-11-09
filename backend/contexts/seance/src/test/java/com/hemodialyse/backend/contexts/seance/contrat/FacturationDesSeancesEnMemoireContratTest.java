package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.ExclusionsEtForfaitsDesSeancesPublies;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesAFacturerPubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesDesFacturesPubliees;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Le double en mémoire passe le même contrat que l'adapter réel. */
class FacturationDesSeancesEnMemoireContratTest extends FacturationDesSeancesContrat {

    private final FacturationDesSeancesEnMemoire seances = new FacturationDesSeancesEnMemoire();

    @Override
    protected SeancesAFacturerPubliees aFacturer() {
        return seances;
    }

    @Override
    protected ExclusionsEtForfaitsDesSeancesPublies exclusions() {
        return seances;
    }

    @Override
    protected SeancesDesFacturesPubliees facturees() {
        return seances;
    }

    @Override
    protected com.hemodialyse.backend.contexts.seance.publishedlanguage.LiaisonDesSeancesAUneFacturePubliee liaison() {
        return seances;
    }

    @Override
    protected UUID factureDe(UUID seance) {
        return seances.factureDe(seance);
    }

    @Override
    protected void facture(UUID id, UUID centre) {
        seances.facture(id, centre);
    }

    @Override
    protected void patient(UUID id, UUID centre, String code, String nom, String prenom, String etat) {
        seances.patient(id, centre, code, nom, prenom, etat);
    }

    @Override
    protected void payeur(UUID patient, UUID centre, UUID centrePayeur, UUID agence) {
        seances.payeur(patient, centrePayeur, agence);
    }

    @Override
    protected void seance(UUID id, UUID centre, UUID patient, LocalDate date, String statut, UUID facture) {
        seances.seance(id, centre, patient, date, statut, facture);
    }

    @Override
    protected void surcharge(UUID seance, UUID forfait, String nom, BigDecimal prix, String code) {
        seances.surcharge(seance, forfait, nom, prix, code);
    }

    @Override
    protected void memoire(UUID seance, String statutAvant) {
        seances.memoire(seance, statutAvant);
    }

    @Override
    protected String statutDe(UUID seance) {
        return seances.statutDe(seance);
    }

    @Override
    protected String codeDuForfaitDe(UUID seance) {
        return seances.codeDuForfaitDe(seance);
    }

    @Override
    protected String memoireDe(UUID seance) {
        return seances.memoireDe(seance);
    }

    @Override
    protected UUID absenceDe(UUID seance) {
        return seances.absenceDe(seance);
    }
}
