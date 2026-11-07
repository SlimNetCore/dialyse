package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.domain.AdministrationDeTraitement;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Administrations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Une administration, champ par champ comme le legacy. */
public record AdministrationReponse(UUID id, UUID patientId, UUID centerId, UUID prescriptionMedicaleId, String typeTraitement, String molecule,
                                    BigDecimal dose, String uniteDose, String voie, LocalDate dateAdministration, UUID seanceId,
                                    String administrePar, boolean administree, String motifNonAdministration, UUID articleId,
                                    BigDecimal quantiteArticle, OffsetDateTime createdAt) {

    /** Enveloppe de pagination de la plateforme. */
    public record Page(List<AdministrationReponse> items, long total, int page, int size) {
        public static Page de(Administrations.PageDAdministrations p) {
            return new Page(p.items().stream().map(AdministrationReponse::de).toList(), p.total(), p.page(), p.taille());
        }
    }

    public static AdministrationReponse de(AdministrationDeTraitement a) {
        var dose = a.dose();
        return new AdministrationReponse(a.id(), a.patient().valeur(), a.centre().valeur(), a.prescription(), a.traitement().name(), a.molecule(),
                dose == null ? null : dose.valeur(), dose == null ? null : dose.unite(), a.voie(), a.jour(), a.seance(), a.administrePar(),
                a.administree(), a.motif(), a.article(), a.quantiteArticle(),
                a.creeeLe() == null ? null : OffsetDateTime.ofInstant(a.creeeLe(), ZoneOffset.UTC));
    }
}
