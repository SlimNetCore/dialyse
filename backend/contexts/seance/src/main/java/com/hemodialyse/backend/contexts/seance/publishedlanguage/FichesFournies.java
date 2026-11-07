package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié (fourni à la Séance par la plateforme, ADR 0088) : les fiches des patients DU CENTRE. Un patient inconnu ou d'un autre
 * centre n'est jamais rendu. Valeurs telles que saisies (nulles ou vides possibles).
 */
public interface FichesFournies {

    Optional<Fiche> fiche(UUID centre, UUID patient);

    Optional<UUID> parIdentifiant(UUID centre, UUID identifiant);

    /** Code patient en majuscules. */
    Optional<UUID> parCode(UUID centre, String code);

    Optional<UUID> parNumeroDAssurance(UUID centre, String numero);

    List<References> tous(UUID centre);

    record Fiche(UUID id, String code, String nom, String prenom, String sexe, LocalDate dateDeNaissance, String numeroDAssurance,
                 String groupeSanguin, String telephoneMobile, UUID medecinTraitant, UUID salle, UUID position, UUID generateur,
                 String generateurNom, String generateurMarque, String generateurEtat) {
    }

    record References(UUID patient, String code, String numeroDAssurance, String numeroDAssuranceDeLAssure) {
    }
}
