package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.persistence;

import com.hemodialyse.backend.contexts.pilotagedirection.application.CaissesDesCentresPayeurs;
import com.hemodialyse.backend.contexts.pilotagedirection.application.EffectifsDesPatients;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.EffectifDuCentre;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.Periode;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.PatientsDeLaCaisse;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.PrescriptionsDeLAnemie;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.ProfilDUnPatient;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lecteur TRANSITOIRE de la table {@code patients} et de la chaîne payeur / agence / caisse : agrégats de la Direction avec le prédicat
 * d'effectif. Jalon inscrit dans {@code TableDuPatientCouvertureTest} (2026-11-09, ADR 0102 ; jointure de la caisse dans un GROUP BY, la
 * découper changerait le résultat) et dans {@code TableDeLOrganisationTest} n'est PAS nécessaire (aucune table de l'Organisation) ;
 * échéance : langage publié du Patient (effectif d'une période, profil et caisse), revue au plus tard le 2026-12-15, butoir 2027-03-31.
 * Lecture seule, AUCUNE ligne n'identifie un patient (sexe, naissance et centre seulement).
 * <p>
 * SQL repris MOT POUR MOT des deux services retirés (commit {@code 9c21d51a}) : effectif et patients sous KT
 * {@code DirectionDashboardQueryService.java:112-116}, profils {@code DirectionBreakdownQueryService.java:171-181}, patients par caisse
 * {@code :186-192} (jointure {@code CAISSE_JOIN :42-45}), patients sous EPO / fer {@code :227-235}.
 */
public final class PatientsDesCentresJdbc implements EffectifsDesPatients {

    private final JdbcTemplate jdbc;
    private final CaissesDesCentresPayeurs caisses;

    /** {@code caisses} : la caisse de chaque centre payeur (KB-SEA-84, ADR 0118 : bornée au centre du patient, une chaîne d'un autre centre = caisse vide). */
    public PatientsDesCentresJdbc(JdbcTemplate jdbc, CaissesDesCentresPayeurs caisses) {
        this.jdbc = jdbc;
        this.caisses = caisses;
    }

    private static String marques(List<UUID> centres) {
        return String.join(",", Collections.nCopies(centres.size(), "?"));
    }

    @Override
    public Map<UUID, EffectifDuCentre> parCentre(List<UUID> centres, Periode periode) {
        Map<UUID, EffectifDuCentre> effectifs = new HashMap<>();
        PredicatDEffectif effectif = PredicatDEffectif.presentSur("p", periode);
        jdbc.query("SELECT p.center_id, COUNT(*) AS total, SUM(CASE WHEN p.sous_kt = TRUE THEN 1 ELSE 0 END) AS kt "
                        + "FROM patients p WHERE p.center_id IN (" + marques(centres) + ") AND " + effectif.sql() + " GROUP BY p.center_id",
                rs -> {
                    effectifs.put(rs.getObject("center_id", UUID.class), new EffectifDuCentre(rs.getLong("total"), rs.getLong("kt")));
                }, effectif.arguments(centres));
        return effectifs;
    }

    @Override
    public List<ProfilDUnPatient> profils(List<UUID> centres, Periode periode) {
        PredicatDEffectif effectif = PredicatDEffectif.presentSur("p", periode);
        return jdbc.query("SELECT p.center_id, p.sexe, p.date_naissance FROM patients p WHERE p.center_id IN (" + marques(centres)
                        + ") AND " + effectif.sql(),
                (rs, i) -> {
                    java.sql.Date naissance = rs.getDate("date_naissance");
                    return new ProfilDUnPatient(rs.getObject("center_id", UUID.class), rs.getString("sexe"),
                            naissance == null ? null : naissance.toLocalDate());
                }, effectif.arguments(centres));
    }

    /**
     * Patients par caisse : regroupés par centre payeur en SQL, puis par caisse. La caisse du centre payeur vient de l'annuaire des payeurs
     * (RF3b, ADR 0127), bornée au centre du patient (KB-SEA-84) ; code vide et nom nul sans caisse. Nom : le plus grand dans l'ordre des
     * chaînes Java ({@code compareTo}), indépendant de la base ; le {@code MAX(ca.nom)} d'avant suivait la collation du SGBD (non définie
     * par le code), le résultat peut donc différer À COLLATION PRÈS, seulement si deux caisses d'un centre partagent un code sans partager
     * leur nom (aucune contrainte en base : KB-RF5-01). Ordre de sortie garanti (RF5) : centre (texte de l'identifiant), puis code.
     */
    @Override
    public List<PatientsDeLaCaisse> parCaisse(List<UUID> centres, Periode periode) {
        record Cle(UUID centre, String code) {
        }
        PredicatDEffectif effectif = PredicatDEffectif.presentSur("p", periode);
        List<Object[]> lignes = new ArrayList<>();
        jdbc.query("SELECT p.center_id, p.centre_payeur_id, COUNT(*) AS n FROM patients p "
                        + "WHERE p.center_id IN (" + marques(centres) + ") AND " + effectif.sql() + " "
                        + "GROUP BY p.center_id, p.centre_payeur_id",
                rs -> {
                    lignes.add(new Object[]{rs.getObject("center_id", UUID.class), rs.getObject("centre_payeur_id", UUID.class),
                            rs.getLong("n")});
                },
                effectif.arguments(centres));
        Map<UUID, Map<UUID, CaissesDesCentresPayeurs.CaisseDuPayeur>> caissesParCentre = new HashMap<>();
        Map<Cle, long[]> nombres = new LinkedHashMap<>();
        Map<Cle, String> noms = new HashMap<>();
        for (Object[] ligne : lignes) {
            UUID centre = (UUID) ligne[0];
            UUID centrePayeur = (UUID) ligne[1];
            CaissesDesCentresPayeurs.CaisseDuPayeur caisse = centrePayeur == null ? null
                    : caissesParCentre.computeIfAbsent(centre, caisses::parCentrePayeur).get(centrePayeur);
            Cle cle = new Cle(centre, caisse == null ? "" : caisse.code());
            nombres.computeIfAbsent(cle, k -> new long[1])[0] += (Long) ligne[2];
            if (caisse != null && caisse.nom() != null) noms.merge(cle, caisse.nom(), (a, b) -> a.compareTo(b) >= 0 ? a : b);
        }
        List<PatientsDeLaCaisse> parCaisse = new ArrayList<>();
        nombres.forEach((cle, nombre) -> parCaisse.add(new PatientsDeLaCaisse(cle.centre(), cle.code(), noms.get(cle), nombre[0])));
        // RF5 (revue complémentaire, constat 1) : le GROUP BY n'ordonne rien ; tri final en Java, identique sur H2 et PostgreSQL
        parCaisse.sort(Comparator.comparing((PatientsDeLaCaisse c) -> c.centreId().toString()).thenComparing(PatientsDeLaCaisse::code));
        return parCaisse;
    }

    @Override
    public Map<UUID, PrescriptionsDeLAnemie> prescriptionsDeLAnemie(List<UUID> centres, Periode periode) {
        Map<UUID, PrescriptionsDeLAnemie> prescriptions = new HashMap<>();
        PredicatDEffectif effectif = PredicatDEffectif.presentSur("p", periode);
        jdbc.query("SELECT p.center_id, "
                        + "SUM(CASE WHEN p.epo_enabled = TRUE THEN 1 ELSE 0 END) AS sous_epo, "
                        + "SUM(CASE WHEN p.fer_enabled = TRUE THEN 1 ELSE 0 END) AS sous_fer "
                        + "FROM patients p WHERE p.center_id IN (" + marques(centres) + ") AND " + effectif.sql() + " GROUP BY p.center_id",
                rs -> {
                    prescriptions.put(rs.getObject("center_id", UUID.class),
                            new PrescriptionsDeLAnemie(rs.getLong("sous_epo"), rs.getLong("sous_fer")));
                }, effectif.arguments(centres));
        return prescriptions;
    }
}
