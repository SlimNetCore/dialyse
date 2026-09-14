package com.hemodialyse.backend.domain.medical.greffe.entity;

import com.hemodialyse.backend.domain.medical.greffe.valueobject.AvisRcp;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Entité (AGENTS.md §14) — une décision de la Réunion de Concertation Pluridisciplinaire (RCP)
 * sur le dossier de greffe. N'a de sens qu'à l'intérieur de l'agrégat {@code BilanPreGreffe}
 * (pas de cycle de vie propre).
 */
public final class DecisionRcp {

    private final UUID id;
    private final LocalDate dateReunion;
    private final AvisRcp avis;
    private final String compteRendu;
    private final LocalDate prochaineDateRevue;

    private DecisionRcp(UUID id, LocalDate dateReunion, AvisRcp avis, String compteRendu,
                        LocalDate prochaineDateRevue) {
        this.id = id;
        this.dateReunion = dateReunion;
        this.avis = avis;
        this.compteRendu = compteRendu;
        this.prochaineDateRevue = prochaineDateRevue;
    }

    public static DecisionRcp creer(LocalDate dateReunion, AvisRcp avis, String compteRendu,
                                    LocalDate prochaineDateRevue) {
        if (dateReunion == null || avis == null) {
            throw new BusinessException("DECISION_RCP_CHAMPS_REQUIS",
                    "La date de réunion et l'avis sont obligatoires pour une décision de RCP");
        }
        if (dateReunion.isAfter(LocalDate.now())) {
            throw new BusinessException("DECISION_RCP_DATE_FUTURE", "La date de réunion ne peut pas être dans le futur");
        }
        return new DecisionRcp(UUID.randomUUID(), dateReunion, avis, compteRendu, prochaineDateRevue);
    }

    public static DecisionRcp reconstituer(UUID id, LocalDate dateReunion, AvisRcp avis, String compteRendu,
                                           LocalDate prochaineDateRevue) {
        return new DecisionRcp(id, dateReunion, avis, compteRendu, prochaineDateRevue);
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getDateReunion() {
        return dateReunion;
    }

    public AvisRcp getAvis() {
        return avis;
    }

    public String getCompteRendu() {
        return compteRendu;
    }

    public LocalDate getProchaineDateRevue() {
        return prochaineDateRevue;
    }
}
