# [BUG] Perte d'émargement lors de validations de présence simultanées (Race Condition)

**Route concernée :** `POST /api/presences` (EF2) — **Règles :** RG3, RG8, ENF3 — **Étape :** 3 (Enveloppe)

**Statut :** non reproductible sur le code actuel, protégé depuis le ticket #4.
**Traitement retenu :** test de non-régression, sans modification du code de production (voir « Résolution retenue »).

---

## Contexte métier

Retour du client, transmis par le surveillant :

> « J'ai ouvert une session ce matin avec deux étudiants côte à côte. Ils ont tapé le code presque en même temps
> et il n'y en a qu'un seul qui apparaît dans ma liste. J'ai réessayé une fois, cette fois les deux sont passés.
> Je ne comprends pas. »

Trois faits à expliquer : les deux envois sont **quasi simultanés**, **un seul** émargement est conservé, et
**une nouvelle tentative réussit**.

---

## Analyse technique

### Ce que fait `POST /api/presences`

`PresenceService.marquerPresence` s'exécute dans **une seule transaction** :

1. contrôles de H6 (code connu, étudiant de la promotion, pas déjà présent, code encore valide) ;
2. insertion de la présence ;
3. attribution des exercices de la session encore sans relecteur (`AttributionService.attribuerExercicesEnAttente`,
   EF4) : le nouvel étudiant présent devient relecteur possible.

L'insertion seule ne peut pas faire perdre une présence : la contrainte `UNIQUE (session_id, etudiant_id)` ne
concerne qu'un même étudiant, et deux étudiants différents n'entrent pas en conflit.

### Mécanisme de la perte

Le point de contention est l'**étape 3**. Lorsqu'un exercice de la session attend un relecteur (statut `DEPOSE`),
les deux émargements simultanés tentent de lui attribuer un relecteur **en même temps** :

| Instant | Émargement de l'étudiant 1 | Émargement de l'étudiant 2 |
|---|---|---|
| t1 | insère sa présence | insère sa présence |
| t2 | lit l'exercice E : `DEPOSE` | lit l'exercice E : `DEPOSE` |
| t3 | insère la relecture de E, valide | insère la relecture de E |
| t4 | **201** | violation de `uk_relectures_exercice` (un seul relecteur par exercice, RG8) |
| t5 | | `DataIntegrityViolationException` non interceptée : **500 `ERREUR_INTERNE`** |
| t6 | | **annulation de toute la transaction, présence comprise** |

Le `catch (DataIntegrityViolationException)` de `marquerPresence` n'entoure que l'insertion de la présence :
l'erreur levée par l'attribution remonte en 500, et la présence de l'étudiant 2 disparaît avec la transaction.

Ce scénario explique les trois faits rapportés : envois simultanés, un seul étudiant enregistré, et une nouvelle
tentative qui réussit, puisque l'exercice est alors déjà `EN_RELECTURE` et qu'il n'y a plus de conflit.

### État du code actuel

La protection existe depuis le commit `4a53803` (ticket #4), le même commit que l'attribution déclenchée par
l'émargement : `ExerciceRepository.findBySessionIdAndStatutOrderByDeposeAtAsc` verrouille les exercices en attente
(`@Lock(PESSIMISTIC_WRITE)`, soit `SELECT … FOR UPDATE`). Le second émargement attend la fin du premier, relit
l'exercice, désormais `EN_RELECTURE`, et l'ignore.

Résultats du test de reproduction (26/09/2026, PostgreSQL 17, Testcontainers) :

| Code testé | Résultat |
|---|---|
| Code actuel du dépôt | **vert** : 7 exécutions de 2 × 20 tentatives simultanées, aucune présence perdue |
| Copie du backend sans le verrou (hors dépôt) | **rouge dès la 1ʳᵉ tentative** : `500 ERREUR_INTERNE` pour l'étudiant 1, `201` pour l'étudiant 2, cause `duplicate key value violates unique constraint "uk_relectures_exercice"` |

**Le bug décrit n'est donc pas reproductible sur le code actuel.** Le test prouve à la fois le mécanisme (il échoue
sans le verrou) et la protection (il passe avec).

### Autres explications à vérifier avec le client

- **Tableau non rafraîchi :** le tableau récapitulatif se charge uniquement quand le formateur clique sur le bouton du
  formulaire, sans rafraîchissement automatique. Consulté entre les deux émargements, il n'en montre qu'un.
- **Erreur de saisie :** si un étudiant saisit l'identifiant de son voisin, une seule présence est enregistrée
  (celle du voisin), et le second des deux envois reçoit `409 DEJA_PRESENT`.

---

## Procédure de reproduction

### Automatisée (fiable)

Docker Desktop doit être lancé.

```bash
cd backend
mvn test -Dtest=PresenceConcurrencyIntegrationTest
```

Le test `PresenceConcurrencyIntegrationTest` rejoue chaque scénario 20 fois. Pour chaque tentative, il envoie
les deux requêtes depuis deux threads libérés au même instant (`CountDownLatch`).

1. **Sans exercice en attente :** ouvrir une session de la promotion 1, puis faire émarger simultanément
   les étudiants 1 et 2. Attendu : deux réponses `201` et **2 présences** en base.
2. **Avec un exercice en attente :** ouvrir une session de la promotion 1, déposer l'exercice de l'étudiant 3
   (personne n'est présent, il reste `DEPOSE`), puis faire émarger simultanément les étudiants 1 et 2.
   Attendu : deux réponses `201`, **2 présences** en base, et **1 seul relecteur** pour l'exercice.

### Manuelle (backend démarré sur le port 8080)

La fenêtre de concurrence ne dure que quelques millisecondes : à la main, il faut en général plusieurs tentatives.

1. Ouvrir une session pour la promotion 1 et noter `id` et `code` :
   ```bash
   curl -s -X POST http://localhost:8080/api/sessions -H "Content-Type: application/json" \
        -d '{"titre": "Reproduction #17", "promotionId": 1}'
   ```
2. Déposer l'exercice de l'étudiant 3. Personne n'étant présent, la réponse indique `"statut": "DEPOSE"` :
   ```bash
   curl -s -X POST http://localhost:8080/api/exercices -H "Content-Type: application/json" \
        -d '{"sessionId": <id>, "etudiantId": 3, "lien": "https://github.com/exemple/reproduction-17"}'
   ```
3. Faire émarger les étudiants 1 et 2 en même temps :
   ```bash
   for e in 1 2; do
     curl -s -w " [%{http_code}]\n" -X POST http://localhost:8080/api/presences \
          -H "Content-Type: application/json" -d "{\"code\": \"<code>\", \"etudiantId\": $e}" &
   done; wait
   ```
4. Consulter le tableau de la promotion 1 (`GET /api/tableau?promotionId=1`) : chaque étudiant doit compter
   la présence.

---

## Résolution retenue

- **Pas de correctif :** le verrou qui empêche la perte est déjà en place ; modifier le code de production
  n'apporterait rien.
- **Test de non-régression :** `PresenceConcurrencyIntegrationTest` rejoint la suite. Il passe tant que les
  attributions concurrentes restent sérialisées, et échoue si le verrou est retiré : le bug ne peut pas réapparaître
  sans que la suite le signale.
- **Tableau non rafraîchi :** hypothèse à confirmer avec le client. Si elle se confirme, elle fera l'objet d'une issue
  séparée côté frontend.

---

## Critères d'acceptation

- Deux émargements simultanés d'étudiants différents sur la même session renvoient tous deux `201`.
- Les deux présences sont enregistrées en base.
- Chaque exercice en attente reçoit un seul relecteur (RG8).
- `PresenceConcurrencyIntegrationTest` est vert, ainsi que le reste de la suite (`mvn clean test`).
- Le test échoue lorsque le verrou des exercices en attente est retiré (vérifié le 26/09/2026 sur une copie du backend
  hors dépôt).
