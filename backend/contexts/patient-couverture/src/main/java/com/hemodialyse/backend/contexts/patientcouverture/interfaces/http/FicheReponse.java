package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http;

import com.hemodialyse.backend.contexts.patientcouverture.application.FicheDuPatient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Affectation;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Couverture;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Fiche;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Réponse de {@code GET /api/v1/patients/{id}} : DTO dédié (plus l'entité de domaine) dont le JSON est identique, champ par
 * champ, à celui de l'ancienne entité sérialisée (le front consomme cette forme) : identifiants et numéro d'assurance sont des
 * objets {@code {"value": ...}}.
 */
record FicheReponse(Valeur id, Valeur centerId, String codePatient, String civilite, String nom, String prenom, String sexe,
                    String groupeSanguin, int nombreEnfants, LocalDate dateNaissance, String lieuNaissance,
                    String situationFamiliale, LocalDate dateAdmission, boolean enSommeil, String etatPatient,
                    LocalDate dateEvenementEtat, String profession, String adresse, String telPersonnel, String telMobile,
                    String telBureau, String email, boolean sousKt, boolean epoEnabled, LocalDate epoDate, boolean ferEnabled,
                    LocalDate ferDate, String observation, String photoBase64, ValeurTexte numeroAssurance,
                    String typePatient, String qualiteAssure, UUID centrePayeurId, String assureNumeroAssurance,
                    AssureInfo assureInfo, String assureHistoryJson, String piecesJointesJson, UUID medecinTraitantId,
                    UUID salleId, UUID positionId, UUID transporteurAllerId, UUID transporteurRetourId,
                    UUID categorieTransportId, UUID generateurId, String generateurNom, String generateurMarque,
                    String generateurEtat, JoursDialyse joursDialyse, OffsetDateTime createdAt) {

    record Valeur(UUID value) {
    }

    record ValeurTexte(String value) {
    }

    record AssureInfo(String sexe, String nom, String prenom, String dateNaissance, String telPersonnel, String adresse,
                      String groupeSanguin, String telMobile, String telBureau) {
    }

    record JoursDialyse(boolean dimanche, boolean lundi, boolean mardi, boolean mercredi, boolean jeudi, boolean vendredi,
                        boolean samedi) {
    }

    static FicheReponse depuis(FicheDuPatient lue) {
        Fiche f = lue.fiche();
        var i = f.identite();
        var k = f.coordonnees();
        var s = f.suivi();
        Couverture c = f.couverture();
        Affectation a = f.affectation();
        var a2 = c.assure();
        var g = lue.generateur();
        var j = a.jours();
        return new FicheReponse(new Valeur(lue.id().valeur()), new Valeur(lue.centre().valeur()), lue.code(), i.civilite(),
                i.nom(), i.prenom(), i.sexe(), i.groupeSanguin(), i.nombreEnfants(), i.dateNaissance(), i.lieuNaissance(),
                i.situationFamiliale(), f.dateAdmission(), s.enSommeil(), lue.etatBrut(), f.dateEvenement(), k.profession(),
                k.adresse(), k.telPersonnel(), k.telMobile(), k.telBureau(), k.email(), s.sousKt(), s.epoActive(), s.epoDate(),
                s.ferActif(), s.ferDate(), s.observation(), s.photoBase64(), new ValeurTexte(c.numeroAssurance().valeur()),
                lue.vacancier() ? "VACANCIER" : "NON_VACANCIER", c.qualiteAssure(), c.centrePayeurId(),
                c.assureNumeroAssurance(),
                new AssureInfo(a2.sexe(), a2.nom(), a2.prenom(), a2.dateNaissance(), a2.telPersonnel(), a2.adresse(),
                        a2.groupeSanguin(), a2.telMobile(), a2.telBureau()),
                // pièces jointes : jamais relues par la fiche actuelle (KB-PAT-19, reproduit : toujours nul)
                c.assureHistoriqueJson(), null, a.medecinTraitantId(), a.salleId(), a.creneauId(),
                a.transporteurAllerId(), a.transporteurRetourId(), a.categorieTransportId(),
                g != null ? g.id() : a.generateurId(), g == null ? null : g.code(), g == null ? null : g.fabricant(),
                g == null ? null : g.statut(),
                new JoursDialyse(j.dimanche(), j.lundi(), j.mardi(), j.mercredi(), j.jeudi(), j.vendredi(), j.samedi()),
                lue.creeLe() == null ? null : OffsetDateTime.ofInstant(lue.creeLe(), ZoneOffset.UTC));
    }
}
