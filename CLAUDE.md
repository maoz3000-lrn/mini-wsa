## 🏗️ Architecture & Component Design
- **Layered Architecture:** Maintain explicit strict separation across `controller` (Web/REST), `service` (Business Logic), `repository` (Persistence/Data), and `model`/`entity` (JPA Domain).
- **Thin Controllers:** Controllers must exclusively map HTTP requests, invoke services, and return responses. No business logic or direct persistence interaction.
- **Data Transfer Objects:** Never expose raw JPA `@Entity` classes in controller methods. Use immutable Java `record` types for all request/response DTOs.
- **Persistence Rules:** Use Spring Data JPA repositories strictly for database access. Always apply `@Transactional` boundaries at the Service layer, not the Controller or Repository level.

## Code Style & Guidelines
* **Code Formatting:** Follow standard Java conventions (4 spaces for indentation, camelCase for variables/methods, PascalCase for classes).
* **Logging Conventions:**
    * **Framework:** Use SLF4J's `@Slf4j` annotation from Lombok (or `LoggerFactory.getLogger(Class.class)` manually).
    * **No Standard Output:** Never use `System.out.println()` or `e.printStackTrace()`. Use logger methods instead.
    * **Parameterized Messages:** Use SLF4J curly-brace placeholders (`{}`) instead of string concatenation. (e.g., `log.info("User {} logged in", userId)` instead of `"User " + userId + " logged in"`).
    * **Log Levels:**
        * `ERROR`: Critical failures that need immediate attention (include the full exception object as the final argument so the stack trace logs).
        * `WARN`: Unexpected behavior that doesn't stop the application flow (e.g., fallback actions, validation failures).
        * `INFO`: Major lifecycle milestones, API request initializations, or significant state changes.
        * `DEBUG`/`TRACE`: High-volume, granular information for troubleshooting data flows and logic execution.
* **String Management (No Hardcoding):**
    * **Configuration & Secrets:** Never hardcode URLs, ports, keys, or credentials. Use `@Value("${property.name}")` or `@ConfigurationProperties` mapped to environment configurations.
    * **Messages & Labels:** Externalize user-facing text and validation messages using resource bundles (`messages.properties`) or public static constants.
    * **Internal Constants:** Reuse repeated arbitrary values by extracting them into descriptive `public static final String` or `enum` types.
* **Modern Java & Spring Features:**
    * Leverage Java 25 optimizations (Virtual Threads, Records, Pattern Matching, Switch Expressions).
    * Enable virtual threads in configuration: `spring.threads.virtual.enabled=true`.
    * Use Spring Boot 4 modular patterns and constructor injection (avoid field-level `@Autowired`).
    * Use `@MockitoBean` instead of `@MockBean` for testing.
* **Naming Conventions:**
    * Packages: lowercase, reverse domain (e.g., `com.mycompany.project`)
    * Interfaces: Name clearly by capability (e.g., `UserService`)
    * Implementations: Appended with `Impl` if singular, or descriptive (e.g., `DatabaseUserService`)
* **Error Handling:** Use custom exceptions where appropriate; avoid catching generic `Exception` unless logging at the top level. Do not swallow exceptions.

## 🚦 Operational Guardrails
- **Verification Rule:** Always run `mvn test` or `mvn clean compile` before declaring a task or ticket complete. If a test fails, perform root-cause analysis instead of introducing temporary hacks.
- **Commit Standards:** Write clean, professional Git commit messages explaining *what* changed and *why*. Never include Claude Code attribution tags inside commit summaries.
- **Extended Documentation:** For deep architectural specifications, refer to specialized guides in `docs/claude/` (e.g., `docs/claude/api-style-guide.md` or `docs/claude/testing-guide.md`).