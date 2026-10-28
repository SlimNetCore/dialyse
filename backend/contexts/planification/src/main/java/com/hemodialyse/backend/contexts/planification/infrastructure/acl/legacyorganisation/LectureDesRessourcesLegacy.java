package com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacyorganisation;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.gmao.publishedlanguage.GenerateursDuCentre;
import com.hemodialyse.backend.contexts.planification.application.LectureDesRessources;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDuPlanningRepository;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre.Creneau;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre.Fermeture;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre.Generateur;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre.Salle;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ACL vers Organisation (salles et créneaux, tables {@code salle} et {@code position_creneau}, encore dans le legacy), vers GMAO (les
 * générateurs par son langage publié {@link GenerateursDuCentre}, jamais par sa table), vers le paramétrage (jours d'ouverture) et
 * vers le calendrier clinique (fériés et fermetures exceptionnelles des 90 jours suivant le jour donné). Seuls les générateurs de
 * dialyse {@code EN_SERVICE} rattachés à une salle sont retenus (RG-PLN-011). Salles et créneaux sont triés par code.
 */
public class LectureDesRessourcesLegacy implements LectureDesRessources {

    /** Horizon des fermetures datées signalées sur une proposition de placement (RG-PLN-013). */
    static final int HORIZON_DES_FERMETURES_EN_JOURS = 90;
    static final String EN_SERVICE = "EN_SERVICE";

    private final JdbcTemplate jdbc;
    private final GenerateursDuCentre generateurs;
    private final ParametresDuPlanningRepository parametres;

    public LectureDesRessourcesLegacy(JdbcTemplate jdbc, GenerateursDuCentre generateurs, ParametresDuPlanningRepository parametres) {
        this.jdbc = jdbc;
        this.generateurs = generateurs;
        this.parametres = parametres;
    }

    @Override
    public RessourcesDuCentre duCentre(CentreId centre, LocalDate aPartirDu) {
        List<Salle> salles = jdbc.query("SELECT id, nom, isolement FROM salle WHERE center_id = ? ORDER BY code",
                (rs, i) -> new Salle(new SalleId(rs.getObject("id", UUID.class)), rs.getString("nom"),
                        SallesDuCentreJdbc.OUI.equals(rs.getString("isolement"))), centre.valeur());
        AtomicInteger rang = new AtomicInteger();
        List<Creneau> creneaux = jdbc.query("SELECT id, libelle FROM position_creneau WHERE center_id = ? ORDER BY code",
                (rs, i) -> new Creneau(new CreneauId(rs.getObject("id", UUID.class)), rs.getString("libelle"),
                        rang.incrementAndGet()), centre.valeur());
        List<Generateur> enService = generateurs.duCentre(centre.valeur()).stream()
                .filter(g -> EN_SERVICE.equals(g.statut()) && g.salleId() != null)
                .map(g -> new Generateur(new GenerateurId(g.id()), g.code(), new SalleId(g.salleId()))).toList();
        return new RessourcesDuCentre(salles, creneaux, enService, parametres.lire(centre).joursOuverts(),
                fermetures(centre, aPartirDu, aPartirDu.plusDays(HORIZON_DES_FERMETURES_EN_JOURS)));
    }

    private List<Fermeture> fermetures(CentreId centre, LocalDate du, LocalDate au) {
        List<Fermeture> fermetures = new ArrayList<>();
        fermetures.addAll(lire("SELECT day_date, label AS motif FROM center_holiday WHERE center_id = ? AND day_date BETWEEN ? AND ?",
                centre, du, au));
        fermetures.addAll(lire("SELECT day_date, reason AS motif FROM center_closure_day WHERE center_id = ? AND day_date BETWEEN ? AND ?",
                centre, du, au));
        return fermetures;
    }

    private List<Fermeture> lire(String sql, CentreId centre, LocalDate du, LocalDate au) {
        return jdbc.query(sql, (rs, i) -> new Fermeture(rs.getDate("day_date").toLocalDate(), rs.getString("motif")),
                centre.valeur(), Date.valueOf(du), Date.valueOf(au));
    }
}
