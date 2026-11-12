package com.hemodialyse.backend.contexts.seance.infrastructure.acl.documents;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.DocumentRendu;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.RenduDeDocument;
import com.hemodialyse.backend.contexts.seance.application.CahierImprime;
import com.hemodialyse.backend.contexts.seance.application.DemandeDeCahier;
import com.hemodialyse.backend.contexts.seance.application.RenduDuCahier;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.sql.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * ACL vers Documents (documents.v1) : le cahier est rendu par le port {@link RenduDeDocument}, type {@value #TYPE_DE_DOCUMENT}, avec le
 * modèle actif du centre (format du modèle). Traduit la demande de la Séance en paramètres du modèle : patient, période, ligne
 * d'identité, édition et portée du volet médical ({@code VOLET_MEDICAL_VISIBLE}). Le gabarit lit lui-même les séances, volets,
 * consommables, forfait et prescription du centre (modèle personnalisable par centre, ADR 0099 décision 3) ; Documents pose le papier
 * en-tête. Les refus du rendu (modèle désactivé, génération impossible) remontent tels quels.
 */
public class CahierRenduParDocuments implements RenduDuCahier {

    public static final String TYPE_DE_DOCUMENT = "CAHIER_DIALYSE";

    private static final String TYPE_TABLEUR = "spreadsheetml";

    private final RenduDeDocument documents;

    public CahierRenduParDocuments(RenduDeDocument documents) {
        this.documents = documents;
    }

    @Override
    public CahierImprime rendre(CentreId centre, DemandeDeCahier demande) {
        DocumentRendu rendu = documents.rendre(centre.valeur(), TYPE_DE_DOCUMENT, parametres(demande), null);
        return new CahierImprime(rendu.octets(), format(rendu.typeMime()));
    }

    static Map<String, Object> parametres(DemandeDeCahier demande) {
        Map<String, Object> p = new HashMap<>();
        p.put("REPORT_LOCALE", Locale.FRANCE);
        p.put("PATIENT_ID", demande.patient().valeur().toString());
        p.put("DATE_DEBUT", Date.valueOf(demande.periode().du()));
        p.put("DATE_FIN", Date.valueOf(demande.periode().au()));
        p.put("PATIENT_LIGNE", demande.ligneDIdentite());
        p.put("EDITE_LE", demande.editeLe());
        p.put("EDITE_PAR", demande.editePar());
        p.put("VOLET_MEDICAL_VISIBLE", demande.voletMedicalVisible());
        return p;
    }

    static CahierImprime.Format format(String typeMime) {
        if (typeMime != null && typeMime.contains(TYPE_TABLEUR)) return CahierImprime.Format.EXCEL;
        if (typeMime != null && typeMime.startsWith("text/html")) return CahierImprime.Format.HTML;
        return CahierImprime.Format.PDF;
    }
}
