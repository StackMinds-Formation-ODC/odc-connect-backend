---
description: Convention de nommage Java / Spring (packages, classes, méthodes, routes, tables, migrations)
globs: "**/*.{java,sql,yaml}"
alwaysApply: true
---

# Nomenclature Java / Spring

## Langue

- Identifiants (classes, méthodes, variables, colonnes, routes, `ErrorCode`) : **anglais**.
- Commentaires, Javadoc, messages d'erreur, documentation, messages de commit : **français**.

## Packages et dossiers

Base : `com.stackminds.odc_connect` (le `_` est imposé, ne pas « corriger »). Sous-packages : minuscules, un mot, sans
`_` ni `-` : `config`, `controller`, `dto`, `exception`, `mapper`, `model`, `repository`, `security`, `service`.
Sous-dossiers de fonctionnalité (quand une couche dépasse ~10 fichiers) : même nom dans toutes les couches.

| Emplacement | Exemple |
|-------------|---------|
| Entités / enums | `model/Member.java`, `model/enums/MemberStatus.java` |
| Repositories | `repository/MemberRepository.java` |
| Services / policies | `service/MemberService.java`, `service/policy/MemberStatusTransitionPolicy.java` |
| Contrôleurs | `controller/MemberController.java` |
| DTO | `dto/CreateMemberRequest.java`, `dto/MemberResponse.java` |
| Mappers | `mapper/MemberMapper.java` |
| Erreurs | `exception/BusinessException.java`, `ErrorCode.java`, `GlobalExceptionHandler.java` |
| Sécurité | `security/JwtAuthConverter.java`, `security/CurrentUser.java`, `security/Roles.java` |
| Config | `config/SecurityConfig.java`, `config/CorsConfig.java`, `config/MailProperties.java` |

Un fichier = un type public, nommé comme lui.

## Classes — PascalCase + suffixe par rôle

| Rôle | Nom | Remarque |
|------|-----|----------|
| Entité | `Member` | pas de suffixe `Entity` |
| Enum | `MemberStatus` | valeurs `UPPER_SNAKE_CASE` |
| Repository | `MemberRepository` | |
| Service | `MemberService` | |
| Policy | `MemberStatusTransitionPolicy` | règle pure, sans I/O (l'unicité en base se vérifie dans le service) |
| Contrôleur | `MemberController` | |
| DTO entrée | `CreateMemberRequest`, `UpdateMemberRequest` | `record` |
| DTO sortie | `MemberResponse` | `record` ; liste : `Page<MemberResponse>` ou `PageResponse<…>` |
| Mapper | `MemberMapper` | |
| Exception métier | `BusinessException` (unique, porte un `ErrorCode`) | pas une classe par erreur |
| Code d'erreur | `ErrorCode` | enum |
| Handler | `GlobalExceptionHandler` | |
| Config | `XxxConfig` ; propriétés : `XxxProperties` | |

## Méthodes, variables, constantes

- Méthodes / variables / paramètres : `camelCase`. Constantes `static final` : `UPPER_SNAKE_CASE`.
- Service et contrôleur (mêmes verbes) : `create`, `getByPublicId`, `list`, `update`, `delete`.
- Repository (requêtes dérivées) : `findByPublicId`, `existsByEmail`, `findAllByStatus`.
- Mapper : `toEntity`, `toResponse`, `applyUpdate`.
- Booléens : `isActive`, `hasRole`. Pas de booléen en paramètre : deux méthodes ou un enum.
- Codes d'erreur : `ENTITE_RAISON` (`MEMBER_NOT_FOUND`, `MEMBER_EMAIL_ALREADY_USED`).

## HTTP

- Préfixe `/api`, ressources en **kebab-case pluriel** : `/api/members`, `/api/member-roles`.
- Identifiant dans l'URL : `/{publicId}` (UUID). Jamais l'`id` interne.
- Champs JSON : `camelCase`. Pagination : `page`, `size`, `sort` (convention `Pageable`).
- Erreurs : `ProblemDetail` + `code` (voir `architecture-couches-spring.md`).

## Base de données

- Tables : `snake_case` **pluriel** (`members`, `member_roles`). Colonnes : `snake_case`.
- Clé primaire interne `id BIGINT GENERATED ALWAYS AS IDENTITY` ; identifiant externe `public_id UUID NOT NULL UNIQUE`.
- Horodatages : `created_at`, `updated_at` en `TIMESTAMPTZ`.
- Clé étrangère : `<entité_singulier>_id` ; contraintes nommées : `fk_<table>_<ref>`, `uk_<table>_<colonne>`,
  `idx_<table>_<colonne>`.
- Entité <-> table explicite : `@Table(name = "members")`, `@Column(name = "public_id")`.
- Migrations Flyway : `V<n>__<description_snake_case>.sql` (deux `_`), numéro strictement croissant, ex.
  `V1__create_members.sql`. Seeds de démo : `R__<NNN>_seed_<nom>.sql` (voir `seeder-gestion.md`).

## Configuration et environnement

- Variables d'environnement : `UPPER_SNAKE_CASE` (`DB_PASSWORD`, `JWT_JWK_SET_URI`).
- Profils Spring : `dev`, `prod` (un fichier `application-<profil>.yaml` chacun).
- Propriétés applicatives propres : préfixe `odc.` (`odc.mail.from`), liées par `@ConfigurationProperties`.

## Tests

- Classe : `<ClasseTestée>Test` (`MemberServiceTest`). Méthode : `shouldXxx[When…]` en anglais
  (`shouldRejectDuplicateEmail`) ; `@DisplayName` en français facultatif. Voir `strategie-tests.md`.
