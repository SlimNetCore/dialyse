package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.AlerteDObservance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Administrations;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Alertes;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DetailDObservance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.MesureDObservance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PeriodeDObservance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Prescriptions;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Traitement;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeDAlerte;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeDeTraitement;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.NotificationsDeLAnemie;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Le contrôle de la nuit (06:30) de l'observance du traitement de l'anémie : pour la prescription la plus récente de chaque patient, un
 * RETARD constaté sur la période précédente et un RAPPEL d'échéance sur la période en cours.
 * <p>
 * REPRODUIT à l'identique (KB-MED-28, à confirmer avec les métiers) : (a) le message du rappel est mal formé ; (b) un rappel ouvert n'est
 * jamais mis à jour ; (c) l'alerte durable au médecin est renvoyée à CHAQUE nuit tant que l'alerte est ouverte ; (d) une alerte de retard
 * acquittée est rouverte la nuit suivante tant que la période précédente est la même ; (e) seule la prescription la plus récente est
 * contrôlée (si plusieurs ont la même date maximale, toutes le sont). Le jour est celui de l'horloge injectée (jour de la JVM : KB-MED-15,
 * le passage au jour du centre est une décision ouverte).
 */
public class ControlerLObservance {

    private final Prescriptions prescriptions;
    private final Administrations administrations;
    private final Alertes alertes;
    private final CentresActifs centres;
    private final UniteDeTravail uniteDeTravail;
    private final GenerateurDIdentifiants identifiants;
    private final HorlogeDuCentre horloge;
    private final NotificationsDeLAnemie notifications;

    public ControlerLObservance(Prescriptions prescriptions, Administrations administrations, Alertes alertes, CentresActifs centres,
                                UniteDeTravail uniteDeTravail, GenerateurDIdentifiants identifiants, HorlogeDuCentre horloge,
                                NotificationsDeLAnemie notifications) {
        this.prescriptions = prescriptions;
        this.administrations = administrations;
        this.alertes = alertes;
        this.centres = centres;
        this.uniteDeTravail = uniteDeTravail;
        this.identifiants = identifiants;
        this.horloge = horloge;
        this.notifications = notifications;
    }

    /** La liste des centres à contrôler. */
    public List<CentreId> centres() {
        return centres.tous();
    }

    public void controlerLeCentre(CentreId centre) {
        for (var prescription : prescriptions.lesPlusRecentesDuCentre(centre)) {
            var contenu = prescription.contenu();
            controlerLeTraitement(centre, prescription.patient(), TypeDeTraitement.EPO, contenu.date(), contenu.epo());
            controlerLeTraitement(centre, prescription.patient(), TypeDeTraitement.FER_INJECTABLE, contenu.date(), contenu.fer());
        }
    }

    private void controlerLeTraitement(CentreId centre, PatientId patient, TypeDeTraitement type, LocalDate datePrescription, Traitement t) {
        if (t.article() == null || t.frequenceValeur() == null || t.frequenceValeur() <= 0 || t.frequence() == null) return;
        LocalDate aujourdhui = horloge.aujourdhui(centre);
        int fenetre = PeriodeDObservance.fenetreEnJours(t.frequence().name());
        uniteDeTravail.executer(() -> {
            controlerLaPeriodePrecedente(centre, patient, type, datePrescription, fenetre, t, aujourdhui);
            controlerLaPeriodeEnCours(centre, patient, type, datePrescription, fenetre, t, aujourdhui);
            return null;
        });
    }

    private MesureDObservance mesurer(CentreId centre, PatientId patient, TypeDeTraitement type, PeriodeDObservance periode, Traitement t) {
        var administre = administrations.administreSur(centre, patient, type, periode.debut(), periode.fin());
        return MesureDObservance.calculer(t.frequenceValeur(), t.dose(), type.unite(), administre.nombre(), administre.dose());
    }

    private void controlerLaPeriodePrecedente(CentreId centre, PatientId patient, TypeDeTraitement type, LocalDate datePrescription, int fenetre,
                                              Traitement t, LocalDate aujourdhui) {
        PeriodeDObservance precedente = PeriodeDObservance.precedente(datePrescription, fenetre, aujourdhui);
        if (precedente == null) return;
        MesureDObservance mesure = mesurer(centre, patient, type, precedente, t);
        if (!mesure.enRetard()) return;
        String message = mesure.messageDeRetard(type, precedente.debut(), precedente.fin(), t.frequenceValeur(), t.frequence().name());
        signaler(centre, patient, type, TypeDAlerte.RETARD_CONSTATE, precedente, t, mesure, message);
    }

    private void controlerLaPeriodeEnCours(CentreId centre, PatientId patient, TypeDeTraitement type, LocalDate datePrescription, int fenetre,
                                           Traitement t, LocalDate aujourdhui) {
        PeriodeDObservance courante = PeriodeDObservance.courante(datePrescription, fenetre, aujourdhui);
        MesureDObservance mesure = mesurer(centre, patient, type, courante, t);
        long joursRestants = courante.joursRestants(aujourdhui);
        if (mesure.enRetard() && joursRestants <= PeriodeDObservance.seuilDeRappelEnJours(fenetre)) {
            signaler(centre, patient, type, TypeDAlerte.RAPPEL_ECHEANCE, courante, t, mesure, mesure.messageDeRappel(type, courante.fin(), joursRestants));
        } else {
            alertes.ouverte(centre, patient, type, TypeDAlerte.RAPPEL_ECHEANCE).ifPresent(a -> {
                a.resoudre(horloge.maintenant());
                alertes.enregistrerLaResolution(a);
            });
        }
    }

    private void signaler(CentreId centre, PatientId patient, TypeDeTraitement type, TypeDAlerte nature, PeriodeDObservance periode, Traitement t,
                          MesureDObservance mesure, String message) {
        var detail = new DetailDObservance(mesure.unite(), mesure.dosePrescrite(), t.frequenceValeur(), t.frequence().name());
        String texte = message.length() > 500 ? message.substring(0, 499) + "…" : message;
        if (alertes.ouverte(centre, patient, type, nature).isEmpty()) {
            alertes.ajouter(AlerteDObservance.declencher(identifiants.suivant(), patient, centre, type, nature, periode.debut(), periode.fin(),
                    mesure.attendu(), mesure.administre(), texte, detail, horloge.maintenant()));
        }
        Map<String, String> details = new HashMap<>();
        details.put("typeAlerte", nature.name());
        details.put("typeTraitement", type.name());
        details.put("debut", periode.debut().toString());
        details.put("fin", periode.fin().toString());
        details.put("attendu", String.valueOf(mesure.attendu()));
        details.put("administre", String.valueOf(mesure.administre()));
        details.put("unite", mesure.unite() == null ? "" : mesure.unite());
        notifications.observanceNonRespectee(centre.valeur(), patient.valeur(), message, details);
    }
}
