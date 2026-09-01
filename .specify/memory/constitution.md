<!--
Sync Impact Report
Version change: N/A -> 1.0.0
Modified principles:
- PRINCIPLE_1_NAME placeholder -> I. Java and Spring Boot First
- PRINCIPLE_2_NAME placeholder -> II. Layered Architecture
- PRINCIPLE_3_NAME placeholder -> III. DTO-Based API Contracts
- PRINCIPLE_4_NAME placeholder -> IV. Persistence and Validation Discipline
- PRINCIPLE_5_NAME placeholder -> V. Centralized Error Handling and REST Semantics
Added sections:
- Backend Constraints
- Development Workflow
Removed sections:
- None
Follow-up TODOs:
- None
-->
# DSCommerce Backend Constitution

## Core Principles

### I. Java and Spring Boot First
The project MUST use Java and Spring Boot as the primary backend technologies. Core application
behavior, configuration, dependency injection, validation integration, persistence integration, and
REST API delivery MUST be implemented through Spring Boot conventions unless a requirement
explicitly justifies a narrower alternative.

Rationale: This project exists for learning backend e-commerce development with the Java and Spring
ecosystem, so consistency in the core stack is required for focused practice.

### II. Layered Architecture
Production code MUST be organized around Controller, Service, and Repository layers. Controllers
MUST handle HTTP concerns and request/response mapping. Services MUST contain business rules,
transaction boundaries, and orchestration. Repositories MUST handle persistence access only.

Rationale: Clear layer boundaries make responsibilities visible, reduce coupling, and keep the
project maintainable as the e-commerce domain grows.

### III. DTO-Based API Contracts
API input and output MUST use DTOs instead of exposing JPA entities directly. Request DTOs MUST
represent client-provided data, response DTOs MUST represent API output, and mapping between DTOs
and domain entities MUST occur outside repositories.

Rationale: DTOs protect persistence models from leaking into public contracts and allow the API to
evolve without forcing database-oriented structures onto clients.

### IV. Persistence and Validation Discipline
Persistence MUST use JPA/Hibernate through repository abstractions. Entity relationships,
identifiers, and transactional behavior MUST be modeled deliberately from the requirements before
implementation. Incoming API data MUST be validated with Bean Validation annotations and enforced
at the controller boundary before service logic executes.

Rationale: JPA/Hibernate is the learning target for persistence, and Bean Validation provides a
standard, explicit way to reject invalid input before business rules run.

### V. Centralized Error Handling and REST Semantics
Exceptions from application flows MUST be handled through centralized exception handling, such as a
Spring `@ControllerAdvice`, rather than duplicated controller-level try/catch logic. REST APIs MUST
use resource-oriented routes, appropriate HTTP methods, meaningful status codes, and consistent
error response bodies.

Rationale: Centralized handling keeps failure behavior predictable, and correct HTTP semantics make
the API easier to learn, test, and consume.

## Backend Constraints

Business rules MUST be derived from documented requirements before implementation. Requirements
MUST identify the relevant e-commerce behavior, expected state transitions, and validation rules
before code is added to services.

Service methods MUST be the source of business decisions. Controllers MUST NOT implement pricing,
order, payment, inventory, authorization, or lifecycle rules. Repositories MUST NOT contain business
branching beyond persistence queries required by services.

Code MUST prioritize clarity, separation of responsibilities, and maintainability. New abstractions
MUST have a concrete purpose in the current requirements. Shared helpers MUST only be introduced
when they reduce meaningful duplication or clarify a recurring pattern.

## Development Workflow

Feature work MUST start from requirements and design artifacts before implementation. The
implementation plan MUST identify API contracts, DTOs, entities, services, repositories, validation
rules, and exception paths affected by the change.

Reviews MUST check that business rules live in services, DTOs are used for API boundaries,
validation is enforced with Bean Validation, persistence uses JPA/Hibernate appropriately, and REST
responses use suitable HTTP status codes.

Tests SHOULD cover service-level business rules and API behavior for success, validation failure,
not-found cases, and domain errors. Gaps in tests MUST be documented when a change is accepted for
learning purposes without full coverage.

## Governance

This constitution governs technical decisions for the project. Specifications, plans, tasks, code
reviews, and implementation work MUST comply with these principles. When another project document
conflicts with this constitution, this constitution takes precedence.

Amendments MUST be made by updating `.specify/memory/constitution.md` with a Sync Impact Report
describing the version change, modified principles, added sections, removed sections, and any
deferred TODOs. Amendments MUST preserve the intent of existing learning goals unless a major
version explicitly changes them.

Versioning follows semantic versioning. MAJOR versions indicate incompatible governance or principle
redefinitions. MINOR versions indicate added principles, added sections, or materially expanded
guidance. PATCH versions indicate clarifications, wording fixes, or non-semantic refinements.

Compliance MUST be reviewed before implementation tasks are considered complete. Any intentional
exception MUST be documented with the requirement that justifies it and the expected follow-up.

**Version**: 1.0.0 | **Ratified**: 2026-08-18 | **Last Amended**: 2026-08-18

