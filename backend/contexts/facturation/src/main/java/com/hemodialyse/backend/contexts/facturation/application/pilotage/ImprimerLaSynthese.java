package com.hemodialyse.backend.contexts.facturation.application.pilotage;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FormatDImpression;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.ImpressionDeLaSynthese;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;

import java.time.LocalDate;

/**
 * Impression de la synthèse mensuelle de facturation d'un centre : valide la période et le format, lit les données et confie
 * la mise en page au port {@link ImpressionDeLaSynthese}. Aucun plafond de durée (KB-FAC-58 reproduit).
 */
public final class ImprimerLaSynthese {

    private final LectureDeLaSynthese lecture;
    private final ImpressionDeLaSynthese impression;

    public ImprimerLaSynthese(LectureDeLaSynthese lecture, ImpressionDeLaSynthese impression) {
        this.lecture = lecture;
        this.impression = impression;
    }

    /**
     * @throws IllegalArgumentException période incomplète ou inversée, format inconnu
     */
    public byte[] executer(CentreId centre, LocalDate debut, LocalDate fin, String format) {
        if (debut == null || fin == null) {
            throw new IllegalArgumentException("periodStart et periodEnd sont obligatoires");
        }
        if (debut.isAfter(fin)) {
            throw new IllegalArgumentException("periodStart doit etre inferieure ou egale a periodEnd");
        }
        FormatDImpression formatRetenu = FormatDImpression.depuis(format);
        return impression.imprimer(lecture.de(centre, debut, fin), formatRetenu);
    }
}
