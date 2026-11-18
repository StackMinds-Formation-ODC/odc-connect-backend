---
description: Architecture en couches Spring Boot — dossiers, dépendances, checklist fonctionnalité
alwaysApply: true
---

# Architecture en couches (Spring Boot)

Adapté de l'ancienne instruction « architecture hexagonale NestJS » : même esprit (métier au centre, HTTP et DB en
périphérie, le domaine parle en codes), mais découpage en **dossiers techniques** plats propre à Spring.

## Règle d'or

Les dépendances descendent, jamais l'inverse :

```
controller  ->  service  ->  repository  ->  model
```

- `model/` ne connaît personne de l'application (JPA/Jakarta seulement).
- `repository/` ne connaît que `model/`.
- `service/` connaît `repository`, `model`, `dto`, `mapper`, `exception`. **Aucun type HTTP** (`ResponseEntity`,
  `HttpStatus`, `HttpServletRequest`…).
- `controller/` ne connaît que `service`, `dto`, `security` (utilisateur courant). Jamais `repository`, jamais `model`.
- `dto/`, `mapper/`, `exception/`, `security/`, `config/` sont transverses : ils ne dépendent **pas** de `service` ni de
  `controller`.

## Dossiers sous `src/main/java/com/stackminds/odc_connect/`

| Dossier | Rôle | Contient |
|---------|------|----------|
| `config/` | Câblage Spring | `@Configuration` (`SecurityConfig`, CORS, OpenAPI), `@ConfigurationProperties` |
| `controller/` | Entrée HTTP | `@RestController` minces |
| `dto/` | Contrat d'API | `records` `*Request` / `*Response`, validation syntaxique |
| `exception/` | Erreurs | `BusinessException`, `ErrorCode`, `GlobalExceptionHandler` |
| `mapper/` | Conversions | `*Mapper` : entité <-> DTO |
| `model/` | Cœur de données | entités JPA, `model/enums/` |
| `repository/` | Persistance | interfaces `JpaRepository` |
| `security/` | Briques de sécurité | `JwtAuthConverter`, `CurrentUser`, constantes de rôles |
| `service/` | Métier | `@Service` transactionnels, `service/policy/` pour les règles inter-entités |

Démarrer **à plat**. Quand une couche dépasse ~10 fichiers, la découper en sous-dossiers par fonctionnalité, avec le **même
nom dans toutes les couches** (`controller/member/`, `service/member/`, `dto/member/`…). Ne pas mélanger plat et
sous-dossiers pour une même fonctionnalité.

## Règles par dossier

- **model/** : `@Entity`, `@Table(name = "<snake_case_pluriel>")`. Lombok limité à `@Getter`, `@NoArgsConstructor(access =
  PROTECTED)` et un constructeur/builder ; **pas de `@Data`**, pas de `@ToString`/`@EqualsAndHashCode` sur des relations
  lazy. `equals/hashCode` sur l'identité (`publicId`). Enums persistés en `EnumType.STRING`. Pas de dépendance à un DTO.
  Une entité protège ses invariants simples ; elle ne contient pas d'orchestration.
- **repository/** : interface `extends JpaRepository<X, Long>`. Requêtes dérivées ou `@Query` JPQL. Retourne `Optional`
  pour une recherche unitaire, `Page` avec `Pageable` pour les listes. Aucune logique métier, aucune transaction ici.
  Collections lazy utilisées par un DTO : `@EntityGraph` ou `JOIN FETCH` (éviter le N+1).
- **dto/** : un `record` par usage. Requêtes annotées Jakarta Validation (`@NotBlank`, `@Size`, `@Email`…) = validation
  **syntaxique**. Aucune entité dedans. Aucun `id` interne : seulement `publicId`.
- **mapper/** : `@Component` écrit à la main (`toEntity`, `toResponse`, `applyUpdate`). Aucun accès repository, aucune règle
  métier. (MapStruct n'est pas installé ; voir `PIEGES.md` avant de l'ajouter : il doit s'enchaîner avec Lombok.)
- **service/** : une classe par fonctionnalité (`MemberService`). `@Transactional` sur les méthodes qui écrivent,
  `@Transactional(readOnly = true)` sur celles qui lisent. Déroulé d'une opération :
  **valider (règles métier) -> charger -> policies -> écrire -> mapper vers `*Response` (dans la transaction)**.
  Prend des `*Request`/commandes, renvoie des `*Response`, **jamais une entité**. Pas d'interface `XxxService` tant qu'il n'y a
  pas deux implémentations (YAGNI). Les règles qui comparent plusieurs entités vont dans `service/policy/*Policy`
  (reçoivent des objets déjà chargés, aucune I/O).
- **controller/** : `@RestController` + `@RequestMapping("/api/<ressources>")`, `@Valid @RequestBody`, un appel au service,
  zéro logique. Statuts : `201` création (+ `Location`), `200` lecture/mise à jour, `204` suppression. Les erreurs ne se
  gèrent **pas** ici (pas de `try/catch`).
- **exception/** : le service lève `new BusinessException(ErrorCode.MEMBER_NOT_FOUND, "…message français…")`. Le **seul**
  endroit qui associe un `ErrorCode` à un statut HTTP est `GlobalExceptionHandler` (`@RestControllerAdvice`), qui renvoie un
  `ProblemDetail` (RFC 9457) avec une propriété `code`. Le handler traduit aussi `MethodArgumentNotValidException` (400),
  `AccessDeniedException` (403) et ne divulgue jamais de trace.
- **security/** : `JwtAuthConverter` transforme les claims du JWT (rôles de l'IdP) en `GrantedAuthority` ; `CurrentUser`
  expose l'utilisateur courant aux services/contrôleurs. La **chaîne de filtres** reste dans `config/SecurityConfig`.
  Autorisation grossière : `requestMatchers` dans `SecurityConfig` ; fine : `@PreAuthorize` (nécessite
  `@EnableMethodSecurity`, pas encore activé).
- **config/** : uniquement du câblage. Valeurs variables = `application*.yaml` + variables d'environnement, lues via
  `@ConfigurationProperties` (record) plutôt que `@Value` dispersés.

## Interdits

- `controller` -> `repository`, ou entité renvoyée/acceptée par un contrôleur.
- Type HTTP (`ResponseEntity`, `HttpStatus`, `HttpServletRequest`) dans `service`, `model`, `repository`, `mapper`.
- `ResponseStatusException` / `HttpStatus` dans un service : lever `BusinessException`.
- Mapping vers DTO **en dehors** d'une transaction (`open-in-view` est désactivé : `LazyInitializationException`).
- Injection par champ (`@Autowired` sur attribut). Constructeur uniquement.
- Exposer l'`id` interne, un hash, un secret ou un champ technique dans un `*Response`.
- Secrets en clair dans `config/` ou `application*.yaml` ; modifier une migration Flyway déjà appliquée.
- `ddl-auto` autre que `validate` (le schéma appartient à Flyway).

## Checklist nouvelle fonctionnalité

1. migration Flyway `V<n>__create_<table>.sql` → 2. `model/` (+ enums) → 3. `repository/` → 4. `dto/` (Request/Response)
→ 5. `exception/ErrorCode` (nouveaux codes + statut dans le handler) → 6. `mapper/` → 7. `service/` (+ `policy/` si besoin)
→ 8. `controller/` → 9. règles d'accès (`SecurityConfig` / `@PreAuthorize`) → 10. tests (voir `strategie-tests.md`)
→ 11. mettre à jour `docs/memoire/ETAT.md` (et une décision si un choix structurant a été fait).
