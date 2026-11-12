package com.hemodialyse.backend.contexts.documents.application;

import com.hemodialyse.backend.contexts.documents.domain.DemandeDImpression;
import com.hemodialyse.backend.contexts.documents.domain.DemandeInvalide;
import com.hemodialyse.backend.contexts.documents.domain.FormatDeSortie;
import com.hemodialyse.backend.contexts.documents.domain.ModeleDeDocument;
import com.hemodialyse.backend.contexts.documents.domain.ParametresReserves;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.MoteurDeRendu;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.ReglesDImpressionDesTypesSensibles;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cas d'usage des routes d'impression {@code POST /api/v1/documents/print} (par type) et {@code /print/{modeleId}} (par modèle).
 *
 * <p>Ordre (comme avant, plus la garde de centre) : (1) règles des types sensibles (profil, centre), AVANT toute validation ;
 * (2) validation de la demande (400) ; (3) garde de centre de la session (403, KB-DOC1-1) ; (4) lecture du modèle ; (5) paramètres :
 * ceux du client privés des noms réservés, puis ceux du serveur (centre, identité, données des types sensibles) qui l'emportent toujours
 * (KB-DOC1-2) ; (6) rendu. Tout ce qui suit la garde de profil et ne relève pas d'un refus est enveloppé en {@link RenduEchoue}.
 */
public class ImprimerUnDocument {

    private final SessionCourante session;
    private final ModelesDeDocument modeles;
    private final PapierEnTeteDesDocuments papier;
    private final MoteurDeRendu moteur;
    private final ReglesDImpressionDesTypesSensibles regles;

    public ImprimerUnDocument(SessionCourante session, ModelesDeDocument modeles, PapierEnTeteDesDocuments papier, MoteurDeRendu moteur,
                              ReglesDImpressionDesTypesSensibles regles) {
        this.session = session;
        this.modeles = modeles;
        this.papier = papier;
        this.moteur = moteur;
        this.regles = regles;
    }

    /** Voie par type : le modèle actif du centre pour ce type. */
    public DocumentImprime parType(UUID centre, String type, String formatDemande, Map<String, String> parametres) {
        regles.garderParType(type, centre);
        return enveloppe(() -> {
            DemandeDImpression demande = DemandeDImpression.parType(centre, type, formatDemande, parametres);
            session.exigerLeCentre(demande.centre());
            ModeleDeDocument modele = modeles.actifDuCentre(demande.centre(), demande.type()).orElseThrow(() -> new DemandeInvalide(
                    "Aucun modèle actif trouvé pour ce centre/type: " + demande.type() + " (centre=" + demande.centre() + ")"));
            return rendre(demande, modele, demande.type(), demande.type());
        });
    }

    /** Voie par identifiant de modèle (utile si plusieurs modèles du même type) : le modèle doit appartenir au centre demandé. */
    public DocumentImprime parModele(UUID modeleId, UUID centre, String formatDemande, Map<String, String> parametres) {
        String typeDuModele = modeles.typeDuModele(modeleId).orElse(null);
        regles.garderParModele(typeDuModele, centre);
        return enveloppe(() -> {
            DemandeDImpression demande = DemandeDImpression.parModele(centre, formatDemande, parametres);
            session.exigerLeCentre(demande.centre());
            ModeleDeDocument modele = modeles.duCentre(modeleId, demande.centre())
                    .orElseThrow(() -> new DemandeInvalide("Modèle introuvable pour ce centre"));
            return rendre(demande, modele, typeDuModele, modele.type());
        });
    }

    private DocumentImprime rendre(DemandeDImpression demande, ModeleDeDocument modele, String typePourLesRegles, String typePourLeNom) {
        String format = demande.formatDemande() != null ? demande.formatDemande() : modele.formatDImpression();
        Map<String, Object> parametres = new HashMap<>(ParametresReserves.ecarterLesReserves(new HashMap<String, Object>(demande.parametres())));
        parametres.put("CENTER_ID", demande.centre().toString());
        parametres.putAll(papier.parametres(demande.centre()));
        parametres.putAll(regles.donneesDuServeur(typePourLesRegles, demande.centre(), demande.parametres()));
        try {
            byte[] octets = moteur.rendreParModele(modele.id(), demande.centre(), modele.cheminDuGabarit(), parametres, format);
            FormatDeSortie sortie = FormatDeSortie.de(format);
            return new DocumentImprime(octets, sortie, sortie.nomDeFichier(typePourLeNom));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception | Error e) {
            throw new RenduEchoue(e);
        }
    }

    private interface Etape {
        DocumentImprime executer();
    }

    /** Enveloppe en {@link RenduEchoue} tout échec technique, en laissant passer les refus (demande invalide, accès refusé). */
    private static DocumentImprime enveloppe(Etape etape) {
        try {
            return etape.executer();
        } catch (DemandeInvalide | AccesRefuse | RenduEchoue e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new DemandeInvalide(e.getMessage());
        } catch (RuntimeException | Error e) {
            throw new RenduEchoue(e);
        }
    }
}
