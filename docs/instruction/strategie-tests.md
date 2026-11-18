---
description: Stratégie de tests — types autorisés vs interdits, emplacement, exécution (JUnit 5 / Spring Boot / Testcontainers)
globs: "**/src/test/**/*.java"
alwaysApply: true
---

# Stratégie de tests

## Emplacement

Standard Maven : `src/test/java/com/stackminds/odc_connect/`, avec **le même découpage par couche que `src/main`**
(`service/`, `controller/`, `repository/`, `mapper/`, `dto/`, `security/`, `exception/`). Ne jamais mettre de test sous
`src/main` : il serait empaqueté dans le jar et les dépendances de test (scope `test`) n'y sont pas visibles.

Déjà présents à la racine du package de test : `TestcontainersConfiguration` (conteneur `postgres:17` avec
`@ServiceConnection`), `TestOdcConnectApplication` (lancement de l'app avec ce conteneur) et `OdcConnectApplicationTests`
(`contextLoads`).

## Autorisé (créer / garder)

| Type | Emplacement | Contenu attendu |
|------|-------------|-----------------|
| Unitaire service **create / update** | `service/*ServiceTest` | Mockito (`@ExtendWith(MockitoExtension.class)`), règles métier, codes `ErrorCode`, orchestration des policies |
| Unitaire policy | `service/policy/*PolicyTest` | cas limites inter-entités |
| Unitaire DTO | `dto/*Test` | `Validator` Jakarta : champs obligatoires, bornes, formats, via `Validation.buildDefaultValidatorFactory()` |
| Unitaire mapper | `mapper/*MapperTest` | seulement s'il y a de la logique (valeurs par défaut, null, listes) |
| Unitaire sécurité | `security/*Test` | `JwtAuthConverter` : claims -> autorités |
| Intégration repository (**prio 1**) | `repository/*RepositoryTest` | `@DataJpaTest` + `@Import(TestcontainersConfiguration.class)` + base **PostgreSQL réelle** ; requêtes, contraintes uniques/FK, migrations Flyway |
| Intégration rollback (prio 1) | `service/*RollbackTest` (`@Tag("integration")`) | transaction annulée en cas d'erreur (vraie base) |
| Contrat HTTP (**prio 2**) | `controller/*ControllerTest` | `@WebMvcTest` + `spring-security-test` (`jwt()`) : statuts 200/201/204/400/401/403/404/409, **corps d'erreur** `ProblemDetail`+`code`, validation, accès par rôle |
| Démarrage complet | `OdcConnectApplicationTests` | **un seul** `contextLoads` (valide Flyway + `ddl-auto: validate`) ; + scénarios `@SpringBootTest` métier rares |

Tout test qui exige Docker (Testcontainers) porte `@Tag("integration")`. Convention : tous les tests s'appellent
`*Test`/`*Tests` (surefire) ; les « intégration » sont distingués par le tag, pas par un autre suffixe.

## Interdit (ne jamais créer ; supprimer s'ils existent)

- **H2 ou toute base en mémoire** à la place de PostgreSQL (SQL Flyway, types et contraintes diffèrent).
- Service **get / list / delete** testé unitairement avec des mocks : redondant avec les tests d'intégration repository.
- Test de contrôleur qui se contente de vérifier « le service a été appelé » (`verify(service).create(...)`) :
  le contrôleur ne se teste que sur son **contrat HTTP** (statut, JSON, validation, sécurité).
- Tests de getters/setters/`equals`/Lombok, de `record`, de constructeurs.
- Tout test qui ne fait que relire un mock (`verify(repo).save(x)`) sans règle métier.
- Un `@SpringBootTest` complet par fonctionnalité (lent) : préférer slice (`@WebMvcTest`, `@DataJpaTest`) ou unitaire.
- `Thread.sleep`, dépendance à l'ordre d'exécution, à l'heure réelle ou à des données laissées par un autre test.

```java
// ❌ BAD — service list unitaire, relit un mock
@Test
void shouldReturnPage() {
	when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(member)));
	assertThat(service.list(PageRequest.of(0, 10)).getContent()).hasSize(1);
}

// ✅ GOOD — règle métier + code d'erreur
@Test
void shouldRejectDuplicateEmail() {
	when(repository.existsByEmail("a@b.c")).thenReturn(true);

	assertThatThrownBy(() -> service.create(new CreateMemberRequest("a@b.c", "Awa")))
		.isInstanceOf(BusinessException.class)
		.extracting("code").isEqualTo(ErrorCode.MEMBER_EMAIL_ALREADY_USED);
}
```

## Conventions

- Structure Arrange / Act / Assert ; un test = un comportement ; AssertJ pour les assertions.
- Classe `<ClasseTestée>Test` ; méthode `shouldXxx[When…]` ; `@DisplayName` français facultatif.
- Données de test construites dans le test (ou une fabrique `TestData`), jamais dépendantes du seed de dev.
- Tests de contrôleur : toujours tester le cas **sans jeton** (401) et **avec un mauvais rôle** (403) en plus du cas nominal.
- Imports et packages des annotations de test : **Spring Boot 4 a modularisé** (`spring-boot-starter-*-test`) ; vérifier la
  doc 4.1.2 plutôt que copier un tutoriel Boot 3.

## Exécution

```bash
./mvnw test                                # tout (Docker requis pour @Tag("integration"))
./mvnw test -DexcludedGroups=integration   # unitaires seulement
./mvnw test -Dtest=MemberServiceTest       # une classe
```

## Comportement agent

- Nouveau test hors tableau « Autorisé » → **ne pas écrire** ; proposer un test d'intégration ou de contrat HTTP si besoin.
- Test H2 / mock tautologique trouvé → **supprimer** (le remplacer par une intégration repository si nécessaire).
- Ne jamais supprimer ni refuser un test d'intégration réel (`@Tag("integration")`).
- Pas de Docker dans l'environnement : exécuter `-DexcludedGroups=integration` et le dire explicitement dans le compte rendu
  (ne pas affirmer que les intégrations passent).
