---
description: Règles de qualité de code (Java / Spring Boot)
globs: "**/*.java"
alwaysApply: true
---

# Qualité de code

Ces règles servent la lisibilité, la maintenabilité et la testabilité. Ne pas les appliquer aveuglément : sur-appliquer
SOLID sur un petit projet produit de la sur-ingénierie. Critère final : « un autre développeur comprendra-t-il et modifiera-t-il
ce code facilement ? »

## 1. Taille du code

| Élément | Limite |
|---------|--------|
| Fichier `.java` sous `src/` | 300 lignes max |
| Classe | 200 à 300 lignes, une seule responsabilité |
| Méthode | 30 à 50 lignes max |
| Ligne | 120 caractères max |
| Paramètres | 3 idéalement, 4 max (sinon un `record`) |
| Imbrication (`if`/`for`) | 2 à 3 niveaux max |
| Complexité cyclomatique | ≤ 10 par méthode |

- Une méthode fait une seule chose et tient dans un écran. Clauses de garde plutôt que `if` imbriqués.
- Si une méthode a des sections « étape 1, étape 2 », la découper. Un bon nom remplace un commentaire.
- Indentation par **tabulations** (comme le code existant) ; fins de ligne LF (`.gitattributes`).
- Exceptions tolérées : constructeurs à beaucoup de dépendances injectées (le contrôleur d'une ressource CRUD en a
  naturellement plusieurs) — au-delà de 5, se demander si la classe a trop de responsabilités.

## 2. SOLID (rappel opérationnel)

- **S** : une classe, une raison de changer (un service qui calcule, génère un PDF et envoie un mail = 3 classes).
- **O** : étendre par une nouvelle implémentation d'interface plutôt que par un `if/else` de plus.
- **L** : une sous-classe doit pouvoir remplacer sa mère sans casser le comportement.
- **I** : plusieurs petites interfaces plutôt qu'une grosse.
- **D** : dépendre d'abstractions, injecter par **constructeur** (pas de `new` de collaborateurs, pas d'`@Autowired` sur champ).

Complémentaires : DRY, KISS, YAGNI, loi de Déméter, composition plutôt qu'héritage, forte cohésion / faible couplage,
Tell-Don't-Ask, séparation commande/requête, principe de moindre surprise.

## 3. Règles Java / Spring spécifiques

- **Immutabilité par défaut** : `final`, `record` pour DTO et valeurs, collections non modifiables en sortie.
- **Pas de `null` en retour** : `Optional` pour une recherche unitaire, collection vide pour une liste. Pas d'`Optional`
  en champ ni en paramètre.
- **Pas de nombres/chaînes magiques** : constantes ou enums nommés.
- **Lombok** : autorisé (`@Getter`, `@RequiredArgsConstructor`, `@Slf4j`, builders). Interdit : `@Data` sur une entité JPA,
  `@Setter` généralisé sur une entité, `@ToString` sur des relations lazy.
- **JPA** : `FetchType.LAZY` par défaut ; surveiller le N+1 (`@EntityGraph`, `JOIN FETCH`) ; pas d'entité renvoyée à l'API ;
  mapping vers DTO **dans** la transaction.
- **Transactions** : sur la couche `service` uniquement ; `readOnly = true` pour les lectures ; pas de `@Transactional`
  sur un contrôleur ni un repository ; pas d'appel d'une méthode `@Transactional` de la même classe (proxy contourné).
- **Journalisation** : SLF4J (`@Slf4j`), jamais `System.out` / `printStackTrace()`. Niveaux adaptés, **jamais** de jeton,
  mot de passe, JWT complet ni donnée personnelle dans les logs. Pas de `log.error` + `throw` pour la même erreur.
- **Pas de code mort ni de code commenté** : Git garde l'historique.

## 4. Gestion des erreurs

- Fail fast : valider les entrées dès l'entrée de la méthode.
- Les erreurs métier passent par `BusinessException(ErrorCode, message)` ; jamais `RuntimeException` nue, jamais
  `catch (Exception)` qui avale. Ne pas intercepter pour retourner `null`.
- Exceptions pour les cas exceptionnels, pas pour le flux normal.
- Messages utiles : contexte, valeurs concernées (hors données sensibles), action possible.
- Le client ne reçoit jamais de trace de pile ni de message technique (SQL, chemins).

## 4 bis. Données et état

- Pas d'état mutable partagé dans un bean singleton (`@Service`, `@Component`) : les beans sont sans état.
- Portée minimale des variables, fonctions pures quand c'est possible.

## 5. Sécurité

- Ne jamais faire confiance aux entrées : Bean Validation sur les `*Request`, requêtes **paramétrées** (JPQL / Spring Data,
  jamais de concaténation SQL).
- **Aucun secret dans le code ni dans Git** : variables d'environnement (`.env` ignoré, `.env.example` versionné).
- Moindre privilège : routes protégées par défaut (`anyRequest().authenticated()`), ouvrir explicitement le strict nécessaire.
- Ne jamais renvoyer d'entité : risque d'exposer `id`, champs techniques, relations.
- Mettre à jour les dépendances, surveiller les CVE ; référentiel OWASP Top 10.
- CORS : jamais `*` avec des identifiants ; liste d'origines explicite par profil.

## 6. Tests et workflow

- Principes FIRST ; structure Arrange / Act / Assert ; un test = un comportement. Détail : `strategie-tests.md`.
- Commits petits et atomiques (Conventional Commits en français : `feat(member): …`), petites PR relues.
- Documentation à jour : `ETAT.md` après chaque évolution notable, décision datée pour tout choix structurant.
- Définition de « terminé » : compile, tests verts, migration fournie si le modèle change, docs mémoire mises à jour.

## 7. Code smells à refactorer

God class, code dupliqué, longues listes de paramètres, `switch`/`if` répétés sur un même type, Feature Envy, Shotgun
Surgery, entité anémique **et** service obèse en même temps.

## 8. Outillage

Aucun outil de style/analyse n'est encore configuré dans `pom.xml` (voir `ETAT.md`). Pistes cohérentes avec ces règles :
Spotless ou Checkstyle (format, 120 colonnes), SonarQube/SonarCloud (complexité, smells), ArchUnit (appliquer la règle de
dépendance entre couches dans un test), Dependabot (dépendances). Ne pas les ajouter sans en parler à l'utilisateur.
