package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.CahierRefuse;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.PeriodeDuCahier;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Imprime le cahier de dialyse d'un patient du centre de la session : une page par séance validée, signée ou facturée, éventuellement
 * limitée à une période. Gardes, dans l'ordre : le centre de la session (403) ; la période (422 {@code CAHIER_PERIODE_INVALIDE}) ; le patient
 * du centre (404 {@code PATIENT_INTROUVABLE}, KB-PL5-6e : c'était 422) ; au moins une séance imprimable (422 {@code CAHIER_VIDE}). Les lignes du
 * cahier sont composées par la Séance (DOC5a, ADR 0193) et le rendu est confié à Documents ; le volet médical n'est imprimé qu'aux profils habilités (KB-PL5-4). L'impression est journalisée par la
 * plateforme sous l'action de lecture {@code CAHIER_DIALYSE_IMPRESSION} (KB-PL5-3).
 */
public class ImprimerLeCahierDeDialyse {

    private static final DateTimeFormatter EDITION = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRANCE);

    private final SessionCourante session;
    private final PatientsDeLaSeance patients;
    private final SeancesDuCahier seances;
    private final RenduDuCahier rendu;
    private final ComposerLesLignesDuCahier lignes;
    private final Clock horloge;

    public ImprimerLeCahierDeDialyse(SessionCourante session, PatientsDeLaSeance patients, SeancesDuCahier seances, RenduDuCahier rendu,
                                     ComposerLesLignesDuCahier lignes, Clock horloge) {
        this.session = session;
        this.patients = patients;
        this.seances = seances;
        this.rendu = rendu;
        this.lignes = lignes;
        this.horloge = horloge;
    }

    /** Fuseau IANA du navigateur pour la date d'édition ; UTC s'il est absent ou invalide. */
    static ZoneId fuseau(String tz) {
        if (tz == null || tz.isBlank()) return ZoneOffset.UTC;
        try {
            return ZoneId.of(tz.trim());
        } catch (DateTimeException e) {
            return ZoneOffset.UTC;
        }
    }

    public CahierImprime executer(UUID centreDemande, UUID patientDemande, LocalDate du, LocalDate au, String tz) {
        CentreId centre = session.centre(centreDemande);
        PatientId patient = new PatientId(patientDemande);
        PeriodeDuCahier periode = new PeriodeDuCahier(du, au);
        FicheDuPatient fiche = patients.fiche(centre, patient).orElseThrow(PatientIntrouvable::new);
        if (seances.nombreAImprimer(centre, patient, periode) == 0) throw CahierRefuse.vide();
        String edite = EDITION.format(ZonedDateTime.now(horloge).withZoneSameInstant(fuseau(tz)));
        boolean voletMedicalVisible = session.peutLireLeVoletMedical();
        return rendu.rendre(centre, new DemandeDeCahier(patient, periode, LigneDIdentiteDuCahier.de(fiche), edite, session.utilisateur(),
                voletMedicalVisible, lignes.composer(centre, patient, periode, voletMedicalVisible)));
    }
}
