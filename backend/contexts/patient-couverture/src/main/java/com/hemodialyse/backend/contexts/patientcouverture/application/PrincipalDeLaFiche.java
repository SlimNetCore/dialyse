package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.AssurePrincipal;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Couverture;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Patient;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Ce que l'enregistrement de la fiche écrit pour l'assuré principal (slice PC2). Dans la transaction de la fiche, sous le verrou
 * du centre déjà pris (R1 : un seul assuré principal).
 * <ul>
 *   <li>Patient assuré lui-même : les lignes d'affectation perdent leur caractère principal (aucune date modifiée, comme
 *   aujourd'hui).</li>
 *   <li>Ayant droit : l'assuré est créé ou mis à jour (jamais capturé à un autre centre : 422
 *   {@code ASSURE_D_UN_AUTRE_CENTRE}) ; une ligne d'affectation n'est ajoutée que si cet assuré n'est pas déjà le principal du
 *   patient.</li>
 * </ul>
 */
public class PrincipalDeLaFiche {

    private final AssuresDuCentre assures;

    public PrincipalDeLaFiche(AssuresDuCentre assures) {
        this.assures = assures;
    }

    public void aligner(CentreId centre, Patient patient) {
        Couverture couverture = patient.fiche().couverture();
        if (couverture.assureLuiMeme()) {
            assures.retirerLePrincipal(centre, patient.id());
            return;
        }
        String numero = couverture.assureNumeroAssurance();
        Optional<AssuresDuCentre.Assure> existant = assures.trouver(numero);
        AssurePrincipal.verifierQueLAssureEstDuCentre(existant.map(AssuresDuCentre.Assure::centre).orElse(null), centre);
        Couverture.AssureSurLaFiche a = couverture.assure();
        assures.declarer(new AssuresDuCentre.Assure(numero, a.nom(), a.prenom(), a.sexe(), dateOuNulle(a.dateNaissance()),
                a.telPersonnel(), a.telMobile(), a.telBureau(), a.adresse(), a.groupeSanguin(), centre.valeur()));
        Optional<String> principal = assures.principalActuel(centre, patient.id()).map(AssuresDuCentre.Affectation::numeroAssurance);
        if (AssurePrincipal.doitAjouterUneAffectation(principal, numero)) {
            assures.retirerLePrincipal(centre, patient.id());
            assures.lierSansDates(centre, patient.id(), numero);
        }
    }

    /** Date de naissance de l'assuré saisie en texte : illisible ou absente, elle est ignorée en silence (comme aujourd'hui). */
    private static LocalDate dateOuNulle(String texte) {
        if (texte == null || texte.isBlank()) return null;
        try {
            return LocalDate.parse(texte.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }
}
