package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationBiologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationsBiologiques;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeFiches;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Consulte les observations biologiques d'un patient du centre de la session : la liste paginée (du prélèvement le plus récent au plus
 * ancien, filtres code LOINC par égalité stricte et période incluse) et celles d'une demande d'examen. Bornes REPRODUITES du legacy
 * (KB-MED-83) : page négative = 0, taille nulle ou négative = 20, plafond 200 ; période inversée = page vide. Patient inconnu ou d'un
 * autre centre : liste vide (200, reproduit). Lecture par demande (écart décidé KB-MED-77, ferme KB-MED-44) : la demande doit appartenir
 * au centre ET au patient de l'URL, sinon la réponse est celle d'« aucune observation » (liste vide, sans révéler l'existence de la
 * demande) ; seules les observations de CE patient sont rendues. Lectures non tracées dans le journal d'audit (à confirmer avec le DPO).
 * Droits : MEDECIN et ADMIN (KB-MED-78, à confirmer avec les métiers).
 */
public class ConsulterLesObservationsBiologiquesDuPatient {
    private final SessionCourante session;
    private final ObservationsBiologiques observations;
    private final DemandesDuPatient demandes;

    public ConsulterLesObservationsBiologiquesDuPatient(SessionCourante session, ObservationsBiologiques observations,
                                                        DemandesDuPatient demandes) {
        this.session = session;
        this.observations = observations;
        this.demandes = demandes;
    }

    public PageDeFiches<ObservationBiologique> liste(UUID centreDemande, UUID patient, String code, LocalDate du, LocalDate au, int page,
                                                     int taille) {
        var centre = session.centre(centreDemande);
        return observations.duPatient(centre, new PatientId(patient), code, du, au, BornesDePage.page(page), BornesDePage.taille(taille));
    }

    public List<ObservationBiologique> deLaDemande(UUID centreDemande, UUID patientDemande, UUID demande) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        if (!demandes.existe(centre, patient, demande)) return List.of();
        return observations.deLaDemande(centre, patient, demande);
    }
}
