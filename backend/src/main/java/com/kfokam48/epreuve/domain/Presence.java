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

/** Présence d'un étudiant à une session, unique par couple (session, étudiant) (RG3). */
@Entity
@Table(name = "presences",
        uniqueConstraints = @UniqueConstraint(name = "uk_presences_session_etudiant",
                columnNames = {"session_id", "etudiant_id"}))
public class Presence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private SessionCours session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "etudiant_id", nullable = false)
    private Etudiant etudiant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SourcePresence source;

    @Column(name = "marquee_at", nullable = false)
    private Instant marqueeAt;

    protected Presence() {
        // requis par JPA
    }

    public Presence(SessionCours session, Etudiant etudiant, Instant marqueeAt) {
        this.session = session;
        this.etudiant = etudiant;
        this.source = SourcePresence.CODE;
        this.marqueeAt = marqueeAt;
    }

    public Long getId() {
        return id;
    }

    public SessionCours getSession() {
        return session;
    }

    public Etudiant getEtudiant() {
        return etudiant;
    }

    public SourcePresence getSource() {
        return source;
    }

    public Instant getMarqueeAt() {
        return marqueeAt;
    }
}
