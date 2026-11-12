package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.DelaiDEcheance;
import com.hemodialyse.backend.contexts.patientcouverture.domain.MoisDuTableauDeBord;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CompteursDeCouverture;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CompteursDeCouverture.StatutDePec;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

/**
 * Tableau de bord administratif du centre (PL3, ex {@code DashboardRestController}) : prises en charge, attestations et patients. Garde
 * de centre d'abord (KB-PL3-1), puis validation du délai et du mois (400, KB-PL3-4) ; l'échéance et l'effectif se calculent au JOUR DU
 * CENTRE (PC4, KB-PAT-47), un seul « aujourd'hui » par réponse. Sans mois : effectif du jour ; avec mois : patients créés dans le mois.
 */
public class ConsulterLeTableauDeBordAdministratif {

    private final SessionCourante session;
    private final CompteursDeCouverture compteurs;
    private final HorlogeDesCentres horloge;

    public ConsulterLeTableauDeBordAdministratif(SessionCourante session, CompteursDeCouverture compteurs, HorlogeDesCentres horloge) {
        this.session = session;
        this.compteurs = compteurs;
        this.horloge = horloge;
    }

    /** @param delai jours avant l'échéance, nul : 30 ; @param mois AAAA-MM, nul ou vide : sans filtre. */
    public TableauDeBordAdministratif executer(UUID centreDemande, Integer delai, String mois) {
        CentreId centre = session.centre(centreDemande);
        DelaiDEcheance echeance = DelaiDEcheance.de(delai);
        Optional<YearMonth> filtre = MoisDuTableauDeBord.lire(mois);

        UUID c = centre.valeur();
        LocalDate aujourdhui = horloge.aujourdhui(centre);
        LocalDate seuil = echeance.seuilAu(aujourdhui);
        LocalDate du = filtre.map(m -> m.atDay(1)).orElse(null);
        LocalDate au = filtre.map(YearMonth::atEndOfMonth).orElse(null);

        long patients = filtre.isPresent() ? compteurs.patientsCrees(c, du, au) : compteurs.patientsPresentsLe(c, aujourdhui);
        return new TableauDeBordAdministratif(
                compteurs.prisesEnCharge(c, StatutDePec.CREE, du, au),
                compteurs.prisesEnCharge(c, StatutDePec.VALIDEE, du, au),
                compteurs.prisesEnChargeValideesAEcheance(c, aujourdhui, seuil),
                compteurs.attestations(c, du, au),
                compteurs.attestationsAEcheance(c, aujourdhui, seuil),
                patients, echeance.jours(), filtre.orElse(null));
    }
}
