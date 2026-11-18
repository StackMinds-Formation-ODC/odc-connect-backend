# CLAUDE.md — `exception/` (erreurs métier et traduction HTTP)

> Instruction de projet : **lecture seule pour les agents IA**. Complète `docs/instruction/architecture-couches-spring.md` et `docs/memoire/decisions/0004-conventions-erreurs-et-api.md`.

## Rôle

Une seule voie pour les erreurs : les services lèvent des **codes** ; **un seul** endroit (`GlobalExceptionHandler`) les traduit
en statut HTTP et en corps `ProblemDetail` (RFC 9457).

## Ce qu'on crée ici

| Fichier | Contenu |
|---|---|
| `ErrorCode.java` | enum de tous les codes métier (`MEMBER_NOT_FOUND`, …) |
| `BusinessException.java` | exception unique portant un `ErrorCode` et un message français |
| `GlobalExceptionHandler.java` | `@RestControllerAdvice` : code → statut + corps |

Pas une classe d'exception par erreur : ajouter un **code**, pas une classe.

## Gabarits

```java
public enum ErrorCode {
	MEMBER_NOT_FOUND,
	MEMBER_EMAIL_ALREADY_USED,
	MEMBER_INVALID_STATUS_TRANSITION,
	INVALID_SORT_FIELD,
	DATA_CONFLICT          // violation de contrainte détectée par la base
}

public class BusinessException extends RuntimeException {
	private final ErrorCode code;

	public BusinessException(ErrorCode code, String message) {
		super(message);
		this.code = code;
	}

	public ErrorCode getCode() { return code; }
}

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	ProblemDetail handleBusiness(BusinessException ex) {
		return problem(statusOf(ex.getCode()), ex.getCode(), ex.getMessage());
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail handleIntegrity(DataIntegrityViolationException ex) {
		log.warn("Violation de contrainte : {}", ex.getMostSpecificCause().getMessage());
		return problem(HttpStatus.CONFLICT, ErrorCode.DATA_CONFLICT, "Conflit avec des données existantes");
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		log.error("Erreur inattendue", ex);                       // trace dans les logs, JAMAIS dans la réponse
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne");
	}

	// switch exhaustif SANS default : ajouter un ErrorCode ne compile plus tant qu'il n'a pas de statut.
	private static HttpStatus statusOf(ErrorCode code) {
		return switch (code) {
			case MEMBER_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case MEMBER_EMAIL_ALREADY_USED, DATA_CONFLICT -> HttpStatus.CONFLICT;
			case MEMBER_INVALID_STATUS_TRANSITION -> HttpStatus.UNPROCESSABLE_ENTITY;
			case INVALID_SORT_FIELD -> HttpStatus.BAD_REQUEST;
		};
	}

	private static ProblemDetail problem(HttpStatus status, ErrorCode code, String detail) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
		pd.setProperty("code", code.name());
		return pd;
	}
}
```

Corps renvoyé : `{"type":"about:blank","title":"Not Found","status":404,"detail":"Membre introuvable : …","code":"MEMBER_NOT_FOUND"}`.

## Règles

- **Codes** : `ENTITE_RAISON` en `UPPER_SNAKE_CASE` (`MEMBER_NOT_FOUND`). Génériques sans préfixe seulement s'ils sont vraiment
  transverses (`INVALID_SORT_FIELD`, `DATA_CONFLICT`). Un code ne se supprime ni ne se renomme (le client le lit) sans décision.
- **Statuts** : `*_NOT_FOUND` 404 · doublon/conflit 409 · règle métier violée mais requête bien formée 422 (ou 400 si c'est une
  entrée invalide) · validation 400 · non authentifié 401 · interdit 403 · jamais 500 volontaire.
- **Message** : français, utile (quelle ressource, quelle valeur — hors données sensibles). Pas de SQL, de chemin, de trace.
- Étendre `ResponseEntityExceptionHandler` donne déjà un `ProblemDetail` pour les erreurs Spring MVC (corps illisible, paramètre de
  type invalide → 400, méthode non supportée → 405). Pour exposer les erreurs **champ par champ** d'une validation `@Valid`,
  surcharger `handleMethodArgumentNotValid` et ajouter une propriété `errors` (liste `{field, message}`) ; ne jamais renvoyer l'objet rejeté.
- **401 / 403 de sécurité** : produits par Spring Security (filtres) avant les contrôleurs. Un `AccessDeniedException` levé par
  `@PreAuthorize` peut être capté ici pour un corps cohérent (403) ; l'authentification manquante (401) reste gérée par la chaîne de
  filtres (`config/SecurityConfig`).
- **Journalisation** : `warn` pour les erreurs attendues du client, `error` + trace **une seule fois**, ici, pour l'inattendu.
  Ne pas logger puis relancer dans les services.
- `@Order` : un seul `@RestControllerAdvice` global. Pas d'`@ExceptionHandler` dans les contrôleurs.

## Interdits

- `ResponseStatusException`, `HttpException`-like, ou statut HTTP choisi dans un service/contrôleur.
- `catch (Exception)` qui avale ; message d'erreur technique renvoyé au client ; `printStackTrace()`.
- Classes d'exception métier multiples (`MemberNotFoundException`…) : un code suffit.

## Tests

- `exception/GlobalExceptionHandlerTest` : un cas par `ErrorCode` → statut attendu (ce test échoue vite si un code est oublié) ;
  le corps contient `code`. Via `@WebMvcTest` d'un contrôleur de test ou en appelant le handler directement.
- Les tests de contrôleur vérifient le corps d'erreur de leurs scénarios.

## Checklist « nouveau code d'erreur »

1. Ajouter la constante dans `ErrorCode`. 2. Compiler : le `switch` de `statusOf` impose le statut. 3. Lever
`new BusinessException(code, "message français")` côté service. 4. Test du statut.
