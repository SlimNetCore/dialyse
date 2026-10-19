package com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.AxeAnalytique;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.EcritureComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.EcritureComptableRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.LigneEcriture;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.NumeroDePiece;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.PageEcritures;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.StatutEcriture;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur SQL sur les tables historiques, sans modification de schéma.
 */
public final class EcritureComptableRepositoryJdbc implements EcritureComptableRepository {
    private static final String COLONNES = "e.id, e.center_id, e.journal_code, e.date_ecriture, e.date_piece, "
            + "e.numero_piece, e.libelle, e.statut, e.source_id, e.modele_id, e.updated_at, "
            + "l.id AS ligne_id, l.compte_scf, l.libelle_ligne, l.montant_debit, l.montant_credit, "
            + "l.tiers_id, l.axes_analytiques";

    private final JdbcTemplate jdbc;

    public EcritureComptableRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Instant instant(Object valeur) {
        if (valeur instanceof OffsetDateTime date) return date.toInstant();
        if (valeur instanceof java.sql.Timestamp date) return date.toInstant();
        if (valeur instanceof Instant instant) return instant;
        return null;
    }

    private static List<AxeAnalytique> axes(String brut) {
        if (brut == null || brut.isBlank()) return List.of();
        return Arrays.stream(brut.split(",", -1)).map(axe -> {
            int separateur = axe.indexOf(':');
            if (separateur < 0) return new AxeAnalytique(axe, axe);
            return new AxeAnalytique(axe.substring(0, separateur), axe.substring(separateur + 1));
        }).toList();
    }

    private static String serialiserAxes(List<AxeAnalytique> axes) {
        if (axes == null || axes.isEmpty()) return null;
        return axes.stream().map(a -> a.code() + ":" + a.libelle()).reduce((a, b) -> a + "," + b).orElse(null);
    }

    private static UUID uuid(Object valeur) {
        return valeur instanceof UUID id ? id : UUID.fromString(valeur.toString());
    }

    private static Filtres filtres(CentreId centre, LocalDate du, LocalDate au, CodeJournal journal,
                                   StatutEcriture statut) {
        StringBuilder where = new StringBuilder("WHERE e.center_id = ? AND e.date_ecriture BETWEEN ? AND ?");
        List<Object> parametres = new ArrayList<>(List.of(centre.valeur(), du, au));
        if (journal != null) {
            where.append(" AND e.journal_code = ?");
            parametres.add(journal.valeur());
        }
        if (statut != null) {
            where.append(" AND e.statut = ?");
            parametres.add(statut.name());
        }
        return new Filtres(where.toString(), parametres);
    }

    @Override
    public Optional<EcritureComptable> parId(CentreId centre, UUID id) {
        List<EcritureComptable> result = lire("WHERE e.center_id = ? AND e.id = ?", List.of(centre.valeur(), id),
                "ORDER BY l.id ASC");
        return result.stream().findFirst();
    }

    @Override
    public Optional<EcritureComptable> parSource(CentreId centre, UUID source) {
        List<EcritureComptable> result = lire("WHERE e.center_id = ? AND e.source_id = ?",
                List.of(centre.valeur(), source), "ORDER BY e.created_at ASC, e.id ASC");
        return result.stream().findFirst();
    }

    @Override
    public PageEcritures rechercher(CentreId centre, LocalDate du, LocalDate au, CodeJournal journal,
                                    StatutEcriture statut, int page, int taille) {
        if (page < 0) throw new IllegalArgumentException("Page index must not be less than zero");
        if (taille < 1) throw new IllegalArgumentException("Page size must not be less than one");
        if (du == null || au == null)
            throw new IllegalArgumentException("La date de début et la date de fin sont obligatoires");
        if (au.isBefore(du)) return new PageEcritures(List.of(), 0, page, taille);

        Filtres filtres = filtres(centre, du, au, journal, statut);
        long total = Optional.ofNullable(jdbc.queryForObject("SELECT COUNT(*) FROM ecritures_comptables e "
                + filtres.where, Long.class, filtres.parametres.toArray())).orElse(0L);
        long offset = (long) page * taille;
        List<Object> parametresPage = new ArrayList<>(filtres.parametres);
        parametresPage.add(taille);
        parametresPage.add(offset);
        String sqlPage = "WITH page_ecritures AS (SELECT e.id FROM ecritures_comptables e " + filtres.where
                + " ORDER BY e.date_ecriture DESC, e.numero_piece DESC LIMIT ? OFFSET ?) SELECT " + COLONNES
                + " FROM ecritures_comptables e LEFT JOIN lignes_ecriture l ON l.ecriture_id = e.id "
                + "JOIN page_ecritures p ON p.id = e.id ORDER BY e.date_ecriture DESC, e.numero_piece DESC, l.id ASC";
        List<EcritureComptable> items = lireSql(sqlPage, parametresPage);
        return new PageEcritures(items, total, page, taille);
    }

    @Override
    public void enregistrer(EcritureComptable ecriture) {
        List<Map<String, Object>> existants = jdbc.queryForList("SELECT center_id, statut FROM ecritures_comptables WHERE id = ?",
                ecriture.id());
        if (!existants.isEmpty() && !ecriture.centre().valeur().equals(uuid(existants.getFirst().get("center_id")))) {
            throw new IllegalArgumentException("Une écriture d'un autre centre ne peut pas être modifiée");
        }
        if (!existants.isEmpty() && "EXPORTEE".equals(existants.getFirst().get("statut"))
                && ecriture.statut() != StatutEcriture.EXPORTEE) {
            throw new IllegalStateException("Une écriture exportée ne peut pas être modifiée");
        }
        if (!existants.isEmpty() && "EXPORTEE".equals(existants.getFirst().get("statut"))) {
            return;
        }
        if (existants.isEmpty()) {
            jdbc.update("INSERT INTO ecritures_comptables (id, center_id, journal_code, date_ecriture, date_piece, "
                            + "numero_piece, libelle, statut, source_id, modele_id, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    ecriture.id(), ecriture.centre().valeur(), ecriture.journal().valeur(), ecriture.dateEcriture(),
                    ecriture.datePiece(), ecriture.numeroDePiece().valeur(), ecriture.libelle(), ecriture.statut().name(),
                    ecriture.source(), ecriture.modele());
        } else {
            if (ecriture.statut() == StatutEcriture.EXPORTEE && ecriture.exporteeLe() != null) {
                jdbc.update("UPDATE ecritures_comptables SET journal_code = ?, date_ecriture = ?, date_piece = ?, numero_piece = ?, "
                                + "libelle = ?, statut = ?, source_id = ?, modele_id = ?, updated_at = ? "
                                + "WHERE id = ? AND center_id = ?",
                        ecriture.journal().valeur(), ecriture.dateEcriture(), ecriture.datePiece(),
                        ecriture.numeroDePiece().valeur(), ecriture.libelle(), ecriture.statut().name(), ecriture.source(),
                        ecriture.modele(), java.time.OffsetDateTime.ofInstant(ecriture.exporteeLe(), java.time.ZoneOffset.UTC),
                        ecriture.id(), ecriture.centre().valeur());
            } else {
                jdbc.update("UPDATE ecritures_comptables SET journal_code = ?, date_ecriture = ?, date_piece = ?, numero_piece = ?, "
                                + "libelle = ?, statut = ?, source_id = ?, modele_id = ?, updated_at = CURRENT_TIMESTAMP "
                                + "WHERE id = ? AND center_id = ?",
                        ecriture.journal().valeur(), ecriture.dateEcriture(), ecriture.datePiece(),
                        ecriture.numeroDePiece().valeur(), ecriture.libelle(), ecriture.statut().name(), ecriture.source(),
                        ecriture.modele(), ecriture.id(), ecriture.centre().valeur());
            }
        }

        jdbc.update("DELETE FROM lignes_ecriture WHERE ecriture_id = ?", ecriture.id());
        for (LigneEcriture ligne : ecriture.lignes()) {
            jdbc.update("INSERT INTO lignes_ecriture (id, ecriture_id, compte_scf, libelle_ligne, montant_debit, "
                            + "montant_credit, tiers_id, axes_analytiques) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    ligne.id(), ecriture.id(), ligne.compte(), ligne.libelle(), ligne.debit(), ligne.credit(), ligne.tiers(),
                    serialiserAxes(ligne.axes()));
        }
    }

    @Override
    public List<EcritureComptable> duJournal(CentreId centre, CodeJournal journal, LocalDate du, LocalDate au) {
        if (au.isBefore(du)) return List.of();
        return lire("WHERE e.center_id = ? AND e.journal_code = ? AND e.date_ecriture BETWEEN ? AND ?",
                List.of(centre.valeur(), journal.valeur(), du, au),
                "ORDER BY e.date_ecriture ASC, e.numero_piece ASC, l.id ASC");
    }

    private List<EcritureComptable> lire(String where, List<?> parametres, String suffixe) {
        String sql = "SELECT " + COLONNES + " FROM ecritures_comptables e LEFT JOIN lignes_ecriture l "
                + "ON l.ecriture_id = e.id " + where + " " + suffixe;
        return lireSql(sql, parametres);
    }

    private List<EcritureComptable> lireSql(String sql, List<?> parametres) {
        Map<UUID, EcritureLue> resultats = new LinkedHashMap<>();
        jdbc.query(sql, (RowCallbackHandler) rs -> ajouter(rs, resultats), parametres.toArray());
        return resultats.values().stream().map(EcritureLue::construire).toList();
    }

    private void ajouter(ResultSet rs, Map<UUID, EcritureLue> resultats) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        EcritureLue ecriture = resultats.computeIfAbsent(id, ignored -> {
            try {
                return new EcritureLue(id, rs.getObject("center_id", UUID.class), rs.getString("journal_code"),
                        rs.getObject("date_ecriture", LocalDate.class), rs.getObject("date_piece", LocalDate.class),
                        rs.getString("numero_piece"), rs.getString("libelle"), rs.getString("statut"),
                        rs.getObject("source_id", UUID.class), rs.getObject("modele_id", UUID.class),
                        instant(rs.getObject("updated_at")));
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });
        UUID ligneId = rs.getObject("ligne_id", UUID.class);
        if (ligneId != null) {
            ecriture.lignes.add(new LigneEcriture(ligneId, rs.getString("compte_scf"), rs.getString("libelle_ligne"),
                    rs.getBigDecimal("montant_debit"), rs.getBigDecimal("montant_credit"),
                    rs.getObject("tiers_id", UUID.class), axes(rs.getString("axes_analytiques"))));
        }
    }

    private record Filtres(String where, List<Object> parametres) {
    }

    private static final class EcritureLue {
        private final UUID id;
        private final UUID centre;
        private final String journal;
        private final LocalDate dateEcriture;
        private final LocalDate datePiece;
        private final String numero;
        private final String libelle;
        private final String statut;
        private final UUID source;
        private final UUID modele;
        private final Instant exporteeLe;
        private final List<LigneEcriture> lignes = new ArrayList<>();

        private EcritureLue(UUID id, UUID centre, String journal, LocalDate dateEcriture, LocalDate datePiece,
                            String numero, String libelle, String statut, UUID source, UUID modele, Instant exporteeLe) {
            this.id = id;
            this.centre = centre;
            this.journal = journal;
            this.dateEcriture = dateEcriture;
            this.datePiece = datePiece;
            this.numero = numero;
            this.libelle = libelle;
            this.statut = statut;
            this.source = source;
            this.modele = modele;
            this.exporteeLe = exporteeLe;
        }

        private EcritureComptable construire() {
            return EcritureComptable.reconstituer(id, new CentreId(centre), CodeJournal.de(journal), dateEcriture,
                    datePiece, NumeroDePiece.de(numero), libelle, lignes, StatutEcriture.depuis(statut), source, modele,
                    "EXPORTEE".equals(statut) ? exporteeLe : null, null);
        }
    }
}






