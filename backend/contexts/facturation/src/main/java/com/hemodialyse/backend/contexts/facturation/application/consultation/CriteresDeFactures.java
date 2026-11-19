package com.hemodialyse.backend.contexts.facturation.application.consultation;

import com.hemodialyse.backend.contexts.facturation.domain.reglement.EtatDeReglement;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.math.BigDecimal;
import java.util.Objects;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Critères de la liste des factures (RG-FAC-046). {@code du}/{@code au} : les deux ou aucun (aucun = mois courant au fuseau du centre).
 * Texte vide ou blanc = filtre absent. {@code tri} nul = tri par défaut.
 */
public record CriteresDeFactures(CentreId centre, LocalDate du, LocalDate au, String numero, String patient, UUID caisse,
                                 EtatDeReglement etat, BigDecimal montantMin, BigDecimal montantMax, TriDesFactures tri) {

    public CriteresDeFactures {
        Objects.requireNonNull(centre, "Centre requis");
        numero = numero == null || numero.isBlank() ? null : numero.trim();
        patient = patient == null || patient.isBlank() ? null : patient.trim();
    }

    /** Les seuls centre et période, sans filtre, tri par défaut. */
    public static CriteresDeFactures duCentre(CentreId centre, LocalDate du, LocalDate au) {
        return new CriteresDeFactures(centre, du, au, null, null, null, null, null, null, null);
    }
}
