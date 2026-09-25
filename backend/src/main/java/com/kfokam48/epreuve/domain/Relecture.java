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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Relecture d'un exercice par un pair, une seule par exercice (RG8).
 * Le relecteur est tiré au sort par le système, jamais transmis par la requête.
 */
@Entity
@Table(name = "relectures")
public class Relecture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercice_id", nullable = false, unique = true)
    private Exercice exercice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "relecteur_id", nullable = false)
    private Etudiant relecteur;

    /** Entier de 0 à 20 (RG6), null tant que la relecture n'est pas rendue. */
    @Column
    private Integer note;

    @Column
    private String commentaire;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private StatutRelecture statut;

    @Column(name = "attribuee_at", nullable = false)
    private Instant attribueeAt;

    @Column(name = "rendue_at")
    private Instant rendueAt;

    @Column(name = "modifiee_at")
    private Instant modifieeAt;

    protected Relecture() {
        // requis par JPA
    }

    public Relecture(Exercice exercice, Etudiant relecteur, Instant attribueeAt) {
        this.exercice = exercice;
        this.relecteur = relecteur;
        this.statut = StatutRelecture.EN_ATTENTE;
        this.attribueeAt = attribueeAt;
    }

    /** EF5 : première soumission de la note et du commentaire. */
    public void rendre(int note, String commentaire, Instant rendueAt) {
        if (statut != StatutRelecture.EN_ATTENTE) {
            throw new IllegalStateException("Cette relecture a déjà été rendue.");
        }
        this.note = note;
        this.commentaire = commentaire;
        this.statut = StatutRelecture.RENDUE;
        this.rendueAt = rendueAt;
    }

    /** EF6 : correction d'une relecture déjà rendue, tant que la session est ouverte (décision Q10, RG9). */
    public void modifier(int note, String commentaire, Instant modifieeAt) {
        if (statut != StatutRelecture.RENDUE) {
            throw new IllegalStateException("Seule une relecture rendue peut être modifiée.");
        }
        this.note = note;
        this.commentaire = commentaire;
        this.modifieeAt = modifieeAt;
    }

    public Long getId() {
        return id;
    }

    public Exercice getExercice() {
        return exercice;
    }

    public Etudiant getRelecteur() {
        return relecteur;
    }

    public Integer getNote() {
        return note;
    }

    public String getCommentaire() {
        return commentaire;
    }

    public StatutRelecture getStatut() {
        return statut;
    }

    public Instant getAttribueeAt() {
        return attribueeAt;
    }

    public Instant getRendueAt() {
        return rendueAt;
    }

    public Instant getModifieeAt() {
        return modifieeAt;
    }
}
