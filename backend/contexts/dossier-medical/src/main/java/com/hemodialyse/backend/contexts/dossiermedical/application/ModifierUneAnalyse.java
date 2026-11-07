package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Analyse;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AnalyseIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Analyses;
import com.hemodialyse.backend.contexts.dossiermedical.domain.MesuresDuBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Remplace en entier une analyse du patient (PUT, reproduit à confirmer : toute mesure absente est effacée, la date absente redevient le
 * jour, la date de création est réinitialisée). Écart décidé (KB-MED-74) : une analyse inconnue, d'un autre patient ou d'un autre centre
 * est refusée en 404 {@code ANALYSE_INTROUVABLE} ; elle n'est plus créée ni déplacée vers un autre patient ou un autre centre. Verrou de
 * la ligne : une écriture concurrente attend 5 s puis 409. Droit : MEDECIN.
 */
public class ModifierUneAnalyse {
    private final SessionCourante session;
    private final Analyses analyses;
    private final UniteDeTravail uniteDeTravail;
    private final HorlogeDuCentre horloge;

    public ModifierUneAnalyse(SessionCourante session, Analyses analyses, UniteDeTravail uniteDeTravail, HorlogeDuCentre horloge) {
        this.session = session;
        this.analyses = analyses;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
    }

    public Analyse executer(UUID centreDemande, UUID patientDemande, UUID id, LocalDate datePrelevement, MesuresDuBilan mesures) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        return uniteDeTravail.executer(() -> {
            Analyse analyse = analyses.trouverEnVerrouillant(centre, patient, id).orElseThrow(AnalyseIntrouvable::new);
            analyse.remplacer(datePrelevement, mesures, horloge.aujourdhui(centre), horloge.maintenant());
            analyses.enregistrerLeRemplacement(analyse);
            return analyse;
        });
    }
}
