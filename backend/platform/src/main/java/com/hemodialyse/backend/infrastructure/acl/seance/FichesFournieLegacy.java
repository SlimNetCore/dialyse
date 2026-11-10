package com.hemodialyse.backend.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients;
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
 * Fournit à la Séance le langage publié {@link FichesFournies}. Hybride (ADR 0133) : les recherches et références (S5a) lisent le
 * langage publié {@link FichesDesPatients} ; la fiche (S5b) lit encore les patients legacy ({@link PatientRepositoryPort}). Toute
 * lecture est bornée au centre demandé.
 */
@Component
public class FichesFournieLegacy implements FichesFournies {

    private final PatientRepositoryPort patients;
    private final FichesDesPatients publiees;

    public FichesFournieLegacy(PatientRepositoryPort patients, FichesDesPatients publiees) {
        this.patients = patients;
        this.publiees = publiees;
    }

    /** S5b : reste lue dans les patients historiques (jointure avec la planification et le générateur). */
    @Override
    public Optional<Fiche> fiche(UUID centre, UUID patient) {
        return patients.findById(PatientId.of(patient), CenterId.of(centre)).map(FichesFournieLegacy::fiche);
    }

    @Override
    public Optional<UUID> parIdentifiant(UUID centre, UUID identifiant) {
        return publiees.existe(centre, identifiant) ? Optional.of(identifiant) : Optional.empty();
    }

    @Override
    public Optional<UUID> parCode(UUID centre, String code) {
        return publiees.parCode(centre, code);
    }

    @Override
    public Optional<UUID> parNumeroDAssurance(UUID centre, String numero) {
        return publiees.parNumeroDAssurance(centre, numero);
    }

    @Override
    public List<References> tous(UUID centre) {
        return publiees.references(centre).stream()
                .map(r -> new References(r.patient(), r.code(), r.numeroAssurance(), r.numeroAssuranceDeLAssure()))
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
