package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.LigneDuDetail;
import com.hemodialyse.backend.contexts.seance.domain.MoisDuTableauDeBord;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.TypeDeDetail;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Détail nominatif du tableau de bord (GET /api/v1/seances/dashboard/details?kind=presence|absence) : un patient, un jour, un statut par
 * ligne, trié par jour, nom, prénom. Lecture seule. Ordre des refus : centre (403), type (400), mois (400). Une ligne dont le patient
 * n'est pas du centre est écartée (cloisonnement). Sans pagination ni filtre (KB-SEA-66, reproduit et signalé dans l'ADR 0095).
 */
public class ConsulterLeDetailDuTableauDeBord {

    private final SessionCourante session;
    private final HorlogeDuCentre horloge;
    private final SeancesRealisees seances;
    private final PatientsDuTableauDeBord patients;
    private final AbsencesDuCentre absences;

    public ConsulterLeDetailDuTableauDeBord(SessionCourante session, HorlogeDuCentre horloge, SeancesRealisees seances,
                                            PatientsDuTableauDeBord patients, AbsencesDuCentre absences) {
        this.session = session;
        this.horloge = horloge;
        this.seances = seances;
        this.patients = patients;
        this.absences = absences;
    }

    public Detail executer(UUID centreDemande, int annee, int mois, String type) {
        CentreId centre = session.centre(centreDemande);
        TypeDeDetail genre = TypeDeDetail.lire(type);
        MoisDuTableauDeBord periode = new MoisDuTableauDeBord(annee, mois);

        var identites = patients.identites(centre);
        List<LigneDuDetail> lignes = new ArrayList<>();
        if (genre == TypeDeDetail.PRESENCE) {
            for (var s : seances.du(centre, periode)) ajouter(lignes, identites, s.patient(), s.jour(), s.statut());
        } else {
            LocalDate aujourdhui = horloge.aujourdhui(centre);
            if (periode.aUnJourEchu(aujourdhui)) {
                for (var a : absences.liste(centre, periode.premierJour(), periode.dernierJourEchu(aujourdhui))) {
                    ajouter(lignes, identites, a.patient(), a.jour(), a.statut());
                }
            }
        }
        lignes.sort(LigneDuDetail.ORDRE);
        return new Detail(periode, genre, lignes);
    }

    private static void ajouter(List<LigneDuDetail> lignes, Map<PatientId, PatientsDuTableauDeBord.Identite> identites,
                                PatientId patient, LocalDate jour, String statut) {
        var identite = identites.get(patient);
        if (identite == null || jour == null) return;
        lignes.add(new LigneDuDetail(patient, identite.nom(), identite.prenom(), jour, statut));
    }

    public record Detail(MoisDuTableauDeBord mois, TypeDeDetail type, List<LigneDuDetail> lignes) {
    }
}
