package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.ForfaitsImposesAuxSeancesPublies;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesDeLaReprisePubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesDuCahierPubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesDesCentresPubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.StatistiquesDesSeancesPubliees;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Le double en mémoire passe le même contrat que l'adapter réel (calé sur H2). */
class PilotageEtRapportsDesSeancesEnMemoireContratTest extends PilotageEtRapportsDesSeancesContrat {

    private final PilotageEtRapportsDesSeancesEnMemoire seances = new PilotageEtRapportsDesSeancesEnMemoire();

    @Override
    protected SeancesRealiseesDesCentresPubliees pilotage() {
        return seances;
    }

    @Override
    protected StatistiquesDesSeancesPubliees statistiques() {
        return seances;
    }

    @Override
    protected SeancesDuCahierPubliees cahier() {
        return seances;
    }

    @Override
    protected ForfaitsImposesAuxSeancesPublies forfaits() {
        return seances;
    }

    @Override
    protected SeancesDeLaReprisePubliees reprise() {
        return seances;
    }

    @Override
    protected void payeur(UUID payeur, UUID caisse, UUID centre, String codeCaisse, String nomCaisse) {
        seances.payeur(payeur, codeCaisse, nomCaisse);
    }

    @Override
    protected void patient(UUID id, UUID centre, UUID payeur) {
        seances.patient(id, centre, payeur);
    }

    @Override
    protected void seance(UUID id, UUID centre, UUID patient, LocalDate date, String statut, Instant creeLe) {
        seances.seance(id, centre, patient, date, statut, creeLe);
    }

    @Override
    protected void volet(UUID centre, UUID seance, BigDecimal poidsAvant, BigDecimal poidsApres, String tensionAvant,
                         BigDecimal ultrafiltration) {
        seances.volet(seance, poidsAvant, poidsApres, tensionAvant, ultrafiltration);
    }

    @Override
    protected void prescription(UUID centre, UUID patient, LocalDate date, BigDecimal poidsSec, Instant majLe) {
        seances.prescription(centre, patient, date, poidsSec, majLe);
    }

    @Override
    protected void forfaitImpose(UUID seance, UUID forfait) {
        seances.forfaitImpose(seance, forfait);
    }

    @Override
    protected void facture(UUID seance, UUID facture) {
        seances.facture(seance, facture);
    }

    @Override
    protected String statutDe(UUID seance) {
        return seances.statutDe(seance);
    }

    @Override
    protected UUID patientDe(UUID seance) {
        return seances.patientDe(seance);
    }
}
