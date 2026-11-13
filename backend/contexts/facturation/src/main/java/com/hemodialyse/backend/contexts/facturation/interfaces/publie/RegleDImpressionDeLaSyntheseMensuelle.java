package com.hemodialyse.backend.contexts.facturation.interfaces.publie;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.RegleDImpressionDUnType;
import com.hemodialyse.backend.contexts.facturation.application.SessionCourante;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.ModeleDeLaSynthese;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.ProfilDeLaSynthese;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseDeFacturation;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseMensuelle;
import org.springframework.security.access.AccessDeniedException;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * La règle d'impression de la synthèse mensuelle de facturation, publiée à Documents (documents.v1, DOC4c, ADR 0166 ; KB-FAC-52 et KB-FAC-60
 * corrigés) : le profil et le centre sont ceux de {@code POST /facturation/synthese/print} (administrateur ou secrétaire ; le médecin seul
 * est refusé, {@link ProfilDeLaSynthese}), sur la voie par type comme sur la voie par modèle ; les montants globaux sont les totaux
 * enregistrés des factures de la période (ADR 0034), posés par le serveur.
 */
public final class RegleDImpressionDeLaSyntheseMensuelle implements RegleDImpressionDUnType {

    public static final String TYPE = "SYNTHESE_FACTURATION_MENSUELLE";

    private final SessionCourante session;
    private final SyntheseDeFacturation synthese;

    public RegleDImpressionDeLaSyntheseMensuelle(SessionCourante session, SyntheseDeFacturation synthese) {
        this.session = session;
        this.synthese = synthese;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void garder(UUID centreDemande) {
        if (!ProfilDeLaSynthese.autorise(session.roles())) {
            throw new AccessDeniedException("Profil insuffisant pour la synthèse de facturation");
        }
        session.centre(centreDemande);
    }

    @Override
    public boolean gardeAussiParLeModele() {
        return true;
    }

    /**
     * Un paramètre du même nom envoyé par le client est écarté par Documents (paramètres réservés, KB-DOC1-2). Période absente ou inversée :
     * rien n'est posé et le gabarit retombe sur la somme des lignes, comme avant. Période illisible : échec d'impression (500), reproduit.
     */
    @Override
    public Map<String, Object> donneesDuServeur(UUID centre, Map<String, String> parametres) {
        try {
            LocalDate debut = LocalDate.parse(parametres.get("PERIOD_START"));
            LocalDate fin = LocalDate.parse(parametres.get("PERIOD_END"));
            if (debut.isAfter(fin)) {
                return Map.of();
            }
            SyntheseMensuelle donnees = synthese.de(centre, debut, fin);
            // PL8 : totaux ET lignes du tableau, répartitions, indicateurs : le gabarit ne lit plus aucune table (ADR 0140 D3)
            return new HashMap<>(ModeleDeLaSynthese.parametresClasses(donnees));
        } catch (NullPointerException e) {
            // période absente : refusée par la suite comme avant
            return Map.of();
        } catch (DateTimeException e) {
            throw new IllegalStateException("Période illisible pour " + TYPE, e);
        }
    }
}
