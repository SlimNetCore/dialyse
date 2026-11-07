package com.hemodialyse.backend.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.FichesFournies;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Fournit à la Séance le langage publié {@link FichesFournies} (ACL vers les patients legacy, {@link PatientRepositoryPort}). Toute
 * lecture est bornée au centre demandé.
 */
@Component
public class FichesFournieLegacy implements FichesFournies {

    private final PatientRepositoryPort patients;

    public FichesFournieLegacy(PatientRepositoryPort patients) {
        this.patients = patients;
    }

    @Override
    public Optional<Fiche> fiche(UUID centre, UUID patient) {
        return patients.findById(PatientId.of(patient), CenterId.of(centre)).map(FichesFournieLegacy::fiche);
    }

    @Override
    public Optional<UUID> parIdentifiant(UUID centre, UUID identifiant) {
        return patients.findById(PatientId.of(identifiant), CenterId.of(centre)).map(p -> p.getId().value());
    }

    @Override
    public Optional<UUID> parCode(UUID centre, String code) {
        return patients.findByCodePatient(CenterId.of(centre), code).map(p -> p.getId().value());
    }

    @Override
    public Optional<UUID> parNumeroDAssurance(UUID centre, String numero) {
        return patients.findByNumeroAssurance(CenterId.of(centre), numero).map(p -> p.getId().value());
    }

    @Override
    public List<References> tous(UUID centre) {
        return patients.findAllByCenter(CenterId.of(centre)).stream()
                .filter(p -> p != null)
                .map(p -> new References(p.getId().value(), p.getCodePatient(), numero(p), p.getAssureNumeroAssurance()))
                .toList();
    }

    private static String numero(Patient p) {
        return p.getNumeroAssurance() == null ? null : p.getNumeroAssurance().value();
    }

    private static Fiche fiche(Patient p) {
        return new Fiche(p.getId().value(), p.getCodePatient(), p.getNom(), p.getPrenom(), p.getSexe(), p.getDateNaissance(), numero(p),
                p.getGroupeSanguin(), p.getTelMobile(), p.getMedecinTraitantId(), p.getSalleId(), p.getPositionId(),
                p.getGenerateurId(), p.getGenerateurNom(), p.getGenerateurMarque(), p.getGenerateurEtat());
    }
}
