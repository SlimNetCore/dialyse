package com.hemodialyse.backend.infrastructure.persistence.mapper;

import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.model.PatientType;
import com.hemodialyse.backend.domain.patient.vo.AssureInfo;
import com.hemodialyse.backend.domain.patient.vo.JoursDialyse;
import com.hemodialyse.backend.domain.patient.vo.NumeroAssurance;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.entity.PatientJpaEntity;

import java.time.LocalDate;

/**
 * Maps between domain Patient and PatientJpaEntity.
 */
public final class PatientMapper {
    private PatientMapper() {}

    public static Patient toDomain(PatientJpaEntity e) {
        Patient p = new Patient();
        p.setId(PatientId.of(e.getId()));
        p.setCenterId(CenterId.of(e.getCenterId()));
        p.setCodePatient(e.getCodePatient());
        p.setCivilite(e.getCivilite());
        p.setNom(e.getNom());
        p.setPrenom(e.getPrenom());
        p.setSexe(e.getSexe());
        p.setGroupeSanguin(e.getGroupeSanguin());
        p.setNombreEnfants(e.getNombreEnfants() != null ? e.getNombreEnfants() : 0);
        p.setDateAdmission(e.getDateAdmission());
        p.setEnSommeil(Boolean.TRUE.equals(e.getEnSommeil()));
        p.setDateNaissance(e.getDateNaissance());
        p.setLieuNaissance(e.getLieuNaissance());
        p.setSituationFamiliale(e.getSituationFamiliale());
        p.setProfession(e.getProfession());
        p.setAdresse(e.getAdresse());
        p.setTelPersonnel(e.getTelPersonnel());
        p.setTelMobile(e.getTelMobile());
        p.setTelBureau(e.getTelBureau());
        p.setEmail(e.getEmail());
        p.setNumeroAssurance(new NumeroAssurance(e.getNumeroAssurance()));
        p.setTypePatient(PatientType.valueOf(e.getTypePatient()));
        p.setEtatPatient(e.getEtatPatient());
        p.setDateEvenementEtat(e.getDateEvenementEtat());
        p.setQualiteAssure(e.getQualiteAssure());
        p.setAssureNumeroAssurance(e.getAssureNumeroAssurance());
        p.setObservation(e.getObservation());
        p.setSousKt(Boolean.TRUE.equals(e.getSousKt()));
        p.setEpoEnabled(Boolean.TRUE.equals(e.getEpoEnabled()));
        p.setEpoDate(e.getEpoDate());
        p.setFerEnabled(Boolean.TRUE.equals(e.getFerEnabled()));
        p.setFerDate(e.getFerDate());
        p.setPhotoBase64(e.getPhotoBase64());
        p.setCentrePayeurId(e.getCentrePayeurId());
        p.setMedecinTraitantId(e.getMedecinTraitantId());
        p.setSalleId(e.getSalleId());
        p.setPositionId(e.getPositionId());
        p.setTransporteurAllerId(e.getTransporteurAllerId());
        p.setTransporteurRetourId(e.getTransporteurRetourId());
        p.setCategorieTransportId(e.getCategorieTransportId());
        p.setGenerateurId(e.getGenerateurId());
        p.setJoursDialyse(new JoursDialyse(
            Boolean.TRUE.equals(e.getJourDimanche()), Boolean.TRUE.equals(e.getJourLundi()),
            Boolean.TRUE.equals(e.getJourMardi()), Boolean.TRUE.equals(e.getJourMercredi()),
            Boolean.TRUE.equals(e.getJourJeudi()), Boolean.TRUE.equals(e.getJourVendredi()),
            Boolean.TRUE.equals(e.getJourSamedi())
        ));
        p.setAssureInfo(new AssureInfo(
            e.getAssureSexe(), e.getAssureNom(), e.getAssurePrenom(),
            e.getAssureDateNaissance() != null ? e.getAssureDateNaissance().toString() : null,
            e.getAssureTelPersonnel(), e.getAssureAdresse(), e.getAssureGroupeSanguin(),
            e.getAssureTelMobile(), e.getAssureTelBureau()
        ));
        p.setAssureHistoryJson(e.getAssureHistoryJson());
        p.setCreatedAt(e.getCreatedAt());
        return p;
    }

    public static PatientJpaEntity toJpa(Patient p) {
        PatientJpaEntity e = new PatientJpaEntity();
        e.setId(p.getId().value());
        e.setCenterId(p.getCenterId().value());
        e.setCodePatient(p.getCodePatient());
        e.setCivilite(p.getCivilite());
        e.setNom(p.getNom());
        e.setPrenom(p.getPrenom());
        e.setSexe(p.getSexe());
        e.setGroupeSanguin(p.getGroupeSanguin());
        e.setNombreEnfants(p.getNombreEnfants());
        e.setDateAdmission(p.getDateAdmission());
        e.setEnSommeil(p.isEnSommeil());
        e.setDateNaissance(p.getDateNaissance());
        e.setLieuNaissance(p.getLieuNaissance());
        e.setSituationFamiliale(p.getSituationFamiliale());
        e.setProfession(p.getProfession());
        e.setAdresse(p.getAdresse());
        e.setTelPersonnel(p.getTelPersonnel());
        e.setTelMobile(p.getTelMobile());
        e.setTelBureau(p.getTelBureau());
        e.setEmail(p.getEmail());
        e.setNumeroAssurance(p.getNumeroAssurance().value());
        e.setTypePatient(p.getTypePatient().name());
        e.setEtatPatient(p.getEtatPatient());
        e.setDateEvenementEtat(p.getDateEvenementEtat());
        e.setQualiteAssure(p.getQualiteAssure());
        e.setAssureNumeroAssurance(p.getAssureNumeroAssurance());
        e.setObservation(p.getObservation());
        e.setSousKt(p.isSousKt());
        e.setEpoEnabled(p.isEpoEnabled());
        e.setEpoDate(p.getEpoDate());
        e.setFerEnabled(p.isFerEnabled());
        e.setFerDate(p.getFerDate());
        e.setPhotoBase64(p.getPhotoBase64());
        e.setCentrePayeurId(p.getCentrePayeurId());
        e.setMedecinTraitantId(p.getMedecinTraitantId());
        e.setSalleId(p.getSalleId());
        e.setPositionId(p.getPositionId());
        e.setTransporteurAllerId(p.getTransporteurAllerId());
        e.setTransporteurRetourId(p.getTransporteurRetourId());
        e.setCategorieTransportId(p.getCategorieTransportId());
        e.setGenerateurId(p.getGenerateurId());
        if (p.getJoursDialyse() != null) {
            e.setJourDimanche(p.getJoursDialyse().dimanche());
            e.setJourLundi(p.getJoursDialyse().lundi());
            e.setJourMardi(p.getJoursDialyse().mardi());
            e.setJourMercredi(p.getJoursDialyse().mercredi());
            e.setJourJeudi(p.getJoursDialyse().jeudi());
            e.setJourVendredi(p.getJoursDialyse().vendredi());
            e.setJourSamedi(p.getJoursDialyse().samedi());
        }
        if (p.getAssureInfo() != null) {
            e.setAssureNom(p.getAssureInfo().nom());
            e.setAssurePrenom(p.getAssureInfo().prenom());
            e.setAssureSexe(p.getAssureInfo().sexe());
            if (p.getAssureInfo().dateNaissance() != null) {
                try { e.setAssureDateNaissance(LocalDate.parse(p.getAssureInfo().dateNaissance())); } catch (Exception ignored) {}
            }
            e.setAssureTelPersonnel(p.getAssureInfo().telPersonnel());
            e.setAssureTelMobile(p.getAssureInfo().telMobile());
            e.setAssureTelBureau(p.getAssureInfo().telBureau());
            e.setAssureAdresse(p.getAssureInfo().adresse());
            e.setAssureGroupeSanguin(p.getAssureInfo().groupeSanguin());
        }
        e.setAssureHistoryJson(p.getAssureHistoryJson());
        e.setPiecesJointesJson(p.getPiecesJointesJson());
        e.setCreatedAt(p.getCreatedAt());
        return e;
    }
}
