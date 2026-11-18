# CLAUDE.md — `controller/` (entrée HTTP)

> Instruction de projet : **lecture seule pour les agents IA**. Complète `docs/instruction/architecture-couches-spring.md` et `nomenclature-java.md`.

## Rôle

Traduire HTTP ⇄ appel de service. **Mince** : lire la requête, déléguer à **un** service, renvoyer le résultat.
Dépend de `service/`, `dto/`, `security/` (constantes de rôles). **Jamais** de `repository/` ni de `model/`.

## Ce qu'on crée ici

`<Ressource>Controller.java` — un par ressource (`MemberController` pour `/api/members`). Pas de contrôleur « fourre-tout ».

## Gabarit

```java
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

	private final MemberService service;

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<MemberResponse> create(@Valid @RequestBody CreateMemberRequest request) {
		MemberResponse created = service.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
			.path("/{publicId}").buildAndExpand(created.publicId()).toUri();
		return ResponseEntity.created(location).body(created);               // 201 + Location
	}

	@GetMapping("/{publicId}")
	public MemberResponse get(@PathVariable UUID publicId) {                   // 200
		return service.getByPublicId(publicId);
	}

	@GetMapping
	public PageResponse<MemberResponse> list(
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return service.list(pageable);
	}

	@PatchMapping("/{publicId}")
	public MemberResponse update(@PathVariable UUID publicId, @Valid @RequestBody UpdateMemberRequest request) {
		return service.update(publicId, request);
	}

	@DeleteMapping("/{publicId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)                                     // 204
	public void delete(@PathVariable UUID publicId) {
		service.delete(publicId);
	}
}
```

> Le `@PreAuthorize` ci-dessus n'a d'effet que si `@EnableMethodSecurity` est activé dans `config/SecurityConfig`
> (voir `config/CLAUDE.md`). S'il ne l'est pas, il est **ignoré sans erreur** : le vérifier avant de s'y fier.
> Dans `@PreAuthorize` (expression SpEL) le nom du rôle s'écrit en littéral ; il doit rester identique à la constante de `security/Roles`.

## Règles

- **Corps de méthode ≤ 5 lignes** : paramètres → un appel de service → réponse. Aucune boucle, aucun `if` métier, aucun `try/catch`.
- **Chemin** : `/api/<ressources-kebab-pluriel>` au niveau de la classe ; `/{publicId}` (type `UUID`) pour une ressource.
  Un `publicId` mal formé donne 400 automatiquement (conversion Spring).
- **Statuts** : `POST` créer → `201` + en-tête `Location` ; `GET`/`PATCH`/`PUT` → `200` ; `DELETE` → `204` sans corps ;
  action métier → `POST /{publicId}/<verbe>` (ex. `/archive`) plutôt que `PATCH` d'un statut brut quand la règle est riche.
  Pas de `200` avec un corps d'erreur : les erreurs passent par exception.
- **Validation** : `@Valid @RequestBody` pour le corps. Paramètres simples (`@RequestParam`) contraints par `@Min/@Max/@Size` avec
  `@Validated` sur la classe.
- **Pagination** : `Pageable` avec `@PageableDefault`. **Borner** : `spring.data.web.pageable.max-page-size` (valeur par défaut
  2000 : la réduire, ex. 100) et **liste blanche de tris** (constante `ALLOWED_SORTS` ; refuser les autres avec
  `BusinessException(ErrorCode.INVALID_SORT_FIELD)`), sinon le client trie sur n'importe quelle propriété.
- **Types** : entrée `*Request`, sortie `*Response`/`PageResponse` (les types de `dto/`). Jamais d'entité, de `Map`, ni de `Object`.
  `ResponseEntity` seulement quand il faut un en-tête ou un statut variable (création) ; sinon renvoyer le DTO directement.
- **Sécurité** : l'accès grossier est dans `SecurityConfig` (`requestMatchers`) ; l'accès fin par `@PreAuthorize("hasRole('…')")`, avec les
  noms de `security/Roles`. Ne jamais lire le JWT ici : passer par `CurrentUser` ou laisser le service le faire.
- **Pas de logique de présentation** : formats, libellés, arrondis se décident dans le DTO/mapper.
- **Versionnement** : pas de `/v1` tant qu'un seul client existe ; changement incompatible = nouvelle ressource ou nouveau préfixe, décision datée.
- Documentation OpenAPI : non installée ; ne pas ajouter de dépendance sans décision.

## Interdits

- Import de `repository`, `model`, `org.springframework.data.*` autre que `Pageable`/`Sort`.
- `@Transactional`, `try/catch`, `ResponseStatusException`, `@ExceptionHandler` local (tout est dans `GlobalExceptionHandler`).
- Appeler deux services pour composer une réponse : créer une méthode de service qui le fait.
- Exposer `id`, ou une route qui accepte l'`id` interne ; `@CrossOrigin` (le CORS se règle dans `config/`).

## Tests

`controller/<Ressource>ControllerTest` : `@WebMvcTest` + `spring-security-test` (`jwt()`), service **mocké** (`@MockitoBean`),
pour vérifier le **contrat HTTP** uniquement : statuts 200/201/204/400/401/403/404/409, corps d'erreur `ProblemDetail` avec
`code`, validation, `Location`, accès par rôle. Toujours inclure le cas sans jeton (401) et le mauvais rôle (403). Pas de test
« le service a été appelé ». Voir `src/test/.../CLAUDE.md`.

## Checklist « nouvelle ressource »

1. Service prêt. 2. Contrôleur avec les 5 routes utiles seulement. 3. Statuts corrects. 4. Règles d'accès. 5. Test de contrat HTTP.
6. Mettre à jour `docs/memoire/ETAT.md` (route ajoutée).
