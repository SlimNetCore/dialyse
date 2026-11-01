package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.Assure;
import com.hemodialyse.backend.contexts.patientcouverture.domain.AssureId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.AssurePatientAssignment;
import com.hemodialyse.backend.contexts.patientcouverture.domain.AssurePatientRepository;
import com.hemodialyse.backend.contexts.patientcouverture.domain.AssureRepository;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientRepository;
import com.hemodialyse.backend.contexts.patientcouverture.domain.TypeAssure;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class AssureService implements AssureUseCases {
    private final AssureRepository assures;
    private final AssurePatientRepository affectations;
    private final PatientRepository patients;
    private final java.util.function.Supplier<UUID> idGenerator;

    public AssureService(AssureRepository assures, AssurePatientRepository affectations, PatientRepository patients,
                         java.util.function.Supplier<UUID> idGenerator) {
        this.assures = assures;
        this.affectations = affectations;
        this.patients = patients;
        this.idGenerator = idGenerator;
    }

    @Override
    public AssureId affecterPrincipal(AffecterAssurePrincipalCommande commande, Instant maintenant) {
        patients.trouver(commande.patientId(), commande.centreId())
                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable"));
        AssureId id = new AssureId(commande.numeroAssurance());
        Assure a = assures.trouver(id, commande.centreId()).orElse(null);
        if (a == null) {
            a = Assure.creer(id, commande.centreId(), commande.nom(), commande.prenom(), commande.sexe(),
                    commande.dateNaissance(), commande.telPersonnel(), commande.telMobile(), commande.telBureau(),
                    commande.adresse(), commande.groupeSanguin(), commande.typeAssure());
        } else {
            a.modifier(commande.nom(), commande.prenom(), commande.sexe(), commande.dateNaissance(),
                    commande.telPersonnel(), commande.telMobile(), commande.telBureau(), commande.adresse(),
                    commande.groupeSanguin(), commande.typeAssure());
        }
        assures.sauvegarder(a);

        // R1: un seul assuré principal actif par patient
        affectations.terminerActifs(commande.patientId(), commande.centreId(), LocalDate.now());
        AssurePatientAssignment aff = new AssurePatientAssignment(idGenerator.get(), commande.patientId(),
                commande.centreId(), id, true, LocalDate.now(), null);
        affectations.sauvegarder(aff);
        return id;
    }

    @Override
    public void modifier(ModifierAssureCommande commande) {
        Assure a = assures.trouver(commande.assureId(), commande.centreId())
                .orElseThrow(() -> new IllegalArgumentException("Assure introuvable"));
        a.modifier(commande.nom(), commande.prenom(), commande.sexe(), commande.dateNaissance(),
                commande.telPersonnel(), commande.telMobile(), commande.telBureau(), commande.adresse(),
                commande.groupeSanguin(), commande.typeAssure());
        assures.sauvegarder(a);
    }

    @Override
    public Assure consulter(AssureId assureId, CentreId centreId) {
        return assures.trouver(assureId, centreId)
                .orElseThrow(() -> new IllegalArgumentException("Assure introuvable"));
    }

    @Override
    public Assure consulterPrincipal(PatientId patientId, CentreId centreId) {
        AssurePatientAssignment aff = affectations.trouverPrincipal(patientId, centreId)
                .orElseThrow(() -> new IllegalArgumentException("Aucun assure principal"));
        return assures.trouver(aff.numeroAssurance(), centreId)
                .orElseThrow(() -> new IllegalArgumentException("Assure introuvable"));
    }
}