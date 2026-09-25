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

import java.time.Duration;
import java.time.Instant;

/** Session de cours ouverte par un formateur pour une promotion (table {@code sessions}). */
@Entity
@Table(name = "sessions")
public class SessionCours {

    /** RG1 : le code de présence est valable 15 minutes à compter de l'ouverture. */
    public static final Duration DUREE_VALIDITE_CODE = Duration.ofMinutes(15);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promotion_id", nullable = false)
    private Promotion promotion;

    @Column(nullable = false, length = 150)
    private String titre;

    @Column(nullable = false, unique = true, length = 6)
    private String code;

    @Column(name = "ouverture_at", nullable = false)
    private Instant ouvertureAt;

    @Column(name = "expiration_at", nullable = false)
    private Instant expirationAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatutSession statut;

    @Column(name = "cloture_at")
    private Instant clotureAt;

    protected SessionCours() {
        // requis par JPA
    }

    public SessionCours(Promotion promotion, String titre, String code, Instant ouvertureAt) {
        this.promotion = promotion;
        this.titre = titre;
        this.code = code;
        this.ouvertureAt = ouvertureAt;
        this.expirationAt = ouvertureAt.plus(DUREE_VALIDITE_CODE);
        this.statut = StatutSession.OUVERTE;
    }

    /** RG1 : le code est accepté si la session est ouverte et que l'instant ne dépasse pas l'expiration. */
    public boolean codeEstValide(Instant instant) {
        return statut == StatutSession.OUVERTE && !instant.isAfter(expirationAt);
    }

    /** EF8 : clôture par le formateur. Irréversible : les notes rendues deviennent définitives (RG9). */
    public void cloturer(Instant clotureAt) {
        if (statut != StatutSession.OUVERTE) {
            throw new IllegalStateException("Seule une session ouverte peut être clôturée.");
        }
        this.statut = StatutSession.CLOTUREE;
        this.clotureAt = clotureAt;
    }

    public Long getId() {
        return id;
    }

    public Promotion getPromotion() {
        return promotion;
    }

    public String getTitre() {
        return titre;
    }

    public String getCode() {
        return code;
    }

    public Instant getOuvertureAt() {
        return ouvertureAt;
    }

    public Instant getExpirationAt() {
        return expirationAt;
    }

    public StatutSession getStatut() {
        return statut;
    }

    public Instant getClotureAt() {
        return clotureAt;
    }
}
