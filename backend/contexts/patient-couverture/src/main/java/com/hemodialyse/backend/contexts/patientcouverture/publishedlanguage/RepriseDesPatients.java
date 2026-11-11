package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Langage publié {@code patientcouverture.v1} : la reprise des patients d'un centre (outil de migration historique, ADR 0134, S6b-2).
 * L'appelant contrôle tout le fichier (doublons, numéros et codes pris, références aux référentiels, assuré connu du centre) AVANT
 * d'écrire ; ce langage ne fait que lire l'existant du centre et écrire la fiche, sans jamais effacer une donnée.
 */
public interface RepriseDesPatients {

    /** Patients déjà présents dans le centre (pour rapprocher par numéro d'assurance ou code et repérer les doublons). */
    List<PatientDuCentre> duCentre(UUID centre);

    /**
     * Crée la fiche ou la met à jour, rattache l'assuré principal (l'assuré lui-même, ou l'assuré indiqué) et renvoie l'identifiant.
     * <p>
     * Une reprise n'efface jamais une donnée : seules les valeurs renseignées du report remplacent celles de la fiche, hormis le
     * nom, le prénom, le sexe, les dates de naissance et d'admission, le numéro d'assurance, le type, l'état et la qualité d'assuré,
     * qui sont toujours remplacés.
     *
     * @param patient identifiant du patient à mettre à jour ; si aucune fiche de ce centre ne le porte (ou s'il est nul), la fiche
     *                est créée, sous cet identifiant quand il est donné (patient supprimé depuis la première reprise : ses
     *                affectations retrouvent leur fiche) et sinon sous un nouvel identifiant
     */
    UUID reprendre(UUID centre, UUID patient, ReportDuPatient report);

    record PatientDuCentre(UUID id, String numeroAssurance, String code, String nom, String prenom, LocalDate dateNaissance) {
    }

    /** Valeurs du fichier de reprise ; une valeur nulle n'est pas renseignée (les premières sont obligatoires). */
    record ReportDuPatient(String numeroAssurance, String nom, String prenom, String sexe, LocalDate dateNaissance,
                           LocalDate dateAdmission, boolean vacancier, String etat, String qualiteAssure, String code,
                           String civilite, String lieuNaissance, String situationFamiliale, Integer nombreEnfants,
                           String profession, String adresse, String telPersonnel, String telMobile, String telBureau,
                           String email, String groupeSanguin, LocalDate dateEvenementEtat, String observation, UUID centrePayeur,
                           UUID medecinTraitant, UUID salle, UUID creneau, UUID generateur, UUID transporteurAller,
                           UUID transporteurRetour, Set<DayOfWeek> jours, String assureNumeroAssurance) {
    }
}
