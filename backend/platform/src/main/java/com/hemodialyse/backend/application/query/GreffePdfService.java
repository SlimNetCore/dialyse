package com.hemodialyse.backend.application.query;

import com.hemodialyse.backend.domain.medical.antecedent.port.AntecedentRepositoryPort;
import com.hemodialyse.backend.domain.medical.antecedent.valueobject.StatutClinique;
import com.hemodialyse.backend.domain.medical.greffe.aggregate.BilanPreGreffe;
import com.hemodialyse.backend.domain.medical.greffe.aggregate.DonneurVivant;
import com.hemodialyse.backend.domain.medical.greffe.aggregate.EtapeBilanPreGreffe;
import com.hemodialyse.backend.domain.medical.greffe.entity.DecisionRcp;
import com.hemodialyse.backend.domain.medical.greffe.port.BilanPreGreffeRepositoryPort;
import com.hemodialyse.backend.domain.medical.greffe.port.DonneurVivantRepositoryPort;
import com.hemodialyse.backend.domain.medical.greffe.port.EtapeBilanPreGreffeRepositoryPort;
import com.hemodialyse.backend.domain.medical.serologie.aggregate.Serologie;
import com.hemodialyse.backend.domain.medical.serologie.port.SerologieRepositoryPort;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Génère le dossier de synthèse "préparation à la greffe rénale" (receveur + donneurs vivants
 * candidats) en PDF, destiné au transfert vers le centre de transplantation. Même patron que
 * {@link PatientStatsQueryService#exportPdf} (openhtmltopdf : construction d'un HTML puis rendu).
 */
@Service
public class GreffePdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PatientRepositoryPort patientRepository;
    private final BilanPreGreffeRepositoryPort bilanRepository;
    private final EtapeBilanPreGreffeRepositoryPort etapeRepository;
    private final SerologieRepositoryPort serologieRepository;
    private final AntecedentRepositoryPort antecedentRepository;
    private final DonneurVivantRepositoryPort donneurRepository;
    private final com.hemodialyse.backend.infrastructure.reporting.DocumentIdentityProvider identity;

    public GreffePdfService(PatientRepositoryPort patientRepository, BilanPreGreffeRepositoryPort bilanRepository,
                            EtapeBilanPreGreffeRepositoryPort etapeRepository,
                            SerologieRepositoryPort serologieRepository,
                            AntecedentRepositoryPort antecedentRepository,
                            DonneurVivantRepositoryPort donneurRepository,
                            com.hemodialyse.backend.infrastructure.reporting.DocumentIdentityProvider identity) {
        this.patientRepository = patientRepository;
        this.bilanRepository = bilanRepository;
        this.etapeRepository = etapeRepository;
        this.serologieRepository = serologieRepository;
        this.antecedentRepository = antecedentRepository;
        this.donneurRepository = donneurRepository;
        this.identity = identity;
    }

    public byte[] exportPdf(CenterId centerId, UUID patientId) {
        Patient patient = patientRepository.findById(PatientId.of(patientId), centerId).orElse(null);
        BilanPreGreffe bilan = bilanRepository.findByPatientId(patientId, centerId).orElse(null);
        List<EtapeBilanPreGreffe> etapes = etapeRepository.findByPatientId(patientId, centerId).stream()
                .sorted(Comparator.comparing(e -> e.getCategorie().name()))
                .toList();
        List<Serologie> serologies = serologieRepository.findByPatientId(patientId, centerId);
        List<com.hemodialyse.backend.domain.medical.antecedent.aggregate.Antecedent> antecedents =
                antecedentRepository.findByPatientId(patientId, centerId).stream()
                        .filter(a -> a.getStatutClinique() == StatutClinique.ACTIF)
                        .toList();
        List<DonneurVivant> donneurs = donneurRepository.findByPatientId(patientId, centerId);

        String html = identity.decorateHtml(buildHtml(patient, bilan, etapes, serologies, antecedents, donneurs),
                centerId.value());
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

    private String buildHtml(Patient patient, BilanPreGreffe bilan, List<EtapeBilanPreGreffe> etapes,
                             List<Serologie> serologies,
                             List<com.hemodialyse.backend.domain.medical.antecedent.aggregate.Antecedent> antecedents,
                             List<DonneurVivant> donneurs) {
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
            sb.append("<div class='kpi'>Statut : ").append(escapeHtml(bilan.getStatut().name())).append("</div>")
                    .append("<div class='kpi'>Groupe sanguin confirmé : ").append(escapeHtml(nullToDash(bilan.getGroupeSanguinConfirme()))).append("</div>")
                    .append("<div class='kpi'>Typage HLA : ").append(escapeHtml(nullToDash(bilan.getTypageHla()))).append("</div>")
                    .append("<div class='kpi'>PRA classe I / II : ")
                    .append(nullToDash(bilan.getPraClasseI())).append(" % / ")
                    .append(nullToDash(bilan.getPraClasseII())).append(" %</div>")
                    .append("<div class='kpi'>Contre-indications : ").append(escapeHtml(nullToDash(bilan.getContreIndications()))).append("</div>")
                    .append("<div class='kpi'>Conclusion néphrologue : ").append(escapeHtml(nullToDash(bilan.getConclusionNephrologue()))).append("</div>");

            sb.append("<h2>Décisions de RCP</h2>");
            if (bilan.getDecisionsRcp().isEmpty()) {
                sb.append("<p>Aucune décision enregistrée.</p>");
            } else {
                sb.append("<table><thead><tr><th>Date</th><th>Avis</th><th>Compte-rendu</th><th>Prochaine revue</th></tr></thead><tbody>");
                for (DecisionRcp d : bilan.getDecisionsRcp()) {
                    sb.append("<tr><td>").append(formatDate(d.getDateReunion()))
                            .append("</td><td>").append(escapeHtml(d.getAvis().name()))
                            .append("</td><td>").append(escapeHtml(nullToDash(d.getCompteRendu())))
                            .append("</td><td>").append(formatDate(d.getProchaineDateRevue()))
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
            for (EtapeBilanPreGreffe etape : etapes) {
                sb.append("<tr><td>").append(escapeHtml(etape.getCategorie().name()))
                        .append("</td><td>").append(escapeHtml(etape.getLibelle()))
                        .append("</td><td>").append(escapeHtml(etape.getStatut().name()))
                        .append("</td><td>").append(formatDate(etape.getDateRealisation()))
                        .append("</td><td>").append(escapeHtml(nullToDash(etape.getResultat())))
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
                sb.append("<tr><td>").append(escapeHtml(s.getMarqueur().name()))
                        .append("</td><td>").append(escapeHtml(s.getResultat().name()))
                        .append("</td><td>").append(formatDate(s.getDatePrelevement()))
                        .append("</td></tr>");
            }
            sb.append("</tbody></table>");
        }

        sb.append("<h2>Antécédents actifs</h2>");
        if (antecedents.isEmpty()) {
            sb.append("<p>Aucun antécédent actif.</p>");
        } else {
            sb.append("<table><thead><tr><th>Diagnostic</th></tr></thead><tbody>");
            for (var a : antecedents) {
                String libelle = a.getDiagnostic() != null ? a.getDiagnostic().display() : a.getLibelleLibre();
                sb.append("<tr><td>").append(escapeHtml(nullToDash(libelle))).append("</td></tr>");
            }
            sb.append("</tbody></table>");
        }

        sb.append("<h2>Donneurs vivants candidats</h2>");
        if (donneurs.isEmpty()) {
            sb.append("<p>Aucun donneur candidat enregistré.</p>");
        } else {
            sb.append("<table><thead><tr><th>Nom</th><th>Lien de parenté</th><th>Groupe sanguin</th>"
                    + "<th>Statut</th><th>Crossmatch</th></tr></thead><tbody>");
            for (DonneurVivant d : donneurs) {
                sb.append("<tr><td>").append(escapeHtml(d.getNom() + " " + nullToDash(d.getPrenom())))
                        .append("</td><td>").append(escapeHtml(d.getLienParente().name()))
                        .append("</td><td>").append(escapeHtml(nullToDash(d.getGroupeSanguin())))
                        .append("</td><td>").append(escapeHtml(d.getStatutBilan().name()))
                        .append("</td><td>").append(escapeHtml(d.getCrossmatchResultat().name()))
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
