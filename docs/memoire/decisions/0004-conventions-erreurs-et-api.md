# 0004 — Conventions d'erreurs et d'API

Date : 2026-11-17 · Statut : **convention proposée, à valider** (aucun contrôleur n'existe encore)

1. **Erreurs** : `BusinessException(ErrorCode, message)` unique côté services ; `ErrorCode` énumère les codes
   `ENTITE_RAISON` ; `GlobalExceptionHandler` (`@RestControllerAdvice`) associe code -> statut HTTP et renvoie un
   `ProblemDetail` (RFC 9457) avec la propriété `code`. Reprise de `BusinessError` + `BusinessErrorFilter` d'`app-service`,
   avec le format de réponse natif de Spring au lieu de `{ code, message }`.
2. **Statuts** : `*_NOT_FOUND` -> 404 ; doublon / conflit -> 409 ; validation Bean Validation -> 400 ; non authentifié -> 401 ;
   interdit -> 403. Pas de `500` volontaire.
3. **Routes** : `/api/<ressources-pluriel-kebab>/{publicId}` — même préfixe qu'`app-service`, sans numéro de version tant qu'il
   n'y a qu'un client.
4. **Pagination** : `Pageable` Spring (`page`, `size`, `sort`) en entrée, `Page<…Response>` en sortie.
5. **Messages d'erreur en français** ; identifiants et codes en anglais.

Écart assumé par rapport à `app-service` : il n'y a pas de couche `domain` séparée ; les codes d'erreur vivent dans `exception/`.
