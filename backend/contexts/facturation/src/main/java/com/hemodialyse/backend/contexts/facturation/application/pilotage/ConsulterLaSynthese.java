package com.hemodialyse.backend.contexts.facturation.application.pilotage;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseMensuelle;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;

import java.time.LocalDate;

/**
 * Lecture des données de la synthèse (sans mise en page) : utilisée par la voie « modèle de document » de la plateforme pour
 * poser les totaux enregistrés des factures (même base que le tableau de bord).
 */
public final class ConsulterLaSynthese {

    private final LectureDeLaSynthese lecture;

    public ConsulterLaSynthese(LectureDeLaSynthese lecture) {
        this.lecture = lecture;
    }

    public SyntheseMensuelle executer(CentreId centre, LocalDate debut, LocalDate fin) {
        return lecture.de(centre, debut, fin);
    }
}
