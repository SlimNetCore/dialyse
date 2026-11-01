package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.Assure;
import com.hemodialyse.backend.contexts.patientcouverture.domain.AssureId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.AssurePatientAssignment;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.TypeAssure;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public interface AssureUseCases {
    AssureId affecterPrincipal(AffecterAssurePrincipalCommande commande, Instant maintenant);

    void modifier(ModifierAssureCommande commande);

    Assure consulter(AssureId assureId, CentreId centreId);

    Assure consulterPrincipal(PatientId patientId, CentreId centreId);

    record AffecterAssurePrincipalCommande(PatientId patientId, CentreId centreId, String numeroAssurance,
                                           String nom, String prenom, String sexe, LocalDate dateNaissance,
                                           String telPersonnel, String telMobile, String telBureau, String adresse,
                                           String groupeSanguin, TypeAssure typeAssure, UUID auteurId) {
    }

    record ModifierAssureCommande(AssureId assureId, CentreId centreId, String nom, String prenom, String sexe,
                                  LocalDate dateNaissance, String telPersonnel, String telMobile, String telBureau,
                                  String adresse, String groupeSanguin, TypeAssure typeAssure) {
    }
}