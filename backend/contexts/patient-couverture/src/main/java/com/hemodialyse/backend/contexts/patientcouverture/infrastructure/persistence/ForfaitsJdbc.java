package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.application.LectureDuCatalogueDeForfaits;
import com.hemodialyse.backend.contexts.patientcouverture.domain.CatalogueDeForfaits;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Forfait;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.ForfaitsDuCentre;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDuCatalogueDeForfaits.FicheDeForfait;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDuCatalogueDeForfaits.PageDeForfaits;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Le catalogue de forfaits sur la table actuelle {@code forfait} (RF2, refonte sur place : mêmes colonnes {@code code},
 * {@code libelle}, {@code prix}) : catalogue, page de l'administration et langage publié. Chaque requête est bornée à
 * {@code center_id} ; chaque écriture signale son centre ({@code ecritures}) pour que la liste en cache soit invalidée après le
 * commit. Le tri est celui de la base ({@code ORDER BY code, id}), comme avant.
 */
public class ForfaitsJdbc implements CatalogueDeForfaits, LectureDuCatalogueDeForfaits, ForfaitsDuCentre {

    private static final String FORFAIT = "SELECT id, center_id, code, libelle, prix FROM forfait";

    private static final RowMapper<Forfait> LIGNE = (rs, n) -> Forfait.reconstituer(rs.getObject("id", UUID.class),
            new CentreId(rs.getObject("center_id", UUID.class)), rs.getString("code"), rs.getString("libelle"), rs.getBigDecimal("prix"));

    private static final RowMapper<ForfaitPublie> PUBLIE = (rs, n) -> new ForfaitPublie(rs.getObject("id", UUID.class),
            rs.getString("code"), rs.getString("libelle"), rs.getBigDecimal("prix"));

    private final JdbcTemplate jdbc;
    private final Consumer<CentreId> ecritures;

    public ForfaitsJdbc(JdbcTemplate jdbc, Consumer<CentreId> ecritures) {
        this.jdbc = jdbc;
        this.ecritures = ecritures;
    }

    @Override
    public Optional<Forfait> trouver(CentreId centre, UUID id) {
        return jdbc.query(FORFAIT + " WHERE center_id = ? AND id = ?", LIGNE, centre.valeur(), id).stream().findFirst();
    }

    @Override
    public List<Forfait> duCentre(CentreId centre) {
        return jdbc.query(FORFAIT + " WHERE center_id = ? ORDER BY code, id", LIGNE, centre.valeur());
    }

    @Override
    public void enregistrer(Forfait f) {
        jdbc.update("INSERT INTO forfait (id, center_id, code, libelle, prix) VALUES (?, ?, ?, ?, ?)", f.id(), f.centre().valeur(),
                f.code(), f.libelle(), f.prix());
        ecritures.accept(f.centre());
    }

    @Override
    public void remplacer(Forfait f) {
        jdbc.update("UPDATE forfait SET code = ?, libelle = ?, prix = ? WHERE id = ? AND center_id = ?", f.code(), f.libelle(), f.prix(),
                f.id(), f.centre().valeur());
        ecritures.accept(f.centre());
    }

    @Override
    public void supprimer(CentreId centre, UUID id) {
        jdbc.update("DELETE FROM forfait WHERE id = ? AND center_id = ?", id, centre.valeur());
        ecritures.accept(centre);
    }

    @Override
    public PageDeForfaits page(CentreId centre, String recherche, int page, int taille) {
        List<Object> parametres = new ArrayList<>();
        parametres.add(centre.valeur());
        String condition = " WHERE t.center_id = ?";
        if (recherche != null && !recherche.isBlank()) {
            String like = "%" + recherche.trim().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            condition += " AND (LOWER(COALESCE(t.code, '')) LIKE ? ESCAPE '\\' OR LOWER(COALESCE(t.libelle, '')) LIKE ? ESCAPE '\\')";
            parametres.add(like);
            parametres.add(like);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM forfait t" + condition, Long.class, parametres.toArray());
        List<Object> pageParametres = new ArrayList<>(parametres);
        pageParametres.add(taille);
        pageParametres.add((long) page * taille);
        List<FicheDeForfait> lignes = jdbc.query("SELECT t.id, t.code, t.libelle, t.prix FROM forfait t" + condition
                        + " ORDER BY t.code, t.id LIMIT ? OFFSET ?",
                (rs, n) -> new FicheDeForfait(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("libelle"),
                        rs.getBigDecimal("prix")),
                pageParametres.toArray());
        return new PageDeForfaits(lignes, total == null ? 0 : total, page, taille);
    }

    // ---- langage publié

    @Override
    public List<ForfaitPublie> lister(UUID centre) {
        return jdbc.query("SELECT id, code, libelle, prix FROM forfait WHERE center_id = ? ORDER BY code, id", PUBLIE, centre);
    }

    @Override
    public Optional<ForfaitPublie> trouver(UUID centre, UUID id) {
        return jdbc.query("SELECT id, code, libelle, prix FROM forfait WHERE center_id = ? AND id = ?", PUBLIE, centre, id)
                .stream().findFirst();
    }

    @Override
    public Optional<ForfaitPublie> parCode(UUID centre, String code) {
        if (code == null) return Optional.empty();
        String cle = Forfait.cleDe(code);
        return lister(centre).stream().filter(f -> f.code() != null && Forfait.cleDe(f.code()).equals(cle)).findFirst();
    }

    @Override
    public Optional<BigDecimal> prix(UUID centre, UUID id) {
        return trouver(centre, id).map(ForfaitPublie::prix);
    }

    @Override
    public boolean existe(UUID centre, UUID id) {
        Long nombre = jdbc.queryForObject("SELECT COUNT(*) FROM forfait WHERE center_id = ? AND id = ?", Long.class, centre, id);
        return nombre != null && nombre > 0;
    }
}
