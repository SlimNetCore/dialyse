package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.domain.AvisRcp;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffeIllisible;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilansPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DecisionRcp;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUnBilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDuBilanPreGreffe;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.VerrousJdbc.horodatage;
import static com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.VerrousJdbc.instant;

/**
 * Bilans pré-greffe sur les tables réelles {@code bilans_pre_greffe} et {@code decisions_rcp} (aucune colonne ni contrainte ajoutée).
 * Un seul bilan par patient est protégé par un verrou consultatif d'ouverture sur PostgreSQL (écart décidé KB-MED-90). Lecture robuste
 * d'un doublon existant : le plus ancien est retenu (création puis identifiant), les autres sont ignorés et leur identifiant SEUL est
 * journalisé en WARN (repérage SELECT seule : {@code verifications-production.md} section 51) ; jamais de 500. Le statut illisible du
 * bilan retenu lève {@link BilanPreGreffeIllisible} (422) ; une décision dont l'avis est illisible est ignorée (WARN, identifiant seul) et
 * n'est JAMAIS supprimée : l'enregistrement n'insère que les décisions nouvelles. Verrou de ligne {@code SELECT ... FOR UPDATE} : un
 * verrou non obtenu dans le délai (55P03) devient {@link EcritureConcurrenteSurUnBilanPreGreffe}.
 */
public class BilansPreGreffeJdbc implements BilansPreGreffe {
    private static final Logger JOURNAL = LoggerFactory.getLogger(BilansPreGreffeJdbc.class);
    private static final String COLONNES = "id, patient_id, center_id, statut, date_debut_bilan, date_inscription_liste_attente, date_greffe, "
            + "groupe_sanguin_confirme, typage_hla, pra_classe_i, pra_classe_ii, contre_indications, conclusion_nephrologue, created_at, updated_at";
    private static final String RECHERCHE = "SELECT " + COLONNES + " FROM bilans_pre_greffe WHERE patient_id = ? AND center_id = ? "
            + "ORDER BY created_at, id";

    private final JdbcTemplate jdbc;
    private final VerrousJdbc verrous;

    public BilansPreGreffeJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.verrous = new VerrousJdbc(jdbc);
    }

    @Override
    public Optional<BilanPreGreffe> trouver(CentreId centre, PatientId patient) {
        return lire(RECHERCHE, centre, patient);
    }

    @Override
    public Optional<BilanPreGreffe> trouverEnVerrouillant(CentreId centre, PatientId patient) {
        return VerrousJdbc.verrouille(() -> lire(RECHERCHE + " FOR UPDATE", centre, patient), EcritureConcurrenteSurUnBilanPreGreffe::new);
    }

    private Optional<BilanPreGreffe> lire(String sql, CentreId centre, PatientId patient) {
        List<Object[]> lignes = jdbc.query(sql, (rs, i) -> brut(rs), patient.valeur(), centre.valeur());
        if (lignes.isEmpty()) return Optional.empty();
        for (int i = 1; i < lignes.size(); i++) {
            JOURNAL.warn("Bilan pré-greffe {} ignoré : doublon d'un bilan plus ancien du même patient (donnée incohérente)", lignes.get(i)[0]);
        }
        return Optional.of(reconstituer(lignes.get(0), centre, patient));
    }

    /** Les colonnes brutes, pour que la décision de lire ou d'écarter reste dans l'adapter. */
    private static Object[] brut(ResultSet rs) throws SQLException {
        return new Object[]{rs.getObject("id", UUID.class), rs.getString("statut"), rs.getObject("date_debut_bilan", LocalDate.class),
                rs.getObject("date_inscription_liste_attente", LocalDate.class), rs.getObject("date_greffe", LocalDate.class),
                rs.getString("groupe_sanguin_confirme"), rs.getString("typage_hla"), rs.getBigDecimal("pra_classe_i"),
                rs.getBigDecimal("pra_classe_ii"), rs.getString("contre_indications"), rs.getString("conclusion_nephrologue"),
                instant(rs, "created_at"), instant(rs, "updated_at")};
    }

    private BilanPreGreffe reconstituer(Object[] c, CentreId centre, PatientId patient) {
        UUID id = (UUID) c[0];
        StatutDuBilanPreGreffe statut;
        try {
            statut = StatutDuBilanPreGreffe.valueOf((String) c[1]);
        } catch (IllegalArgumentException | NullPointerException e) {
            JOURNAL.warn("Bilan pré-greffe {} illisible : statut absent de l'énumération (donnée incohérente)", id);
            throw new BilanPreGreffeIllisible();
        }
        return BilanPreGreffe.reconstituer(id, patient, centre, statut, (LocalDate) c[2], (LocalDate) c[3], (LocalDate) c[4], (String) c[5],
                (String) c[6], (java.math.BigDecimal) c[7], (java.math.BigDecimal) c[8], (String) c[9], (String) c[10], decisions(id),
                (java.time.Instant) c[11], (java.time.Instant) c[12]);
    }

    private List<DecisionRcp> decisions(UUID bilan) {
        List<DecisionRcp> lues = new ArrayList<>();
        jdbc.query("SELECT id, date_reunion, avis, compte_rendu, prochaine_date_revue FROM decisions_rcp WHERE bilan_id = ? "
                + "ORDER BY date_reunion DESC, id", rs -> {
            UUID id = rs.getObject("id", UUID.class);
            try {
                lues.add(new DecisionRcp(id, rs.getObject("date_reunion", LocalDate.class), AvisRcp.valueOf(rs.getString("avis")),
                        rs.getString("compte_rendu"), rs.getObject("prochaine_date_revue", LocalDate.class)));
            } catch (IllegalArgumentException | NullPointerException e) {
                JOURNAL.warn("Décision de RCP {} ignorée : avis illisible en base (donnée incohérente)", id);
            }
        }, bilan);
        return lues;
    }

    @Override
    public void verrouillerLOuverture(CentreId centre, PatientId patient) {
        VerrousJdbc.verrouille(() -> {
            verrous.consultatif("bilan-pre-greffe|" + centre.valeur() + "|" + patient.valeur());
            return Boolean.TRUE;
        }, EcritureConcurrenteSurUnBilanPreGreffe::new);
    }

    @Override
    public void ouvrir(BilanPreGreffe b) {
        jdbc.update("INSERT INTO bilans_pre_greffe (" + COLONNES + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", b.id(), b.patient().valeur(),
                b.centre().valeur(), b.statut().name(), b.dateDebutBilan(), b.dateInscriptionListeAttente(), b.dateGreffe(),
                b.groupeSanguinConfirme(), b.typageHla(), b.praClasseI(), b.praClasseII(), b.contreIndications(), b.conclusionNephrologue(),
                horodatage(b.creeLe()), horodatage(b.modifieLe()));
    }

    @Override
    public void enregistrer(BilanPreGreffe b) {
        jdbc.update("UPDATE bilans_pre_greffe SET statut = ?, date_debut_bilan = ?, date_inscription_liste_attente = ?, date_greffe = ?, "
                        + "groupe_sanguin_confirme = ?, typage_hla = ?, pra_classe_i = ?, pra_classe_ii = ?, contre_indications = ?, "
                        + "conclusion_nephrologue = ?, updated_at = ? WHERE id = ?", b.statut().name(), b.dateDebutBilan(),
                b.dateInscriptionListeAttente(), b.dateGreffe(), b.groupeSanguinConfirme(), b.typageHla(), b.praClasseI(), b.praClasseII(),
                b.contreIndications(), b.conclusionNephrologue(), horodatage(b.modifieLe()), b.id());
        Set<UUID> presentes = new HashSet<>(jdbc.queryForList("SELECT id FROM decisions_rcp WHERE bilan_id = ?", UUID.class, b.id()));
        for (DecisionRcp d : b.decisions()) {
            if (presentes.contains(d.id())) continue;
            jdbc.update("INSERT INTO decisions_rcp (id, bilan_id, date_reunion, avis, compte_rendu, prochaine_date_revue) VALUES (?,?,?,?,?,?)",
                    d.id(), b.id(), d.dateReunion(), d.avis().name(), d.compteRendu(), d.prochaineDateRevue());
        }
    }
}
