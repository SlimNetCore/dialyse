package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.MarqueurSerologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.RegleDeGestionViolee;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ResultatSerologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologies;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Enregistre un résultat sérologique pour un patient du centre de la session, puis fait réévaluer sa place en isolement, dans UNE
 * transaction. Ordre des contrôles reproduit du legacy : centre de la session (403), marqueur puis résultat (400), puis sous verrou :
 * doublon (422 {@code BUSINESS_RULE_VIOLATION}), règles de la sérologie (champs, date future, conduite), puis la Planification (patient
 * inconnu ou d'un autre centre : 422 {@code PLACEMENT_PATIENT_INTROUVABLE}, sérologie annulée). Date absente : le jour du centre.
 * Droit : MEDECIN (porté par l'interface). Aucun événement publié (KB-MED-46), aucune ligne d'audit propre (KB-MED-54).
 */
public class EnregistrerUneSerologie {

    private final SessionCourante session;
    private final Serologies serologies;
    private final IsolementDesPatients isolement;
    private final UniteDeTravail uniteDeTravail;
    private final GenerateurDIdentifiants identifiants;
    private final HorlogeDuCentre horloge;

    public EnregistrerUneSerologie(SessionCourante session, Serologies serologies, IsolementDesPatients isolement,
                                   UniteDeTravail uniteDeTravail, GenerateurDIdentifiants identifiants, HorlogeDuCentre horloge) {
        this.session = session;
        this.serologies = serologies;
        this.isolement = isolement;
        this.uniteDeTravail = uniteDeTravail;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    public Serologie executer(UUID centreDemande, UUID patientDemande, String marqueurSaisi, String resultatSaisi, BigDecimal titre,
                              String unite, LocalDate datePrelevement, String laboratoire, LocalDate dateProchainControle,
                              String conduiteATenir) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        var marqueur = MarqueurSerologique.lire(marqueurSaisi);
        var resultat = ResultatSerologique.lire(resultatSaisi);
        var aujourdhui = horloge.aujourdhui(centre);
        var date = datePrelevement != null ? datePrelevement : aujourdhui;
        return uniteDeTravail.executer(() -> {
            isolement.verrouillerLesPlaces(centre, patient);
            if (serologies.existeEnVerrouillant(centre, patient, marqueur, date)) {
                throw new RegleDeGestionViolee("BUSINESS_RULE_VIOLATION", "Un résultat existe déjà pour ce marqueur à cette date de prélèvement");
            }
            var serologie = Serologie.enregistrer(identifiants.suivant(), patient, centre, marqueur, resultat, titre, unite, date, aujourdhui,
                    laboratoire, dateProchainControle, conduiteATenir, horloge.maintenant());
            serologies.ajouter(serologie);
            isolement.reevaluer(centre, patient);
            return serologie;
        });
    }
}
