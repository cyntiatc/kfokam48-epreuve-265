package com.kfokam48.epreuve.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** Exercice déposé par un étudiant pour une session, unique par couple (session, étudiant) (RG4). */
@Entity
@Table(name = "exercices",
        uniqueConstraints = @UniqueConstraint(name = "uk_exercices_session_etudiant",
                columnNames = {"session_id", "etudiant_id"}))
public class Exercice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private SessionCours session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "etudiant_id", nullable = false)
    private Etudiant auteur;

    @Column(nullable = false, length = 2048)
    private String lien;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutExercice statut;

    @Column(name = "depose_at", nullable = false)
    private Instant deposeAt;

    protected Exercice() {
        // requis par JPA
    }

    public Exercice(SessionCours session, Etudiant auteur, String lien, Instant deposeAt) {
        this.session = session;
        this.auteur = auteur;
        this.lien = lien;
        this.statut = StatutExercice.DEPOSE;
        this.deposeAt = deposeAt;
    }

    /** EF4 : un relecteur vient d'être attribué (D4 : DEPOSE -> EN_RELECTURE). */
    public void passerEnRelecture() {
        if (statut != StatutExercice.DEPOSE) {
            throw new IllegalStateException(
                    "Seul un exercice déposé peut passer en relecture (statut actuel : " + statut + ").");
        }
        statut = StatutExercice.EN_RELECTURE;
    }

    public Long getId() {
        return id;
    }

    public SessionCours getSession() {
        return session;
    }

    public Etudiant getAuteur() {
        return auteur;
    }

    public String getLien() {
        return lien;
    }

    public StatutExercice getStatut() {
        return statut;
    }

    public Instant getDeposeAt() {
        return deposeAt;
    }
}
