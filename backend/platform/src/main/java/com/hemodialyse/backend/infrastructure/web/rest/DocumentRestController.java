package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.ReglesDImpressionDesTypesSensibles;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.ProfilDeLaSynthese;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseDeFacturation;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseMensuelle;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Règles des types sensibles de documents (reste en plateforme jusqu'à DOC4c).
 * <p>
 * La gestion des modèles ({@code /modeles}) est servie par le contexte Documents (DOC4a, ADR 0164), la liste des types
 * ({@code GET /types}) aussi (DOC4b, ADR 0165), comme les routes d'impression
 * {@code POST /print} et {@code POST /print/{modeleId}} (DOC1, ADR 0142). Cette classe fournit encore à Documents, de façon
 * TRANSITOIRE, les règles des types sensibles ({@link ReglesDImpressionDesTypesSensibles}) : profil exigé pour l'ordonnance et la
 * synthèse de facturation, totaux de la synthèse.
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentRestController implements ReglesDImpressionDesTypesSensibles {

    private final CenterAccessGuard centerAccessGuard;
    private final SyntheseDeFacturation syntheseDeFacturation;

    public DocumentRestController(CenterAccessGuard centerAccessGuard, SyntheseDeFacturation syntheseDeFacturation) {
        this.centerAccessGuard = centerAccessGuard;
        this.syntheseDeFacturation = syntheseDeFacturation;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Règles des types sensibles, fournies au contexte Documents (transitoire)
    // ═══════════════════════════════════════════════════════════════════

    private static final String SYNTHESE_FACTURATION = "SYNTHESE_FACTURATION_MENSUELLE";
    private static final String ORDONNANCE = "ORDONNANCE";

    private static String normaliser(String type) {
        return type == null ? null : type.trim().toUpperCase(Locale.ROOT);
    }

    @Override
    public void garderParType(String type, UUID centreDemande) {
        String normalise = normaliser(type);
        if (SYNTHESE_FACTURATION.equals(normalise)) {
            garderLaSyntheseDeFacturation(centreDemande);
        }
        if (ORDONNANCE.equals(normalise)) {
            garderLOrdonnance(centreDemande);
        }
    }

    @Override
    public void garderParModele(String typeDuModele, UUID centreDemande) {
        // Le type du modèle chargé (jamais un paramètre du client) décide de la garde de la synthèse de facturation.
        if (SYNTHESE_FACTURATION.equals(normaliser(typeDuModele))) {
            garderLaSyntheseDeFacturation(centreDemande);
        }
    }

    /**
     * L'impression d'une ordonnance (nom du patient, n° d'assurance, prescripteur) exige les droits de LECTURE des ordonnances
     * (MEDECIN ou ADMIN) et le contrôle du centre du corps : 403 sinon, avant tout accès aux données (KB-MED-18 corrigé, ADR 0081,
     * à confirmer avec les métiers). Les autres types de document ne changent pas.
     */
    private void garderLOrdonnance(UUID centreDemande) {
        var roles = centerAccessGuard.currentScope().roles();
        if (!roles.contains("ROLE_MEDECIN") && !roles.contains("ROLE_ADMIN")) {
            throw new AccessDeniedException("Profil insuffisant pour imprimer une ordonnance");
        }
        centerAccessGuard.requireCenter(centreDemande);
    }

    /**
     * La synthèse de facturation par modèle de document exige le même profil et le même contrôle de centre que
     * {@code POST /facturation/synthese/print} (administrateur ou secrétaire ; le médecin seul est refusé) : 403 sinon
     * (KB-FAC-52 et KB-FAC-60 corrigés). Les autres types de document ne changent pas.
     */
    private void garderLaSyntheseDeFacturation(UUID centreDemande) {
        if (!ProfilDeLaSynthese.autorise(centerAccessGuard.currentScope().roles())) {
            throw new AccessDeniedException("Profil insuffisant pour la synthèse de facturation");
        }
        centerAccessGuard.requireCenter(centreDemande);
    }

    /**
     * Les montants globaux sont les totaux enregistrés des factures de la période (ADR 0034), posés par le serveur : un
     * paramètre du même nom envoyé par le client est écarté (paramètres réservés de Documents, KB-DOC1-2). Période illisible ou
     * inversée : rien n'est posé et le gabarit retombe sur la somme des lignes, comme avant.
     */
    @Override
    public Map<String, Object> donneesDuServeur(String type, UUID centre, Map<String, String> params) {
        if (!SYNTHESE_FACTURATION.equals(normaliser(type))) {
            return Map.of();
        }
        try {
            java.time.LocalDate debut = java.time.LocalDate.parse(params.get("PERIOD_START"));
            java.time.LocalDate fin = java.time.LocalDate.parse(params.get("PERIOD_END"));
            if (debut.isAfter(fin)) {
                return Map.of();
            }
            SyntheseMensuelle synthese = syntheseDeFacturation.de(centre, debut, fin);
            // PL8 : totaux ET lignes du tableau, répartitions, indicateurs : le gabarit ne lit plus aucune table (ADR 0140 D3)
            return new HashMap<>(com.hemodialyse.backend.contexts.facturation.publishedlanguage.ModeleDeLaSynthese.parametresClasses(synthese));
        } catch (NullPointerException e) {
            // période absente : refusée par la suite comme avant
            return Map.of();
        } catch (java.time.DateTimeException e) {
            // période illisible : le gabarit n'a plus de requête qui échoue ; l'échec d'impression (500) d'avant est reproduit (PL8)
            throw new IllegalStateException("Période illisible pour SYNTHESE_FACTURATION_MENSUELLE", e);
        }
    }
}
