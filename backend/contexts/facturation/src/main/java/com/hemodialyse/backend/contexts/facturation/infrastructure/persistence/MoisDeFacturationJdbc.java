package com.hemodialyse.backend.contexts.facturation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.EntreeDuJournal;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDeFacturationRepository;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Tables {@code periodes_facturation} (une ligne par centre et par mois) et {@code periodes_facturation_journal} (append-only).
 * <p>
 * Le verrou est celui de la ligne du mois : {@code SELECT ... FOR UPDATE}, gardé jusqu'à la fin de la transaction (comme la
 * séquence de numérotation). La ligne est d'abord créée ouverte si elle manque ({@code ON CONFLICT DO NOTHING} sur PostgreSQL,
 * {@code WHERE NOT EXISTS} sur H2), puis verrouillée : l'émission d'un mois et sa clôture ne peuvent donc jamais s'entrelacer.
 */
public class MoisDeFacturationJdbc implements MoisDeFacturationRepository {

    private static final String COLONNES = "annee, mois, statut, cloturee_par, cloturee_le, rouverte_par, rouverte_le, motif_reouverture";
    private static final String CREER_SANS_CONFLIT = "INSERT INTO periodes_facturation (center_id, annee, mois, statut) "
            + "VALUES (?, ?, ?, 'OUVERT') ON CONFLICT (center_id, annee, mois) DO NOTHING";
    private static final String CREER_SI_ABSENTE = "INSERT INTO periodes_facturation (center_id, annee, mois, statut) "
            + "SELECT ?, ?, ?, 'OUVERT' WHERE NOT EXISTS (SELECT 1 FROM periodes_facturation WHERE center_id = ? AND annee = ? AND mois = ?)";

    private final JdbcTemplate jdbc;

    public MoisDeFacturationJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Instant instant(ResultSet rs, String colonne) throws SQLException {
        OffsetDateTime valeur = rs.getObject(colonne, OffsetDateTime.class);
        return valeur == null ? null : valeur.toInstant();
    }

    private static OffsetDateTime horodatage(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private RowMapper<MoisDeFacturation> ligne(CentreId centre) {
        return (rs, i) -> MoisDeFacturation.reconstituer(centre, YearMonth.of(rs.getInt("annee"), rs.getInt("mois")),
                "CLOTURE".equals(rs.getString("statut")), instant(rs, "cloturee_le"), rs.getObject("cloturee_par", UUID.class),
                instant(rs, "rouverte_le"), rs.getObject("rouverte_par", UUID.class), rs.getString("motif_reouverture"));
    }

    @Override
    public MoisDeFacturation duMois(CentreId centre, YearMonth mois) {
        return jdbc.query("SELECT " + COLONNES + " FROM periodes_facturation WHERE center_id = ? AND annee = ? AND mois = ?",
                ligne(centre), centre.valeur(), mois.getYear(), mois.getMonthValue()).stream().findFirst()
                .orElseGet(() -> MoisDeFacturation.ouvert(centre, mois));
    }

    @Override
    public MoisDeFacturation verrouiller(CentreId centre, YearMonth mois) {
        String selection = "SELECT " + COLONNES + " FROM periodes_facturation WHERE center_id = ? AND annee = ? AND mois = ? FOR UPDATE";
        List<MoisDeFacturation> trouves = jdbc.query(selection, ligne(centre), centre.valeur(), mois.getYear(), mois.getMonthValue());
        if (!trouves.isEmpty()) {
            return trouves.getFirst();
        }
        if (estPostgreSql()) {
            jdbc.update(CREER_SANS_CONFLIT, centre.valeur(), mois.getYear(), mois.getMonthValue());
        } else {
            jdbc.update(CREER_SI_ABSENTE, centre.valeur(), mois.getYear(), mois.getMonthValue(), centre.valeur(), mois.getYear(),
                    mois.getMonthValue());
        }
        return jdbc.query(selection, ligne(centre), centre.valeur(), mois.getYear(), mois.getMonthValue()).getFirst();
    }

    @Override
    public List<MoisDeFacturation> deLAnnee(CentreId centre, int annee) {
        return jdbc.query("SELECT " + COLONNES + " FROM periodes_facturation WHERE center_id = ? AND annee = ? ORDER BY mois",
                ligne(centre), centre.valeur(), annee);
    }

    @Override
    public void enregistrer(MoisDeFacturation mois, EntreeDuJournal entree) {
        CentreId centre = mois.centre();
        int modifiees = jdbc.update("UPDATE periodes_facturation SET statut = ?, cloturee_par = ?, cloturee_le = ?, rouverte_par = ?, "
                        + "rouverte_le = ?, motif_reouverture = ? WHERE center_id = ? AND annee = ? AND mois = ?",
                mois.estClos() ? "CLOTURE" : "OUVERT", mois.closPar(), horodatage(mois.closLe()), mois.rouvertPar(),
                horodatage(mois.rouvertLe()), mois.motifDeReouverture(), centre.valeur(), mois.mois().getYear(),
                mois.mois().getMonthValue());
        if (modifiees == 0) {
            jdbc.update("INSERT INTO periodes_facturation (center_id, annee, mois, statut, cloturee_par, cloturee_le, rouverte_par, "
                            + "rouverte_le, motif_reouverture) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", centre.valeur(),
                    mois.mois().getYear(), mois.mois().getMonthValue(), mois.estClos() ? "CLOTURE" : "OUVERT", mois.closPar(),
                    horodatage(mois.closLe()), mois.rouvertPar(), horodatage(mois.rouvertLe()), mois.motifDeReouverture());
        }
        jdbc.update("INSERT INTO periodes_facturation_journal (center_id, annee, mois, type_acte, effectue_par, effectue_le, motif, "
                        + "seances_eligibles_restantes) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", centre.valeur(), entree.mois().getYear(),
                entree.mois().getMonthValue(), entree.type().name(), entree.par(), horodatage(entree.le()), entree.motif(),
                entree.seancesEligiblesRestantes());
    }

    @Override
    public List<EntreeDuJournal> journal(CentreId centre, YearMonth mois) {
        return jdbc.query("SELECT type_acte, effectue_par, effectue_le, motif, seances_eligibles_restantes "
                        + "FROM periodes_facturation_journal WHERE center_id = ? AND annee = ? AND mois = ? ORDER BY id",
                (rs, i) -> new EntreeDuJournal(EntreeDuJournal.Type.valueOf(rs.getString("type_acte")), centre, mois,
                        instant(rs, "effectue_le"), rs.getObject("effectue_par", UUID.class), rs.getString("motif"),
                        (Integer) rs.getObject("seances_eligibles_restantes")),
                centre.valeur(), mois.getYear(), mois.getMonthValue());
    }

    private boolean estPostgreSql() {
        return Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection ->
                "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())));
    }
}
