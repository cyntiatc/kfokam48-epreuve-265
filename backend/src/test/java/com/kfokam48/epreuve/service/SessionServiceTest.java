package com.kfokam48.epreuve.service;

import com.kfokam48.epreuve.domain.Promotion;
import com.kfokam48.epreuve.domain.SessionCours;
import com.kfokam48.epreuve.domain.StatutSession;
import com.kfokam48.epreuve.erreur.CodeErreur;
import com.kfokam48.epreuve.erreur.ErreurMetierException;
import com.kfokam48.epreuve.repository.PromotionRepository;
import com.kfokam48.epreuve.repository.SessionCoursRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-09-28T08:00:00.123456Z"), ZoneOffset.UTC);

    @Mock
    private SessionCoursRepository sessionRepository;
    @Mock
    private PromotionRepository promotionRepository;
    @Mock
    private GenerateurCode generateurCode;

    private SessionService service;

    @BeforeEach
    void setUp() {
        service = new SessionService(sessionRepository, promotionRepository, generateurCode, HORLOGE);
    }

    @Test
    void ouvrirSession_creeUneSessionOuverteValable15Minutes() {
        Promotion promotion = new Promotion("L3 GL");
        when(promotionRepository.findById(3L)).thenReturn(Optional.of(promotion));
        when(generateurCode.generer()).thenReturn("K7P2QX");
        when(sessionRepository.existsByCode("K7P2QX")).thenReturn(false);
        when(sessionRepository.save(any(SessionCours.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SessionCours session = service.ouvrirSession("  Spring Boot - API REST  ", 3L);

        assertThat(session.getPromotion()).isSameAs(promotion);
        assertThat(session.getTitre()).isEqualTo("Spring Boot - API REST");
        assertThat(session.getCode()).isEqualTo("K7P2QX");
        assertThat(session.getStatut()).isEqualTo(StatutSession.OUVERTE);
        assertThat(session.getClotureAt()).isNull();
        // RG1 : ouverture à l'heure du serveur (à la seconde), expiration 15 minutes plus tard.
        assertThat(session.getOuvertureAt()).isEqualTo(Instant.parse("2026-09-28T08:00:00Z"));
        assertThat(session.getExpirationAt()).isEqualTo(Instant.parse("2026-09-28T08:15:00Z"));
    }

    @Test
    void ouvrirSession_regenereLeCodeTantQuIlEstDejaUtilise() {
        when(promotionRepository.findById(3L)).thenReturn(Optional.of(new Promotion("L3 GL")));
        when(generateurCode.generer()).thenReturn("AAAAAA", "BBBBBB");
        when(sessionRepository.existsByCode("AAAAAA")).thenReturn(true);
        when(sessionRepository.existsByCode("BBBBBB")).thenReturn(false);
        when(sessionRepository.save(any(SessionCours.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SessionCours session = service.ouvrirSession("Spring Boot", 3L);

        assertThat(session.getCode()).isEqualTo("BBBBBB");
        verify(generateurCode, times(2)).generer();
    }

    @Test
    void ouvrirSession_echoueSiAucunCodeLibreApresLeNombreMaximalDeTentatives() {
        when(promotionRepository.findById(3L)).thenReturn(Optional.of(new Promotion("L3 GL")));
        when(generateurCode.generer()).thenReturn("AAAAAA");
        when(sessionRepository.existsByCode("AAAAAA")).thenReturn(true);

        assertThatThrownBy(() -> service.ouvrirSession("Spring Boot", 3L))
                .isInstanceOf(IllegalStateException.class);
        verify(generateurCode, times(SessionService.MAX_TENTATIVES_CODE)).generer();
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void ouvrirSession_refuseUnePromotionInconnue() {
        when(promotionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ouvrirSession("Spring Boot", 99L))
                .isInstanceOfSatisfying(ErreurMetierException.class, erreur -> {
                    assertThat(erreur.getCode()).isEqualTo(CodeErreur.REQUETE_INVALIDE);
                    assertThat(erreur.getMessage()).isEqualTo("Aucune promotion ne correspond à l'identifiant 99.");
                });
        verify(sessionRepository, never()).save(any());
    }
}
