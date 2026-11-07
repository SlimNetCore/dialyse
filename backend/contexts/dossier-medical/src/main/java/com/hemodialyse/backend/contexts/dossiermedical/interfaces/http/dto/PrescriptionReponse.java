package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.application.CatalogueDesArticles.ArticleDeTraitement;
import com.hemodialyse.backend.contexts.dossiermedical.application.PrescriptionDetaillee;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ContenuDePrescription;
import com.hemodialyse.backend.contexts.dossiermedical.domain.UniteDeFrequence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Une prescription médicale, champ par champ comme le legacy (code et libellé des articles inclus, nuls si l'article est inconnu). */
public record PrescriptionReponse(UUID id, UUID patientId, UUID centerId, LocalDate datePrescription, UUID medecinId, Integer qbCible,
                                  Integer qdCible, Integer ufMaxMl, Integer dureeCibleMin, BigDecimal poidsSecCibleKg,
                                  String typeDialyseurPrescrit, String anticoagTypePrescrit, UUID epoArticleId, String epoArticleCode,
                                  String epoArticleLibelle, Integer epoDoseUi, String epoVoie, Integer epoFrequenceValeur,
                                  UniteDeFrequence epoFrequenceUnite, UUID ferArticleId, String ferArticleCode, String ferArticleLibelle,
                                  Integer ferDoseMg, String ferVoie, Integer ferFrequenceValeur, UniteDeFrequence ferFrequenceUnite,
                                  OffsetDateTime createdAt, OffsetDateTime updatedAt) {

    public static PrescriptionReponse de(PrescriptionDetaillee d) {
        var p = d.prescription();
        ContenuDePrescription c = p.contenu();
        return new PrescriptionReponse(p.id(), p.patient().valeur(), p.centre().valeur(), c.date(), c.medecin(), c.qbCible(), c.qdCible(),
                c.ufMaxMl(), c.dureeCibleMin(), c.poidsSecCibleKg(), c.typeDialyseur(), c.anticoagulant(), c.epo().article(),
                d.epo().map(ArticleDeTraitement::code).orElse(null), d.epo().map(ArticleDeTraitement::libelle).orElse(null),
                c.epo().dose(), c.epo().voie(), c.epo().frequenceValeur(), c.epo().frequence(), c.fer().article(),
                d.fer().map(ArticleDeTraitement::code).orElse(null), d.fer().map(ArticleDeTraitement::libelle).orElse(null),
                c.fer().dose(), c.fer().voie(), c.fer().frequenceValeur(), c.fer().frequence(), horodatage(p.creeeLe()),
                horodatage(p.modifieeLe()));
    }

    private static OffsetDateTime horodatage(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
