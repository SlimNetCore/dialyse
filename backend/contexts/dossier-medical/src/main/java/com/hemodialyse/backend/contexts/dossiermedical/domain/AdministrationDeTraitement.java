package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Une injection d'EPO ou de fer faite (ou non faite) par l'infirmier. Enregistrement immuable. Règles : patient, centre, traitement et
 * date obligatoires ; une administration effective porte une dose ; une non-administration porte un motif (et jamais de dose, d'article
 * ni de quantité : ils sont écartés, reproduit). Reproduit (KB-MED-27, à confirmer avec les métiers) : aucune cohérence entre
 * traitement, unité de la dose et article n'est contrôlée.
 */
public record AdministrationDeTraitement(UUID id, PatientId patient, CentreId centre, UUID prescription, TypeDeTraitement traitement,
                                         String molecule, DoseAdministree dose, String voie, LocalDate jour, UUID seance,
                                         String administrePar, boolean administree, String motif, UUID article, BigDecimal quantiteArticle,
                                         Instant creeeLe) {

    public static AdministrationDeTraitement enregistrer(UUID id, PatientId patient, CentreId centre, UUID prescription,
                                                         TypeDeTraitement traitement, String molecule, DoseAdministree dose, String voie,
                                                         LocalDate jour, UUID seance, String administrePar, boolean administree,
                                                         String motif, UUID article, BigDecimal quantiteArticle, Instant maintenant) {
        if (patient == null || centre == null || traitement == null || jour == null) {
            throw new RegleDeGestionViolee("ADMINISTRATION_CHAMPS_REQUIS",
                    "Patient, centre, type de traitement et date d'administration sont obligatoires");
        }
        if (administree && dose == null) {
            throw new RegleDeGestionViolee("ADMINISTRATION_DOSE_REQUISE", "La dose est obligatoire pour un traitement effectivement administré");
        }
        if (!administree && (motif == null || motif.isBlank())) {
            throw new RegleDeGestionViolee("ADMINISTRATION_MOTIF_REQUIS",
                    "Un motif est obligatoire lorsque le traitement n'a pas été administré");
        }
        borner(molecule, 100, "La molécule");
        borner(voie, 10, "La voie");
        borner(administrePar, 100, "L'auteur de l'administration");
        borner(motif, 500, "Le motif");
        if (quantiteArticle != null && quantiteArticle.precision() - quantiteArticle.scale() > 7) {
            throw new SaisieInvalide("La quantité d'article est trop grande");
        }
        return new AdministrationDeTraitement(id, patient, centre, prescription, traitement, molecule, administree ? dose : null, voie, jour,
                seance, administrePar, administree, motif, administree ? article : null, administree ? quantiteArticle : null, maintenant);
    }

    private static void borner(String valeur, int max, String quoi) {
        if (valeur != null && valeur.length() > max) throw new SaisieInvalide(quoi + " est limité à " + max + " caractères");
    }
}
