# CLAUDE.md — `src/test/` (tests, un gabarit par couche)

> Instruction de projet : **lecture seule pour les agents IA**. Complète `docs/instruction/strategie-tests.md` (tableau
> « Autorisé / Interdit » qui fait foi). Ce fichier donne la **forme** des tests de chaque couche.

## Arborescence (miroir de `src/main`)

```
src/test/java/<package racine>/
  TestcontainersConfiguration.java   # existant : conteneur postgres:17 en @ServiceConnection
  TestOdcConnectApplication.java     # existant : lancer l'app avec ce conteneur
  OdcConnectApplicationTests.java    # existant : UN seul contextLoads (valide Flyway + ddl-auto=validate)
  controller/  service/  service/policy/  repository/  mapper/  dto/  security/  exception/
  support/                           # fabriques de données de test (MemberFixtures…), pas de test ici
```

Tout test qui exige Docker : `@Tag("integration")`. Exécution : `./mvnw test` (tout) · `./mvnw test -DexcludedGroups=integration` (sans Docker).
Noms : `<ClasseTestée>Test` ; méthodes `shouldXxx[When…]` (anglais), `@DisplayName` français facultatif. AssertJ + Arrange/Act/Assert.

> **Spring Boot 4 a modularisé les starters de test** : les annotations (`@WebMvcTest`, `@DataJpaTest`,
> `@AutoConfigureTestDatabase`…) ont changé de package par rapport à Boot 3. Les imports sont omis ci-dessous : les vérifier dans la
> doc 4.1.2 (liens dans `HELP.md`) ou par l'auto-complétion de l'IDE ; ne pas copier un tutoriel Boot 3.

## Service — unitaire (Mockito)

```java
@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

	@Mock MemberRepository repository;
	@Mock MemberMapper mapper;
	@Spy  MemberStatusTransitionPolicy transitions = new MemberStatusTransitionPolicy();
	@InjectMocks MemberService service;

	@Test
	void shouldRejectDuplicateEmail() {
		when(repository.existsByEmailIgnoreCase("a@b.c")).thenReturn(true);

		assertThatThrownBy(() -> service.create(new CreateMemberRequest("a@b.c", "Awa")))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getCode()).isEqualTo(ErrorCode.MEMBER_EMAIL_ALREADY_USED));
		verify(repository, never()).save(any());
	}
}
```
Seulement `create`/`update` et leurs règles. Pas de test de `get`/`list`/`delete` triviaux, pas de `verify` qui ne fait que relire un mock.

## Contrôleur — contrat HTTP (`@WebMvcTest`)

```java
@WebMvcTest(MemberController.class)
@Import({SecurityConfig.class, JwtAuthConverter.class, GlobalExceptionHandler.class})
class MemberControllerTest {

	@Autowired MockMvc mvc;
	@MockitoBean MemberService service;
	@MockitoBean JwtDecoder jwtDecoder;      // requis par le resource server dans la tranche web

	@Test
	void shouldReturn401WithoutToken() throws Exception {
		mvc.perform(get("/api/members/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
	}

	@Test
	void shouldReturn404WithErrorCodeWhenMemberMissing() throws Exception {
		UUID id = UUID.randomUUID();
		when(service.getByPublicId(id))
			.thenThrow(new BusinessException(ErrorCode.MEMBER_NOT_FOUND, "Membre introuvable"));

		mvc.perform(get("/api/members/{id}", id).with(jwt()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
	}

	@Test
	void shouldReturn400WhenEmailInvalid() throws Exception {
		mvc.perform(post("/api/members").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"pas-un-email\",\"displayName\":\"Awa\"}"))
			.andExpect(status().isBadRequest());
	}
}
```
Toujours : cas **sans jeton** (401) et **mauvais rôle** (403) + un cas nominal + les erreurs de validation/métier de la ressource.
Le service est mocké : on teste le **contrat HTTP**, pas « le service a été appelé ».

## Repository — intégration (vraie base)

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)   // ne pas remplacer par H2
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class MemberRepositoryTest {

	@Autowired MemberRepository repository;

	@Test
	void shouldFindByPublicId() {
		Member saved = repository.saveAndFlush(new Member("a@b.c", "Awa"));
		assertThat(repository.findByPublicId(saved.getPublicId())).isPresent();
	}

	@Test
	void shouldRejectDuplicateEmailAtDatabaseLevel() {
		repository.saveAndFlush(new Member("a@b.c", "Awa"));
		assertThatThrownBy(() -> repository.saveAndFlush(new Member("a@b.c", "Bob")))
			.isInstanceOf(DataIntegrityViolationException.class);       // le filet UNIQUE de la migration
	}
}
```
Flyway s'exécute sur le conteneur : ce test prouve aussi que **entité et migration concordent**. Jamais H2.

## DTO — validation (sans Spring)

```java
class CreateMemberRequestTest {
	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	@Test
	void shouldRejectBlankDisplayName() {
		var violations = VALIDATOR.validate(new CreateMemberRequest("a@b.c", " "));
		assertThat(violations).extracting(v -> v.getPropertyPath().toString()).containsExactly("displayName");
	}
}
```

## Policy, mapper, sécurité, exception

- **Policy** : cas limites en appelant la méthode (aucun mock : elle est pure).
- **Mapper** : seulement s'il porte de la logique (défauts, null, listes).
- **Sécurité** : `JwtAuthConverter` avec `Jwt.withTokenValue("t").header("alg","none").claim("realm_access", Map.of("roles", List.of("ADMIN"))).build()`.
- **Exception** : un cas par `ErrorCode` → statut attendu.

## Règles communes

- Données construites dans le test ou dans `support/*Fixtures` ; jamais dépendantes des seeds de dev.
- Un test = un comportement ; indépendant de l'ordre ; pas de `Thread.sleep` ni d'horloge réelle (`Clock.fixed`).
- `@SpringBootTest` complet : seulement `OdcConnectApplicationTests` et de rares scénarios de bout en bout.
- Pas de Docker : lancer `-DexcludedGroups=integration` et **le dire** (ne pas affirmer que les intégrations passent).
