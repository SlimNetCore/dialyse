package com.hemodialyse.backend.application.query;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DossierDeGreffePublie;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DossierDeGreffePublie.AntecedentActif;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DossierDeGreffePublie.Bilan;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DossierDeGreffePublie.DecisionDeRcp;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DossierDeGreffePublie.Donneur;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DossierDeGreffePublie.DossierDeGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DossierDeGreffePublie.Etape;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DossierDeGreffePublie.Serologie;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Génère le dossier de synthèse "préparation à la greffe rénale" (receveur + donneurs vivants candidats) en PDF, destiné au transfert vers
 * le centre de transplantation. Même patron que {@link PatientStatsQueryService#exportPdf} (openhtmltopdf : construction d'un HTML puis
 * rendu). DM9 (ADR 0101) : le contenu médical vient du langage publié du Dossier médical ({@link DossierDeGreffePublie}, qui garde le patient :
 * 404 par l'advice du contexte) ; la plateforme ne garde que l'identité du patient (fiche) et la présentation. Même HTML qu'avant.
 */
@Service
public class GreffePdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PatientRepositoryPort patientRepository;
    private final DossierDeGreffePublie dossiers;
    private final com.hemodialyse.backend.infrastructure.reporting.DocumentIdentityProvider identity;

    public GreffePdfService(PatientRepositoryPort patientRepository, DossierDeGreffePublie dossiers,
                            com.hemodialyse.backend.infrastructure.reporting.DocumentIdentityProvider identity) {
        this.patientRepository = patientRepository;
        this.dossiers = dossiers;
        this.identity = identity;
    }

    public byte[] exportPdf(CenterId centerId, UUID patientId) {
        DossierDeGreffe dossier = dossiers.duPatient(centerId.value(), patientId);
        Patient patient = patientRepository.findById(PatientId.of(patientId), centerId).orElse(null);

        String html = identity.decorateHtml(buildHtml(patient, dossier), centerId.value());
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de générer le PDF du dossier de greffe", e);
        }
    }

    private String buildHtml(Patient patient, DossierDeGreffe dossier) {
        Bilan bilan = dossier.bilan();
        List<Etape> etapes = dossier.etapes();
        List<Serologie> serologies = dossier.serologies();
        List<AntecedentActif> antecedents = dossier.antecedentsActifs();
        List<Donneur> donneurs = dossier.donneurs();
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><meta charset='UTF-8'/><style>")
                .append("body{font-family:Arial,sans-serif;font-size:12px;color:#111;margin:24px;}")
                .append("h1{font-size:20px;margin:0 0 8px 0;}h2{font-size:16px;margin:20px 0 8px 0;}")
                .append(".muted{color:#666;font-size:11px;margin-bottom:12px;}")
                .append("table{width:100%;border-collapse:collapse;margin-top:8px;}")
                .append("th,td{border:1px solid #ddd;padding:6px;text-align:left;}")
                .append("th{background:#f5f7fb;}")
                .append("</style></head><body>");

        sb.append("<h1>Dossier de préparation à la greffe rénale</h1>");
        if (patient != null) {
            sb.append("<div class='muted'>Patient : ").append(escapeHtml(patient.getNom() + " " + patient.getPrenom()))
                    .append(" (").append(escapeHtml(patient.getCodePatient())).append(")")
                    .append(" — Groupe sanguin déclaré : ").append(escapeHtml(nullToDash(patient.getGroupeSanguin())))
                    .append("</div>");
        }

        sb.append("<h2>Éligibilité</h2>");
        if (bilan != null) {
            sb.append("<div class='kpi'>Statut : ").append(escapeHtml(bilan.statut())).append("</div>")
                    .append("<div class='kpi'>Groupe sanguin confirmé : ").append(escapeHtml(nullToDash(bilan.groupeSanguinConfirme()))).append("</div>")
                    .append("<div class='kpi'>Typage HLA : ").append(escapeHtml(nullToDash(bilan.typageHla()))).append("</div>")
                    .append("<div class='kpi'>PRA classe I / II : ")
                    .append(nullToDash(bilan.praClasseI())).append(" % / ")
                    .append(nullToDash(bilan.praClasseII())).append(" %</div>")
                    .append("<div class='kpi'>Contre-indications : ").append(escapeHtml(nullToDash(bilan.contreIndications()))).append("</div>")
                    .append("<div class='kpi'>Conclusion néphrologue : ").append(escapeHtml(nullToDash(bilan.conclusionNephrologue()))).append("</div>");

            sb.append("<h2>Décisions de RCP</h2>");
            if (bilan.decisions().isEmpty()) {
                sb.append("<p>Aucune décision enregistrée.</p>");
            } else {
                sb.append("<table><thead><tr><th>Date</th><th>Avis</th><th>Compte-rendu</th><th>Prochaine revue</th></tr></thead><tbody>");
                for (DecisionDeRcp d : bilan.decisions()) {
                    sb.append("<tr><td>").append(formatDate(d.dateReunion()))
                            .append("</td><td>").append(escapeHtml(d.avis()))
                            .append("</td><td>").append(escapeHtml(nullToDash(d.compteRendu())))
                            .append("</td><td>").append(formatDate(d.prochaineDateRevue()))
                            .append("</td></tr>");
                }
                sb.append("</tbody></table>");
            }
        } else {
            sb.append("<p>Bilan non débuté.</p>");
        }

        sb.append("<h2>Checklist du bilan pré-greffe</h2>");
        if (etapes.isEmpty()) {
            sb.append("<p>Aucune étape enregistrée.</p>");
        } else {
            sb.append("<table><thead><tr><th>Catégorie</th><th>Étape</th><th>Statut</th><th>Date réalisation</th><th>Résultat</th></tr></thead><tbody>");
            for (Etape etape : etapes) {
                sb.append("<tr><td>").append(escapeHtml(etape.categorie()))
                        .append("</td><td>").append(escapeHtml(etape.libelle()))
                        .append("</td><td>").append(escapeHtml(etape.statut()))
                        .append("</td><td>").append(formatDate(etape.dateRealisation()))
                        .append("</td><td>").append(escapeHtml(nullToDash(etape.resultat())))
                        .append("</td></tr>");
            }
            sb.append("</tbody></table>");
        }

        sb.append("<h2>Sérologies</h2>");
        if (serologies.isEmpty()) {
            sb.append("<p>Aucune sérologie enregistrée.</p>");
        } else {
            sb.append("<table><thead><tr><th>Marqueur</th><th>Résultat</th><th>Date prélèvement</th></tr></thead><tbody>");
            for (Serologie s : serologies) {
                sb.append("<tr><td>").append(escapeHtml(s.marqueur()))
                        .append("</td><td>").append(escapeHtml(s.resultat()))
                        .append("</td><td>").append(formatDate(s.datePrelevement()))
                        .append("</td></tr>");
            }
            sb.append("</tbody></table>");
        }

        sb.append("<h2>Antécédents actifs</h2>");
        if (antecedents.isEmpty()) {
            sb.append("<p>Aucun antécédent actif.</p>");
        } else {
            sb.append("<table><thead><tr><th>Diagnostic</th></tr></thead><tbody>");
            for (AntecedentActif a : antecedents) {
                sb.append("<tr><td>").append(escapeHtml(nullToDash(a.libelle()))).append("</td></tr>");
            }
            sb.append("</tbody></table>");
        }

        sb.append("<h2>Donneurs vivants candidats</h2>");
        if (donneurs.isEmpty()) {
            sb.append("<p>Aucun donneur candidat enregistré.</p>");
        } else {
            sb.append("<table><thead><tr><th>Nom</th><th>Lien de parenté</th><th>Groupe sanguin</th>"
                    + "<th>Statut</th><th>Crossmatch</th></tr></thead><tbody>");
            for (Donneur d : donneurs) {
                sb.append("<tr><td>").append(escapeHtml(d.nom() + " " + nullToDash(d.prenom())))
                        .append("</td><td>").append(escapeHtml(d.lienDeParente()))
                        .append("</td><td>").append(escapeHtml(nullToDash(d.groupeSanguin())))
                        .append("</td><td>").append(escapeHtml(d.statutBilan()))
                        .append("</td><td>").append(escapeHtml(d.crossmatch()))
                        .append("</td></tr>");
            }
            sb.append("</tbody></table>");
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    private String formatDate(java.time.LocalDate date) {
        return date == null ? "-" : date.format(DATE_FMT);
    }

    private String nullToDash(Object value) {
        return value == null ? "-" : value.toString();
    }

    private String escapeHtml(String raw) {
        if (raw == null) return "-";
        return raw
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
