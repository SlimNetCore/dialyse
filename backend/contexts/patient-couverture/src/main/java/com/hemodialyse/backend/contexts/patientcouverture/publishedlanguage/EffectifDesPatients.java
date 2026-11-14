package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Port publié (lecture) : <b>l'effectif d'une période</b> des patients de centres donnés (patientcouverture.v1, LEC-PAT sous-slice iii,
 * ADR 0178). Le Pilotage direction en tire ses agrégats ; aucune ligne n'identifie un patient (centre, sexe, naissance, centre payeur).
 * <p>
 * Règle de l'effectif (RG-PAT-032, la seule règle de présence de Patient et couverture) : un patient est compté s'il a été admis au plus
 * tard le dernier jour de la période (admission inconnue : compté) et n'était pas déjà sorti avant son premier jour. Une sortie sans date
 * n'est plus comptée ; avec une date, TRANSFERE et GUERRI comptent jusqu'à la date incluse, DECEDE et GREFFE jusqu'à la veille (la place
 * est libérée le jour même). Un séjour limité (OCCASIONNEL, VACANCIER_LOCAL, VACANCIER_ETRANGER) compte jusqu'à la fin de séjour incluse,
 * sans borne si elle est inconnue. {@code centres} est une collection non vide ; un centre sans patient dans l'effectif est absent des
 * tables rendues.
 */
public interface EffectifDesPatients {

    /** Patients de l'effectif et, parmi eux, patients sous KT, par centre. */
    Map<UUID, EffectifDuCentre> parCentre(Collection<UUID> centres, LocalDate debut, LocalDate fin);

    /** Centre, sexe et naissance, tels que saisis (peuvent être nuls), de chaque patient de l'effectif ; sans ordre garanti. */
    List<ProfilDuPatient> profils(Collection<UUID> centres, LocalDate debut, LocalDate fin);

    /**
     * Patients de l'effectif regroupés par centre puis par centre payeur ({@code centrePayeur} nul : aucun centre payeur renseigné) ;
     * sans ordre garanti. La caisse d'un centre payeur relève de l'annuaire des payeurs ({@link PayeursDuCentre}), pas de cette lecture.
     */
    List<PatientsDuCentrePayeur> parCentrePayeur(Collection<UUID> centres, LocalDate debut, LocalDate fin);

    /** Patients de l'effectif sous EPO et sous fer prescrits sur la fiche, par centre. */
    Map<UUID, PrescriptionsDeLAnemie> prescriptionsDeLAnemie(Collection<UUID> centres, LocalDate debut, LocalDate fin);

    record EffectifDuCentre(long patients, long sousKt) {
    }

    record ProfilDuPatient(UUID centre, String sexe, LocalDate dateNaissance) {
    }

    record PatientsDuCentrePayeur(UUID centre, UUID centrePayeur, long patients) {
    }

    record PrescriptionsDeLAnemie(long sousEpo, long sousFer) {
    }
}
