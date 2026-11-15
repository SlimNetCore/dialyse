package com.hemodialyse.backend.contexts.patientcouverture.interfaces.publie;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.RegleDImpressionDUnType;
import com.hemodialyse.backend.contexts.patientcouverture.application.ComposerLaFicheSignaletiqueImprimee;
import com.hemodialyse.backend.contexts.patientcouverture.application.ComposerLaFicheSignaletiqueImprimee.FicheSignaletiqueImprimee;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.patientcouverture.interfaces.publie.IdentifiantsDuDocument.date;
import static com.hemodialyse.backend.contexts.patientcouverture.interfaces.publie.IdentifiantsDuDocument.lireSansTolererLeVide;

/**
 * La règle d'impression de la fiche signalétique du patient, publiée à Documents (documents.v1, DOC5i, ADR 0201). Le type n'est PAS gardé
 * (comportement reproduit) ; le gabarit n'embarque plus de requête, le serveur pose la ligne ({@code LIGNES}) composée par Patient et
 * couverture, sur les deux voies. Un identifiant de patient absent ne désigne rien (document vide) ; présent mais vide ou illisible, c'est un
 * échec d'impression (500) au texte de l'ancienne erreur SQL (l'ancienne requête n'avait pas de {@code NULLIF}). Mêmes noms de champs
 * (majuscules) et mêmes types que ceux du gabarit.
 */
public final class RegleDImpressionDeLaFicheSignaletique implements RegleDImpressionDUnType {

    public static final String TYPE = "FICHE_PATIENT";

    private final ComposerLaFicheSignaletiqueImprimee fiche;

    public RegleDImpressionDeLaFicheSignaletique(ComposerLaFicheSignaletiqueImprimee fiche) {
        this.fiche = fiche;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void garder(UUID centreDemande) {
        // Aucune garde : le type n'en avait pas avant la conversion et n'en reçoit pas (comportement reproduit).
    }

    @Override
    public Map<String, Object> donneesDuServeur(UUID centre, Map<String, String> parametres) {
        UUID patient = lireSansTolererLeVide(parametres.get("patientId"), "fiche_patient");
        return Map.of("LIGNES", fiche.executer(centre, patient).map(f -> List.of(champs(f))).orElse(List.of()));
    }

    static Map<String, Object> champs(FicheSignaletiqueImprimee imprimee) {
        var f = imprimee.fiche();
        Map<String, Object> c = new HashMap<>();
        c.put("CODE_PATIENT", f.codePatient());
        c.put("CIVILITE", f.civilite());
        c.put("NOM", f.nom());
        c.put("PRENOM", f.prenom());
        c.put("SEXE", f.sexe());
        c.put("GROUPE_SANGUIN", f.groupeSanguin());
        c.put("DATE_NAISSANCE", date(f.dateNaissance()));
        c.put("LIEU_NAISSANCE", f.lieuNaissance());
        c.put("DATE_ADMISSION", date(f.dateAdmission()));
        c.put("NOMBRE_ENFANTS", f.nombreEnfants());
        c.put("SITUATION_FAMILIALE", f.situationFamiliale());
        c.put("PROFESSION1", f.profession());
        c.put("ADRESSE", f.adresse());
        c.put("TEL_PERSONNEL", f.telPersonnel());
        c.put("TEL_MOBILE", f.telMobile());
        c.put("TEL_BUREAU", f.telBureau());
        c.put("EMAIL", f.email());
        c.put("NUMERO_ASSURANCE", f.numeroAssurance());
        c.put("ETAT_PATIENT", f.etatPatient());
        c.put("QUALITE_ASSURE", f.qualiteAssure());
        c.put("OBSERVATION", f.observation());
        c.put("SOUS_KT", f.sousKt());
        c.put("ASSURE_NOM", f.assureNom());
        c.put("ASSURE_PRENOM", f.assurePrenom());
        c.put("CENTER_NAME", imprimee.nomDuCentre());
        return c;
    }
}
