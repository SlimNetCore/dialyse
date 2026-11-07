package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedent;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AntecedentIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedents;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CodeClinique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.RegleDeGestionViolee;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutClinique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SystemeDeCodage;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Corrige un antécédent du patient : la correction REMPLACE diagnostic, libellé, dates, sévérité et note (absents = effacés), le type du
 * corps est ignoré, un statut absent est conservé (KB-MED-65, reproduit, à confirmer avec les métiers et le DPO). Antécédent inconnu,
 * d'un autre patient ou d'un autre centre : refus 400 (reproduit, KB-MED-60). Le diagnostic ne doit pas déjà être actif sur un AUTRE
 * antécédent du patient (422, reproduit). Droit : MEDECIN.
 */
public class CorrigerUnAntecedent {
    private final SessionCourante session;
    private final Antecedents antecedents;
    private final UniteDeTravail uniteDeTravail;
    private final HorlogeDuCentre horloge;

    public CorrigerUnAntecedent(SessionCourante session, Antecedents antecedents, UniteDeTravail uniteDeTravail, HorlogeDuCentre horloge) {
        this.session = session;
        this.antecedents = antecedents;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
    }

    public Antecedent executer(UUID centreDemande, UUID patientDemande, UUID id, String systemeSaisi, String codeSaisi, String affichageSaisi,
                               String libelleLibre, LocalDate debut, LocalDate fin, String statutSaisi, String severite, String note) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        CodeClinique diagnostic = codeSaisi == null || codeSaisi.isBlank() ? null
                : CodeClinique.saisir(SystemeDeCodage.lire(systemeSaisi), codeSaisi, affichageSaisi);
        StatutClinique statut = StatutClinique.lireFacultatif(statutSaisi);
        return uniteDeTravail.executer(() -> {
            Antecedent antecedent = antecedents.trouverEnVerrouillant(centre, patient, id).orElseThrow(AntecedentIntrouvable::new);
            if (diagnostic != null && antecedents.unDiagnosticActifExisteEnVerrouillant(centre, patient, diagnostic, id)) {
                throw new RegleDeGestionViolee("BUSINESS_RULE_VIOLATION", "Un antécédent actif avec ce diagnostic existe déjà pour ce patient");
            }
            antecedent.corriger(diagnostic, libelleLibre, debut, fin, statut, severite, note, horloge.maintenant());
            antecedents.enregistrerLaCorrection(antecedent);
            return antecedent;
        });
    }
}
