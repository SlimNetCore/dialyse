package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PecId;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.ForfaitApplique;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.time.Instant;
import java.time.LocalDate;

public interface SeanceUseCases {
    SeanceId creer(CreerSeanceCommande commande, Instant maintenant);

    void valider(ValiderSeanceCommande commande);

    void signer(SignerSeanceCommande commande);

    void deverrouiller(DeverrouillerPourRegularisationCommande commande);

    void debloquer(DebloquerPourRegularisationCommande commande);

    void declarerForfaitSurcharge(DeclarerForfaitSurchargeCommande commande);

    Seance consulter(SeanceId seanceId, CentreId centreId);

    PagedResult<Seance> lister(CentreId centreId, int page, int size);

    PagedResult<Seance> listerDuJour(CentreId centreId, LocalDate date, int page, int size);

    record CreerSeanceCommande(PatientId patientId, CentreId centreId, PecId pecId,
                               LocalDate dateSeance, String typeSeance) {
    }

    record ValiderSeanceCommande(SeanceId seanceId, CentreId centreId) {
    }

    record SignerSeanceCommande(SeanceId seanceId, CentreId centreId) {
    }

    record DeverrouillerPourRegularisationCommande(SeanceId seanceId, CentreId centreId) {
    }

    record DebloquerPourRegularisationCommande(SeanceId seanceId, CentreId centreId) {
    }

    record DeclarerForfaitSurchargeCommande(SeanceId seanceId, CentreId centreId, ForfaitApplique forfait,
                                            java.math.BigDecimal montantSurcharge, String auteur,
                                            java.time.Instant instant) {
    }
}