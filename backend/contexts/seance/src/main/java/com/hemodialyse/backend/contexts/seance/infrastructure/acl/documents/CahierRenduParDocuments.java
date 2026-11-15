package com.hemodialyse.backend.contexts.seance.infrastructure.acl.documents;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.DocumentRendu;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.RenduDeDocument;
import com.hemodialyse.backend.contexts.seance.application.CahierImprime;
import com.hemodialyse.backend.contexts.seance.application.DemandeDeCahier;
import com.hemodialyse.backend.contexts.seance.application.LigneDuCahier;
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
 * <p>DOC5a (ADR 0193) : le gabarit n'embarque plus de requête ; la Séance lui fournit ses lignes ({@code LIGNES}, un enregistrement par
 * séance et consommable). Un modèle personnalisé stocké qui porte encore l'ancienne requête reçoit ces mêmes lignes (la source de données
 * prime sur la requête) : mêmes noms de champs.
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
        p.put("LIGNES", demande.lignes().stream().map(CahierRenduParDocuments::champs).toList());
        return p;
    }

    /**
     * Une ligne du cahier traduite en champs du gabarit (DOC5a, ADR 0193) : un nom de champ par valeur, dans les types que le gabarit déclare
     * ({@code java.sql.Date} pour les dates, {@code BigDecimal} pour les montants, texte sinon).
     */
    public static Map<String, Object> champs(LigneDuCahier l) {
        Map<String, Object> c = new HashMap<>();
        c.put("SEANCE_ID", l.seance());
        c.put("DATE_SEANCE", date(l.jour()));
        c.put("STATUT", l.statut());
        c.put("FORFAIT_CODE", l.forfaitCode());
        c.put("FORFAIT_NOM", l.forfaitNom());
        c.put("FORFAIT_PRIX", l.forfaitPrix());
        c.put("POIDS_AVANT", l.poidsAvant());
        c.put("POIDS_APRES", l.poidsApres());
        c.put("TA_AVANT", l.tensionAvant());
        c.put("TA_APRES", l.tensionApres());
        c.put("DUREE", l.duree());
        c.put("DEBIT_SANG", l.debitSang());
        c.put("UF", l.ultrafiltration());
        c.put("DIALYSAT", l.dialysat());
        c.put("ANTICOAGULANT", l.anticoagulant());
        c.put("INCIDENTS", l.incidents());
        c.put("ARTICLE_CODE", l.articleCode());
        c.put("ARTICLE_LIBELLE", l.articleLibelle());
        c.put("ARTICLE_UNITE", l.articleUnite());
        c.put("QUANTITE", l.quantite());
        c.put("VALEUR_UNITAIRE", l.valeurUnitaire());
        c.put("TOTAL_VALORISE", l.totalValorise());
        c.put("NEPHROPATHIE", l.nephropathie());
        c.put("DATE_MISE_DIALYSE", date(l.miseEnDialyse()));
        c.put("HEPATITE_B", l.hepatiteB());
        c.put("HEPATITE_C", l.hepatiteC());
        c.put("OBSERVATION_GLOBALE", l.observationGlobale());
        c.put("CONCLUSION_DOSSIER", l.conclusionDuDossier());
        c.put("POIDS_SEC", l.poidsSec());
        c.put("EPO_LIGNE", l.epo());
        c.put("FER_LIGNE", l.fer());
        c.put("MED_PRESCRIPTION", l.prescription());
        c.put("MED_TOLERANCE", l.tolerance());
        c.put("MED_EXAMEN", l.examen());
        c.put("MED_RESULTATS", l.resultats());
        c.put("MED_AJUSTEMENTS", l.ajustements());
        c.put("MED_CONCLUSION", l.conclusionMedicale());
        return c;
    }

    private static Date date(java.time.LocalDate jour) {
        return jour == null ? null : Date.valueOf(jour);
    }

    static CahierImprime.Format format(String typeMime) {
        if (typeMime != null && typeMime.contains(TYPE_TABLEUR)) return CahierImprime.Format.EXCEL;
        if (typeMime != null && typeMime.startsWith("text/html")) return CahierImprime.Format.HTML;
        return CahierImprime.Format.PDF;
    }
}
