package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.application.LectureDesTransporteurs;
import com.hemodialyse.backend.contexts.patientcouverture.domain.CarnetDeTransporteurs;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Transporteur;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesTransporteurs.FicheDeTransporteur;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesTransporteurs.PageDeTransporteurs;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.Transporteurs;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Les transporteurs sur la table actuelle {@code transporteur} (RF1, refonte sur place : mêmes colonnes) : carnet, page de
 * l'administration et langage publié. Chaque requête est bornée à {@code center_id} ; chaque écriture signale son centre
 * ({@code ecritures}) pour que les lectures en cache soient invalidées après le commit. Le nombre de références ne compte que
 * les patients du centre du transporteur (KB-RF1-03 corrigé, ADR 0123).
 */
public class TransporteursJdbc implements CarnetDeTransporteurs, LectureDesTransporteurs, Transporteurs {

    private static final String TRANSPORTEUR = "SELECT id, center_id, nom, telephone FROM transporteur";

    private static final RowMapper<Transporteur> LIGNE = (rs, n) -> Transporteur.reconstituer(rs.getObject("id", UUID.class),
            new CentreId(rs.getObject("center_id", UUID.class)), rs.getString("nom"), rs.getString("telephone"));

    private final JdbcTemplate jdbc;
    private final Consumer<CentreId> ecritures;

    public TransporteursJdbc(JdbcTemplate jdbc, Consumer<CentreId> ecritures) {
        this.jdbc = jdbc;
        this.ecritures = ecritures;
    }

    @Override
    public Optional<Transporteur> trouver(CentreId centre, UUID id) {
        return jdbc.query(TRANSPORTEUR + " WHERE center_id = ? AND id = ?", LIGNE, centre.valeur(), id).stream().findFirst();
    }

    @Override
    public List<Transporteur> duCentre(CentreId centre) {
        return jdbc.query(TRANSPORTEUR + " WHERE center_id = ?", LIGNE, centre.valeur());
    }

    @Override
    public void enregistrer(Transporteur t) {
        jdbc.update("INSERT INTO transporteur (id, center_id, nom, telephone) VALUES (?, ?, ?, ?)", t.id(), t.centre().valeur(),
                t.nom(), t.telephone());
        ecritures.accept(t.centre());
    }

    @Override
    public void remplacer(Transporteur t) {
        jdbc.update("UPDATE transporteur SET nom = ?, telephone = ? WHERE id = ? AND center_id = ?", t.nom(), t.telephone(),
                t.id(), t.centre().valeur());
        ecritures.accept(t.centre());
    }

    @Override
    public void supprimer(CentreId centre, UUID id) {
        jdbc.update("DELETE FROM transporteur WHERE id = ? AND center_id = ?", id, centre.valeur());
        ecritures.accept(centre);
    }

    @Override
    public long nombreDeReferences(CentreId centre, UUID id) {
        long total = 0;
        for (String colonne : new String[]{"transporteur_aller_id", "transporteur_retour_id"}) {
            try {
                Long nombre = jdbc.queryForObject("SELECT COUNT(*) FROM patients WHERE " + colonne + " = ? AND center_id = ?",
                        Long.class, id, centre.valeur());
                total += nombre == null ? 0 : nombre;
            } catch (DataAccessException schemaAllege) {
                // table absente d'un schéma allégé : le contrôle est ignoré, comme avant
            }
        }
        return total;
    }

    @Override
    public PageDeTransporteurs page(CentreId centre, String recherche, int page, int taille) {
        List<Object> parametres = new ArrayList<>();
        parametres.add(centre.valeur());
        String condition = " WHERE t.center_id = ?";
        if (recherche != null && !recherche.isBlank()) {
            String like = "%" + recherche.trim().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            condition += " AND (LOWER(COALESCE(t.nom, '')) LIKE ? ESCAPE '\\' OR LOWER(COALESCE(t.telephone, '')) LIKE ? ESCAPE '\\')";
            parametres.add(like);
            parametres.add(like);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM transporteur t" + condition, Long.class, parametres.toArray());
        List<Object> pageParametres = new ArrayList<>(parametres);
        pageParametres.add(taille);
        pageParametres.add((long) page * taille);
        List<FicheDeTransporteur> lignes = jdbc.query("SELECT t.id, t.nom, t.telephone FROM transporteur t" + condition
                        + " ORDER BY t.nom, t.id LIMIT ? OFFSET ?",
                (rs, n) -> new FicheDeTransporteur(rs.getObject("id", UUID.class), rs.getString("nom"), rs.getString("telephone")),
                pageParametres.toArray());
        return new PageDeTransporteurs(lignes, total == null ? 0 : total, page, taille);
    }

    @Override
    public List<TransporteurPublie> lister(UUID centre) {
        return jdbc.query("SELECT id, nom FROM transporteur WHERE center_id = ? ORDER BY nom",
                (rs, n) -> new TransporteurPublie(rs.getObject("id", UUID.class), rs.getString("nom")), centre);
    }

    @Override
    public Optional<String> nom(UUID centre, UUID id) {
        return jdbc.query("SELECT nom FROM transporteur WHERE center_id = ? AND id = ?", (rs, n) -> rs.getString("nom"), centre, id)
                .stream().findFirst();
    }

    @Override
    public boolean existe(UUID centre, UUID id) {
        Long nombre = jdbc.queryForObject("SELECT COUNT(*) FROM transporteur WHERE center_id = ? AND id = ?", Long.class, centre, id);
        return nombre != null && nombre > 0;
    }
}
