package com.hemodialyse.backend.contexts.absencespatients.infrastructure.persistence;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.domain.EntreeDuJournalDuMois;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.UtilisateurId;
import com.hemodialyse.backend.contexts.absencespatients.domain.MoisAbsences;
import com.hemodialyse.backend.contexts.absencespatients.domain.MoisAbsencesRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Tables {@code mois_absences} (une ligne par centre et par mois) et {@code mois_absences_journal} (append-only), migration V44.
 * <p>
 * Le verrou est celui de la ligne du mois : {@code SELECT ... FOR UPDATE}, gardé jusqu'à la fin de la transaction. La ligne est
 * d'abord créée ouverte si elle manque ({@code ON CONFLICT DO NOTHING} sur PostgreSQL, {@code WHERE NOT EXISTS} sur H2, avec reprise
 * si deux créations se croisent), puis verrouillée : l'écriture d'une absence du mois et sa clôture ne peuvent jamais s'entrelacer.
 * Un verrou qu'on ne peut pas prendre dans le délai de la transaction est une écriture concurrente (409).
 */
public class MoisAbsencesJdbc implements MoisAbsencesRepository {

    private static final String COLONNES = "annee, mois, statut, cloture_par, cloture_le, rouvert_par, rouvert_le, motif_reouverture";
    private static final String CREER_SANS_CONFLIT = "INSERT INTO mois_absences (center_id, annee, mois, statut) "
            + "VALUES (?, ?, ?, 'OUVERT') ON CONFLICT (center_id, annee, mois) DO NOTHING";
    private static final String CREER_SI_ABSENTE = "INSERT INTO mois_absences (center_id, annee, mois, statut) "
            + "SELECT ?, ?, ?, 'OUVERT' WHERE NOT EXISTS (SELECT 1 FROM mois_absences WHERE center_id = ? AND annee = ? AND mois = ?)";

    private final JdbcTemplate jdbc;
    /**
     * Produit de la base, lu une seule fois (les métadonnées de connexion coûtent cher à chaque mois créé).
     */
    private volatile Boolean postgres;

    public MoisAbsencesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Instant instant(ResultSet rs, String colonne) throws SQLException {
        OffsetDateTime valeur = rs.getObject(colonne, OffsetDateTime.class);
        return valeur == null ? null : valeur.toInstant();
    }

    private static OffsetDateTime horodatage(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static UtilisateurId utilisateur(UUID valeur) {
        return valeur == null ? null : new UtilisateurId(valeur);
    }

    private static UUID valeur(UtilisateurId utilisateur) {
        return utilisateur == null ? null : utilisateur.valeur();
    }

    private RowMapper<MoisAbsences> ligne(CentreId centre) {
        return (rs, i) -> MoisAbsences.reconstituer(centre, YearMonth.of(rs.getInt("annee"), rs.getInt("mois")),
                "CLOTURE".equals(rs.getString("statut")), instant(rs, "cloture_le"),
                utilisateur(rs.getObject("cloture_par", UUID.class)), instant(rs, "rouvert_le"),
                utilisateur(rs.getObject("rouvert_par", UUID.class)), rs.getString("motif_reouverture"));
    }

    @Override
    public MoisAbsences duMois(CentreId centre, YearMonth mois) {
        return jdbc.query("SELECT " + COLONNES + " FROM mois_absences WHERE center_id = ? AND annee = ? AND mois = ?",
                        ligne(centre), centre.valeur(), mois.getYear(), mois.getMonthValue()).stream().findFirst()
                .orElseGet(() -> MoisAbsences.ouvert(centre, mois));
    }

    @Override
    public boolean estOuvert(CentreId centre, YearMonth mois) {
        return !duMois(centre, mois).estClos();
    }

    @Override
    public boolean estOuvertSousVerrou(CentreId centre, YearMonth mois) {
        return !verrouiller(centre, mois).estClos();
    }

    @Override
    public List<YearMonth> moisClos(CentreId centre) {
        return jdbc.query("SELECT annee, mois FROM mois_absences WHERE center_id = ? AND statut = 'CLOTURE' ORDER BY annee, mois",
                (rs, i) -> YearMonth.of(rs.getInt("annee"), rs.getInt("mois")), centre.valeur());
    }

    @Override
    public MoisAbsences verrouiller(CentreId centre, YearMonth mois) {
        return RefusDeVerrou.verrouillant(() -> {
            List<MoisAbsences> trouves = selectionnerPourMiseAJour(centre, mois);
            if (!trouves.isEmpty()) {
                return trouves.getFirst();
            }
            creerOuverte(centre, mois);
            return selectionnerPourMiseAJour(centre, mois).getFirst();
        });
    }

    private List<MoisAbsences> selectionnerPourMiseAJour(CentreId centre, YearMonth mois) {
        return jdbc.query("SELECT " + COLONNES + " FROM mois_absences WHERE center_id = ? AND annee = ? AND mois = ? FOR UPDATE",
                ligne(centre), centre.valeur(), mois.getYear(), mois.getMonthValue());
    }

    private void creerOuverte(CentreId centre, YearMonth mois) {
        try {
            if (estPostgreSql()) {
                jdbc.update(CREER_SANS_CONFLIT, centre.valeur(), mois.getYear(), mois.getMonthValue());
            } else {
                jdbc.update(CREER_SI_ABSENTE, centre.valeur(), mois.getYear(), mois.getMonthValue(), centre.valeur(),
                        mois.getYear(), mois.getMonthValue());
            }
        } catch (DuplicateKeyException creeeEntreTemps) {
            // H2 : une autre transaction vient de créer la même ligne ; elle est maintenant à verrouiller
        }
    }

    @Override
    public List<MoisAbsences> deLAnnee(CentreId centre, int annee) {
        return jdbc.query("SELECT " + COLONNES + " FROM mois_absences WHERE center_id = ? AND annee = ? ORDER BY mois",
                ligne(centre), centre.valeur(), annee);
    }

    @Override
    public void enregistrer(MoisAbsences mois, EntreeDuJournalDuMois entree) {
        CentreId centre = mois.centre();
        int modifiees = jdbc.update("UPDATE mois_absences SET statut = ?, cloture_par = ?, cloture_le = ?, rouvert_par = ?, "
                        + "rouvert_le = ?, motif_reouverture = ? WHERE center_id = ? AND annee = ? AND mois = ?",
                mois.estClos() ? "CLOTURE" : "OUVERT", valeur(mois.closPar()), horodatage(mois.closLe()),
                valeur(mois.rouvertPar()), horodatage(mois.rouvertLe()), mois.motifDeReouverture(), centre.valeur(),
                mois.mois().getYear(), mois.mois().getMonthValue());
        if (modifiees == 0) {
            jdbc.update("INSERT INTO mois_absences (center_id, annee, mois, statut, cloture_par, cloture_le, rouvert_par, "
                            + "rouvert_le, motif_reouverture) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", centre.valeur(),
                    mois.mois().getYear(), mois.mois().getMonthValue(), mois.estClos() ? "CLOTURE" : "OUVERT",
                    valeur(mois.closPar()), horodatage(mois.closLe()), valeur(mois.rouvertPar()), horodatage(mois.rouvertLe()),
                    mois.motifDeReouverture());
        }
        jdbc.update("INSERT INTO mois_absences_journal (center_id, annee, mois, type_acte, effectue_par, effectue_le, motif, "
                        + "a_qualifier_restantes, en_retard) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", centre.valeur(),
                entree.mois().getYear(), entree.mois().getMonthValue(), entree.type().name(), valeur(entree.par()),
                horodatage(entree.le()), entree.motif(), entree.aQualifierRestantes(), entree.enRetard());
    }

    @Override
    public List<EntreeDuJournalDuMois> journal(CentreId centre, YearMonth mois) {
        return jdbc.query("SELECT type_acte, effectue_par, effectue_le, motif, a_qualifier_restantes, en_retard "
                        + "FROM mois_absences_journal WHERE center_id = ? AND annee = ? AND mois = ? ORDER BY id",
                (rs, i) -> new EntreeDuJournalDuMois(EntreeDuJournalDuMois.Type.valueOf(rs.getString("type_acte")), centre, mois,
                        instant(rs, "effectue_le"), utilisateur(rs.getObject("effectue_par", UUID.class)), rs.getString("motif"),
                        (Long) rs.getObject("a_qualifier_restantes"), (Long) rs.getObject("en_retard")),
                centre.valeur(), mois.getYear(), mois.getMonthValue());
    }

    private boolean estPostgreSql() {
        Boolean connu = postgres;
        if (connu == null) {
            connu = Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection ->
                    "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())));
            postgres = connu;
        }
        return connu;
    }
}
