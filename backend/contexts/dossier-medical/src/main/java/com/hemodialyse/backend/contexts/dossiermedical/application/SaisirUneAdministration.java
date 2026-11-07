package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.AdministrationDeTraitement;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Administrations;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DoseAdministree;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeDeTraitement;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.NotificationsDeLAnemie;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * L'infirmier (ou le médecin) saisit une administration de traitement de l'anémie, effective ou non faite.
 * <p>
 * Reproduit du legacy : pour une administration effective avec article et dose, la quantité d'article est DÉDUITE de la fiche article
 * (celle du client est ignorée) et un article inconnu est refusé (422 {@code ARTICLE_INTROUVABLE}) ; avec une séance, la sortie de
 * stock est faite à la date du jour. Écarts décidés : patient inconnu ou d'un autre centre = 404 (KB-MED-19) ; tout se fait désormais
 * dans UNE transaction, dans l'ordre du legacy (conversion, sortie de stock, validation, enregistrement) : une saisie refusée ensuite
 * annule la sortie de stock (avant, elle restait sortie) ; traitement ou « administrée » absent = 400 (KB-MED-22). Droits : INFIRMIER et MEDECIN.
 */
public class SaisirUneAdministration {

    /** La saisie telle que reçue. */
    public record Saisie(UUID prescription, String traitement, String molecule, BigDecimal dose, String uniteDose, String voie, LocalDate jour,
                         UUID seance, String administrePar, Boolean administree, String motif, UUID article, BigDecimal quantiteArticle) {
    }

    private final SessionCourante session;
    private final PatientsDuCentre patients;
    private final Administrations administrations;
    private final CatalogueDesArticles catalogue;
    private final SortiesDeStockDesAdministrations sortiesDeStock;
    private final UniteDeTravail uniteDeTravail;
    private final GenerateurDIdentifiants identifiants;
    private final HorlogeDuCentre horloge;
    private final NotificationsDeLAnemie notifications;

    public SaisirUneAdministration(SessionCourante session, PatientsDuCentre patients, Administrations administrations,
                                   CatalogueDesArticles catalogue, SortiesDeStockDesAdministrations sortiesDeStock,
                                   UniteDeTravail uniteDeTravail, GenerateurDIdentifiants identifiants, HorlogeDuCentre horloge,
                                   NotificationsDeLAnemie notifications) {
        this.session = session;
        this.patients = patients;
        this.administrations = administrations;
        this.catalogue = catalogue;
        this.sortiesDeStock = sortiesDeStock;
        this.uniteDeTravail = uniteDeTravail;
        this.identifiants = identifiants;
        this.horloge = horloge;
        this.notifications = notifications;
    }

    public AdministrationDeTraitement executer(UUID centreDemande, UUID patientDemande, Saisie saisie) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        if (saisie.administree() == null) throw new SaisieInvalide("Le champ administree est obligatoire");
        boolean administree = saisie.administree();
        TypeDeTraitement traitement = typeDe(saisie.traitement());
        return uniteDeTravail.executer(() -> {
            if (!patients.existe(centre, patient)) throw new PatientIntrouvable();
            BigDecimal quantite = saisie.quantiteArticle();
            if (administree && saisie.article() != null && saisie.dose() != null) {
                quantite = catalogue.quantitePourLaDose(centre, saisie.article(), saisie.dose(), saisie.uniteDose());
            }
            // Ordre du legacy conservé (le refus du Stock pour une séance facturée précède la validation de la saisie : filet Facturation F6),
            // mais tout dans UNE transaction : une saisie refusée ensuite annule aussi la sortie de stock.
            if (administree && saisie.article() != null && saisie.seance() != null && quantite != null) {
                sortiesDeStock.sortir(centre, saisie.seance(), patient, horloge.aujourdhui(centre), saisie.article(), quantite, saisie.administrePar());
            }
            DoseAdministree dose = saisie.dose() == null ? null : new DoseAdministree(saisie.dose(), saisie.uniteDose());
            var administration = AdministrationDeTraitement.enregistrer(identifiants.suivant(), patient, centre, saisie.prescription(), traitement,
                    saisie.molecule(), dose, saisie.voie(), saisie.jour(), saisie.seance(), saisie.administrePar(), administree, saisie.motif(),
                    saisie.article(), quantite, horloge.maintenant());
            administrations.ajouter(administration);
            notifications.administrationSaisie(centre.valeur(), patient.valeur(), saisie.jour());
            return administration;
        });
    }

    private static TypeDeTraitement typeDe(String texte) {
        if (texte == null) throw new SaisieInvalide("Le type de traitement est obligatoire");
        try {
            return TypeDeTraitement.valueOf(texte);
        } catch (IllegalArgumentException e) {
            throw new SaisieInvalide("Type de traitement inconnu");
        }
    }
}
