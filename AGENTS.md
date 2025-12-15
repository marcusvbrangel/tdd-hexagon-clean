Repository Guidelines
====================

Project Structure & Module Organization
--------------------------------------
- `src/main/java/com/mvbr/estudo/tdd` holds Spring Boot 4 code organized by layers: `domain` (entities like `Order`, value objects, exceptions), `application` (ports/use cases such as `CreateOrderUseCase`, orchestration helpers), `infrastructure` (adapters: web controller, Kafka consumer/publisher, JPA persistence), and `config` for wiring.
- `src/main/resources/application.yaml` contains service, persistence, and messaging defaults. Keep environment-specific values external when possible.
- `src/test/java` stores JUnit 5 tests; mirror package names to keep scannable coverage.

Build, Test, and Development Commands
-------------------------------------
- `./mvnw clean verify` runs the full Maven lifecycle with tests; prefer before merging.
- `./mvnw test` runs the JUnit suite quickly during development.
- `./mvnw spring-boot:run` starts the API locally using `application.yaml`; override properties via `--spring.profiles.active` or `-D` flags.

Coding Style & Naming Conventions
---------------------------------
- Target Java 21, Spring Boot idioms, and layered boundaries. Classes and enums use PascalCase; methods/fields use camelCase; packages remain lowercase.
- Keep domain types immutable where feasible; validate invariants inside constructors/factories.
- Avoid wildcard imports; prefer constructor injection for components.
- If formatting is needed, use `./mvnw spotless:apply` (add Spotless if not already configured) or follow standard IntelliJ/VS Code Java defaults: 4-space indent, 120-char line target.

Testing Guidelines
------------------
- Use JUnit 5 (`@SpringBootTest` for context, lighter tests for domain logic). Name files `*Test`/`*Tests` matching the class under test.
- Favor domain-level unit tests without Spring context for speed; isolate adapters with mocks or in-memory fakes.
- For integration flows (web + persistence + messaging), annotate with profile-specific configurations and clean up created data between runs.

Commit & Pull Request Guidelines
--------------------------------
- Commit messages: short imperative subject (e.g., `Add order validation`) with optional body explaining rationale/impacts.
- Pull requests should describe the change, risk, and test evidence. Link relevant issues; include curl or HTTPie examples for new endpoints and note config changes (Kafka topics, DB URLs) in the description.

Security & Configuration Notes
------------------------------
- Never hardcode secrets in `application.yaml`; use environment variables or Spring config server.
- Ensure new adapters or topics are registered in `KafkaConfig` and that persistence entities maintain backward compatibility when altering schemas.
