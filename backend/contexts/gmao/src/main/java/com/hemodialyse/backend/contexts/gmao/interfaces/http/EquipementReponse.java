package com.hemodialyse.backend.contexts.gmao.interfaces.http;

import com.hemodialyse.backend.contexts.gmao.application.equipement.EquipementVue;
import com.hemodialyse.backend.contexts.gmao.application.equipement.PageDEquipements;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.TypeEquipement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Réponse JSON d'un équipement : mêmes champs, même ordre et mêmes types sérialisés que le legacy
 * ({@code EquipementResponse}, sans le centre) ; les instants sont exposés en {@link OffsetDateTime} UTC pour un rendu
 * identique.
 */
public record EquipementReponse(
        UUID id,
        String code,
        String designation,
        TypeEquipement type,
        String fabricant,
        String modele,
        String numeroSerie,
        OffsetDateTime dateInstallation,
        StatutEquipement statut,
        String localisation,
        String observations,
        OffsetDateTime dateCreation,
        OffsetDateTime dateModification,
        UUID creePar,
        UUID modifiePar,
        UUID salleId,
        BigDecimal prixAcquisition) {

    static EquipementReponse depuis(EquipementVue vue) {
        return new EquipementReponse(vue.id(), vue.code(), vue.designation(), vue.type(), vue.fabricant(), vue.modele(),
                vue.numeroSerie(), utc(vue.dateInstallation()), vue.statut(), vue.localisation(), vue.observations(),
                utc(vue.dateCreation()), utc(vue.dateModification()), vue.creePar(), vue.modifiePar(), vue.salleId(),
                vue.prixAcquisition());
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    /** Page d'équipements : même forme que le contrat observé ({@code items}, {@code total}, {@code page}, {@code size}). */
    public record Page(List<EquipementReponse> items, long total, int page, int size) {

        static Page depuis(PageDEquipements page) {
            return new Page(page.items().stream().map(EquipementReponse::depuis).toList(), page.total(), page.page(),
                    page.size());
        }
    }
}
