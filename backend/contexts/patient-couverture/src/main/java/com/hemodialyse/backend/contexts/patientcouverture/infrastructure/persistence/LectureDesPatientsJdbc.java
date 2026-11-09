package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.LibellesDeLOrganisation;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.LibellesDeLOrganisation.LibelleDeCreneau;
import com.hemodialyse.backend.contexts.patientcouverture.application.CriteresDeRecherche;
import com.hemodialyse.backend.contexts.patientcouverture.application.LectureDesPatients;
import com.hemodialyse.backend.contexts.patientcouverture.application.LigneDEffectif;
import com.hemodialyse.backend.contexts.patientcouverture.application.LignePatient;
import com.hemodialyse.backend.contexts.patientcouverture.application.PageDePatients;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Lecture SQL de la liste des patients : mêmes filtres, même tri, mêmes libellés que la recherche actuelle (critères JPA
 * traduits en SQL). Jamais d'écriture. {@code %} et {@code _} ne sont pas échappés (reproduit, KB-PAT-11) ; une colonne non
 * triable retombe sur nom, prénom ; l'ordre alphabétique dépend de l'interclassement de la base. Les libellés du médecin et du créneau
 * et leurs recherches viennent du langage publié d'Organisation (O4 lot C, ADR 0109), bornés au centre, lus par lot.
 */
public class LectureDesPatientsJdbc implements LectureDesPatients {

    private static final Map<String, String> COLONNES_TRIABLES = Map.of("code", "code_patient", "nom", "nom", "prenom", "prenom",
            "sexe", "sexe", "dateAdmission", "date_admission", "numeroAssurance", "numero_assurance", "etatPatient",
            "etat_patient");

    private static final String CODE_LIBELLE_RECHERCHE = "LOWER(COALESCE(code, '') || ' ' || COALESCE(libelle, ''))";
    private static final String NOM_RECHERCHE = "LOWER(COALESCE(nom, ''))";

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate nomme;
    private final LibellesDeLOrganisation organisation;

    public LectureDesPatientsJdbc(JdbcTemplate jdbc, LibellesDeLOrganisation organisation) {
        this.jdbc = jdbc;
        this.nomme = new NamedParameterJdbcTemplate(jdbc);
        this.organisation = organisation;
    }

    private record CodeLibelle(String code, String libelle) {
    }

    private record Pec(UUID forfaitId, String statut, OffsetDateTime creeLe) {
    }

    private record Ligne(UUID id, UUID medecin, UUID position, UUID aller, UUID retour, LignePatient base) {
    }

    @Override
    public PageDePatients rechercher(CentreId centre, CriteresDeRecherche c) {
        UUID centreId = centre.valeur();
        List<String> predicats = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        predicats.add("p.center_id = ?");
        args.add(centreId);
        contient(predicats, args, "p.code_patient", c.code());
        contient(predicats, args, "p.nom", c.nom());
        contient(predicats, args, "p.prenom", c.prenom());
        contientOuParmi(predicats, args, "p.sexe", c.sexe());
        contient(predicats, args, "p.numero_assurance", c.numeroAssurance());
        contientOuParmi(predicats, args, "p.etat_patient", c.etatPatient());
        parmi(predicats, args, "p.medecin_traitant_id", texte(c.medecinTraitantId())
                ? organisation.medecinsCorrespondant(centreId, c.medecinTraitantId()) : null);
        parmi(predicats, args, "p.position_id", texte(c.positionId())
                ? choisisOu(c.positionId(), t -> organisation.creneauxCorrespondant(centreId, t)) : null);
        parmi(predicats, args, "p.transporteur_aller_id", texte(c.transporteurAllerId())
                ? correspondances("transporteur", NOM_RECHERCHE, centreId, c.transporteurAllerId()) : null);
        parmi(predicats, args, "p.transporteur_retour_id", texte(c.transporteurRetourId())
                ? correspondances("transporteur", NOM_RECHERCHE, centreId, c.transporteurRetourId()) : null);
        if (texte(c.pecForfaitId())) {
            Set<UUID> forfaits = choisisOu(c.pecForfaitId(), t -> correspondances("forfait", CODE_LIBELLE_RECHERCHE, centreId, t));
            if (forfaits.isEmpty()) {
                predicats.add("1 = 0");
            } else {
                predicats.add("EXISTS (SELECT 1 FROM prise_en_charge f WHERE f.center_id = p.center_id AND f.patient_id = p.id "
                        + "AND LOWER(f.statut) = 'validee' AND f.forfait_demande_id IN (" + marques(forfaits.size()) + "))");
                args.addAll(forfaits);
            }
        }
        LocalDate du = c.dateAdmissionFrom();
        LocalDate au = c.dateAdmissionTo();
        if (du != null && au != null && du.isAfter(au)) {
            LocalDate t = du;
            du = au;
            au = t;
        }
        if (du != null) {
            predicats.add("p.date_admission >= ?");
            args.add(Date.valueOf(du));
        }
        if (au != null) {
            predicats.add("p.date_admission <= ?");
            args.add(Date.valueOf(au));
        }
        if (c.nonFacturable() != null) {
            predicats.add((c.nonFacturable() ? "NOT EXISTS" : "EXISTS") + " (SELECT 1 FROM prise_en_charge v "
                    + "WHERE v.center_id = p.center_id AND v.patient_id = p.id AND LOWER(v.statut) = 'validee')");
        }
        String ou = " WHERE " + String.join(" AND ", predicats);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM patients p" + ou, Long.class, args.toArray());

        String colonne = c.sortBy() == null ? null : COLONNES_TRIABLES.get(c.sortBy());
        String ordre = colonne == null ? " ORDER BY p.nom ASC, p.prenom ASC"
                : " ORDER BY p." + colonne + ("desc".equalsIgnoreCase(c.sortDirection()) ? " DESC" : " ASC");
        List<Object> argsPage = new ArrayList<>(args);
        argsPage.add(c.size());
        argsPage.add((long) c.page() * c.size());
        List<Ligne> lignes = jdbc.query("SELECT p.id, p.code_patient, p.nom, p.prenom, p.sexe, p.date_admission, "
                + "p.numero_assurance, p.etat_patient, p.date_evenement_etat, p.medecin_traitant_id, p.position_id, "
                + "p.transporteur_aller_id, p.transporteur_retour_id, p.jour_dimanche, p.jour_lundi, p.jour_mardi, "
                + "p.jour_mercredi, p.jour_jeudi, p.jour_vendredi, p.jour_samedi FROM patients p" + ou + ordre
                + " LIMIT ? OFFSET ?", (rs, n) -> {
            Date evenement = rs.getDate("date_evenement_etat");
            String dateEvenement = evenement == null ? "" : evenement.toLocalDate().toString();
            Date admission = rs.getDate("date_admission");
            return new Ligne(rs.getObject("id", UUID.class), rs.getObject("medecin_traitant_id", UUID.class),
                    rs.getObject("position_id", UUID.class), rs.getObject("transporteur_aller_id", UUID.class),
                    rs.getObject("transporteur_retour_id", UUID.class),
                    new LignePatient(rs.getObject("id", UUID.class), rs.getString("code_patient"), rs.getString("nom"),
                            rs.getString("prenom"), rs.getString("sexe"), admission == null ? null : admission.toLocalDate(),
                            rs.getString("numero_assurance"), rs.getString("etat_patient"), dateEvenement, dateEvenement,
                            rs.getObject("medecin_traitant_id", UUID.class), null, rs.getObject("position_id", UUID.class), null,
                            null, rs.getObject("transporteur_aller_id", UUID.class), null,
                            rs.getObject("transporteur_retour_id", UUID.class), null, nullable(rs, "jour_dimanche"),
                            nullable(rs, "jour_lundi"), nullable(rs, "jour_mardi"), nullable(rs, "jour_mercredi"),
                            nullable(rs, "jour_jeudi"), nullable(rs, "jour_vendredi"), nullable(rs, "jour_samedi"), true, null,
                            null, null, null));
        }, argsPage.toArray());

        Map<UUID, Pec> pecs = dernieresPecValidees(centreId, lignes.stream().map(Ligne::id).toList());
        Map<UUID, String> medecins = organisation.nomsDesMedecins(centreId, lignes.stream().map(Ligne::medecin).toList());
        Map<UUID, LibelleDeCreneau> positions = organisation.creneauxDuCentre(centreId,
                lignes.stream().map(Ligne::position).toList());
        List<UUID> transporteurs = new ArrayList<>();
        lignes.forEach(l -> {
            transporteurs.add(l.aller());
            transporteurs.add(l.retour());
        });
        Map<UUID, String> nomsTransporteurs = libelles("SELECT id, nom AS label FROM transporteur", centreId, transporteurs);
        Map<UUID, CodeLibelle> forfaits = codeLibelles("forfait", centreId,
                pecs.values().stream().map(Pec::forfaitId).toList());

        List<LignePatient> items = new ArrayList<>();
        for (Ligne l : lignes) {
            LignePatient b = l.base();
            Pec pec = pecs.get(l.id());
            LibelleDeCreneau position = trouve(positions, l.position());
            CodeLibelle forfait = pec == null ? null : trouve(forfaits, pec.forfaitId());
            items.add(new LignePatient(b.id(), b.codePatient(), b.nom(), b.prenom(), b.sexe(), b.dateAdmission(),
                    b.numeroAssurance(), b.etatPatient(), b.dateEvenementEtat(), b.dateEvenement(), l.medecin(),
                    trouve(medecins, l.medecin()), l.position(), position == null ? null : position.code(),
                    position == null ? null : position.libelle(), l.aller(), trouve(nomsTransporteurs, l.aller()), l.retour(),
                    trouve(nomsTransporteurs, l.retour()), b.jourDimanche(), b.jourLundi(), b.jourMardi(), b.jourMercredi(),
                    b.jourJeudi(), b.jourVendredi(), b.jourSamedi(), pec == null, pec == null ? null : pec.statut(),
                    pec == null ? null : pec.forfaitId(), forfait == null ? null : forfait.code(),
                    forfait == null ? null : forfait.libelle()));
        }
        return new PageDePatients(items, total == null ? 0 : total, c.page(), c.size());
    }

    @Override
    public List<LigneDEffectif> effectifDuCentre(CentreId centre) {
        return jdbc.query("SELECT id, code_patient, nom, prenom, sexe, date_naissance, sous_kt, etat_patient, "
                + "date_evenement_etat, date_admission FROM patients WHERE center_id = ?", (rs, n) -> {
            Date naissance = rs.getDate("date_naissance");
            Date evenement = rs.getDate("date_evenement_etat");
            Date admission = rs.getDate("date_admission");
            return new LigneDEffectif(rs.getObject("id", UUID.class), rs.getString("code_patient"), rs.getString("nom"),
                    rs.getString("prenom"), rs.getString("sexe"), naissance == null ? null : naissance.toLocalDate(),
                    nullable(rs, "sous_kt"), rs.getString("etat_patient"), evenement == null ? null : evenement.toLocalDate(),
                    admission == null ? null : admission.toLocalDate());
        }, centre.valeur());
    }

    // ---- filtres

    private static boolean texte(String valeur) {
        return valeur != null && !valeur.isBlank();
    }

    private static void contient(List<String> predicats, List<Object> args, String colonne, String valeur) {
        if (!texte(valeur)) return;
        predicats.add("LOWER(" + colonne + ") LIKE ?");
        args.add("%" + valeur.toLowerCase(Locale.ROOT).trim() + "%");
    }

    private static void contientOuParmi(List<String> predicats, List<Object> args, String colonne, String valeur) {
        if (!texte(valeur)) return;
        if (!valeur.contains(",")) {
            contient(predicats, args, colonne, valeur);
            return;
        }
        List<String> choix = Arrays.stream(valeur.split(",")).map(v -> v.trim().toLowerCase(Locale.ROOT))
                .filter(v -> !v.isEmpty()).distinct().toList();
        if (choix.isEmpty()) return;
        predicats.add("LOWER(" + colonne + ") IN (" + marques(choix.size()) + ")");
        args.addAll(choix);
    }

    private static void parmi(List<String> predicats, List<Object> args, String colonne, Set<UUID> identifiants) {
        if (identifiants == null) return;
        if (identifiants.isEmpty()) {
            predicats.add("1 = 0");
            return;
        }
        predicats.add(colonne + " IN (" + marques(identifiants.size()) + ")");
        args.addAll(identifiants);
    }

    private static String marques(int nombre) {
        return String.join(",", java.util.Collections.nCopies(nombre, "?"));
    }

    /** Identifiants séparés par des virgules, utilisés tels quels ; toute autre saisie est cherchée par libellé. */
    private static Set<UUID> choisisOu(String valeur, Function<String, Set<UUID>> parLibelle) {
        Set<UUID> ids = new HashSet<>();
        for (String partie : valeur.split(",")) {
            String t = partie.trim();
            if (t.isEmpty()) continue;
            try {
                ids.add(UUID.fromString(t));
            } catch (IllegalArgumentException pasUnIdentifiant) {
                return parLibelle.apply(valeur);
            }
        }
        return ids.isEmpty() ? parLibelle.apply(valeur) : ids;
    }

    // ---- référentiels (toujours bornés au centre)

    private Set<UUID> correspondances(String table, String expression, UUID centre, String terme) {
        String like = "%" + terme.trim().toLowerCase(Locale.ROOT) + "%";
        return new HashSet<>(nomme.queryForList("SELECT id FROM " + table + " WHERE center_id = :centre AND (" + expression
                        + " LIKE :terme OR LOWER(CAST(id AS VARCHAR(36))) LIKE :terme)",
                new MapSqlParameterSource().addValue("centre", centre).addValue("terme", like), UUID.class));
    }

    private Map<UUID, String> libelles(String select, UUID centre, Collection<UUID> ids) {
        Set<UUID> distincts = distincts(ids);
        if (distincts.isEmpty()) return Map.of();
        Map<UUID, String> sortie = new HashMap<>();
        nomme.query(select + " WHERE center_id = :centre AND id IN (:ids)",
                new MapSqlParameterSource().addValue("centre", centre).addValue("ids", distincts), rs -> {
                    String libelle = rs.getString("label");
                    if (libelle != null && !libelle.isBlank()) sortie.put(rs.getObject("id", UUID.class), libelle.trim());
                });
        return sortie;
    }

    private Map<UUID, CodeLibelle> codeLibelles(String table, UUID centre, Collection<UUID> ids) {
        Set<UUID> distincts = distincts(ids);
        if (distincts.isEmpty()) return Map.of();
        Map<UUID, CodeLibelle> sortie = new HashMap<>();
        nomme.query("SELECT id, code, libelle FROM " + table + " WHERE center_id = :centre AND id IN (:ids)",
                new MapSqlParameterSource().addValue("centre", centre).addValue("ids", distincts),
                rs -> {
                    sortie.put(rs.getObject("id", UUID.class), new CodeLibelle(rs.getString("code"), rs.getString("libelle")));
                });
        return sortie;
    }

    private static Set<UUID> distincts(Collection<UUID> ids) {
        Set<UUID> sortie = new HashSet<>();
        if (ids != null) ids.stream().filter(Objects::nonNull).forEach(sortie::add);
        return sortie;
    }

    private static <V> V trouve(Map<UUID, V> valeurs, UUID id) {
        return id == null ? null : valeurs.get(id);
    }

    /** La prise en charge validée la plus récente de chaque patient (statut exact {@code VALIDEE}). */
    private Map<UUID, Pec> dernieresPecValidees(UUID centre, List<UUID> patients) {
        if (patients.isEmpty()) return Map.of();
        Map<UUID, Pec> sortie = new HashMap<>();
        nomme.query("SELECT patient_id, forfait_demande_id, statut, created_at FROM prise_en_charge WHERE center_id = :centre "
                        + "AND statut = 'VALIDEE' AND patient_id IN (:ids)",
                new MapSqlParameterSource().addValue("centre", centre).addValue("ids", patients), rs -> {
                    Pec pec = new Pec(rs.getObject("forfait_demande_id", UUID.class), rs.getString("statut"),
                            rs.getObject("created_at", OffsetDateTime.class));
                    sortie.merge(rs.getObject("patient_id", UUID.class), pec, (gauche, droite) -> {
                        if (gauche.creeLe() == null) return droite;
                        if (droite.creeLe() == null) return gauche;
                        return Comparator.comparing(Pec::creeLe).compare(gauche, droite) >= 0 ? gauche : droite;
                    });
                });
        return sortie;
    }

    private static Boolean nullable(java.sql.ResultSet rs, String colonne) throws java.sql.SQLException {
        boolean valeur = rs.getBoolean(colonne);
        return rs.wasNull() ? null : valeur;
    }
}
