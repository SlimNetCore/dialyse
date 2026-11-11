package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesPatients.PatientDuCentre;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Port sortant de la reprise des patients (table {@code patients}) : la fiche telle que la reprise la lit et l'écrit, bornée au
 * centre. N'écrit que les colonnes de la fiche qu'une reprise renseigne (jamais les pièces jointes, la photo, la prescription
 * d'EPO ou de fer, l'historique de l'assuré) ; les colonnes de place sont écrites par la reprise seule, sans contrôle de la
 * Planification (l'outil se lance hors activité du centre, ADR 0134).
 */
public interface PatientsDeLaReprise {

    List<PatientDuCentre> duCentre(CentreId centre);

    Optional<Fiche> trouver(CentreId centre, UUID id);

    /** Vrai si une fiche de n'importe quel centre porte cet identifiant (jamais réutilisé pour la fiche d'un autre centre). */
    boolean identifiantPris(UUID id);

    void creer(CentreId centre, Fiche fiche, Instant creeLe);

    void mettreAJour(CentreId centre, Fiche fiche);

    /** Ce que l'assuré lui-même recopie sur la fiche (colonnes {@code assure_*}). */
    record AssureSurLaFiche(String sexe, String nom, String prenom, LocalDate dateNaissance, String telPersonnel, String adresse,
                            String groupeSanguin, String telMobile, String telBureau) {
        static AssureSurLaFiche vide() {
            return new AssureSurLaFiche(null, null, null, null, null, null, null, null, null);
        }
    }

    record Fiche(UUID id, String code, String numeroAssurance, String nom, String prenom, String sexe, LocalDate dateNaissance,
                 LocalDate dateAdmission, boolean vacancier, String etat, String qualiteAssure, String civilite,
                 String lieuNaissance, String situationFamiliale, int nombreEnfants, String profession, String adresse,
                 String telPersonnel, String telMobile, String telBureau, String email, String groupeSanguin,
                 LocalDate dateEvenementEtat, String observation, UUID centrePayeur, UUID medecinTraitant, UUID salle,
                 UUID creneau, UUID generateur, UUID transporteurAller, UUID transporteurRetour, Set<DayOfWeek> jours,
                 String assureNumeroAssurance, AssureSurLaFiche assure) {

        Fiche avecLAssure(String numeroDeLAssure, AssureSurLaFiche recopie) {
            return new Fiche(id, code, numeroAssurance, nom, prenom, sexe, dateNaissance, dateAdmission, vacancier, etat,
                    qualiteAssure, civilite, lieuNaissance, situationFamiliale, nombreEnfants, profession, adresse, telPersonnel,
                    telMobile, telBureau, email, groupeSanguin, dateEvenementEtat, observation, centrePayeur, medecinTraitant,
                    salle, creneau, generateur, transporteurAller, transporteurRetour, jours, numeroDeLAssure, recopie);
        }
    }
}
