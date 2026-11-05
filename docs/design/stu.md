# Student (STU) Design

> Realizes: UC-STU-deactivate-student
> Depends on: FR-SEC-active-account, FR-SEC-authorization, CI-email-notifications, BR-student-lifecycle, BR-submission-owed, BR-deactivated-evaluatee, BR-deactivated-student-report-weeks, BR-section-scoped-access, BR-role-based-access, CO-ferpa
> See: [use cases](../requirements/use-cases.md#student), [architectural-design.md](architectural-design.md#crosscutting-concepts) (Crosscutting Concepts and the foundation component view), the [SRS Business Domain Model](../requirements/software-requirements-specification.md)
> Designed against `main` at `59f0267` (2026-11-04).

## Overview

Deactivating a student is a dedicated, confirmed action in the `student` slice of the REST API container. It replaces the Status switch that flipped `enabled` alongside name and email. Most of its reach is outside that slice: the account check runs on every authenticated request, and `evaluation` applies the rules about a deactivated evaluatee. Reminders already skip a deactivated student (`SubmissionStatusService.findOwedItems`), so `notification` does not change.

`UC-STU-reactivate-student` is not designed here. Until it is, the edit form's Status control remains as its stopgap and offers only reactivation.

## Components & classes

```mermaid
classDiagram
    direction LR
    class StudentController {
        <<reused, changed>>
        +deactivateStudent(studentId) Result
    }
    class StudentService {
        <<reused, changed>>
        +deactivateStudent(studentId) Student
        +updateStudent(studentId, update) Student
    }
    class StudentDeactivationNotifier {
        <<new, not transactional>>
        +notify(student) boolean
    }
    class EmailService {
        <<reused, changed>>
        +sendAccountDeactivatedEmail(toEmail, studentName)
    }
    class Student {
        <<reused, changed>>
        -LocalDateTime deactivatedAt
    }
    class StudentCourseAdminAuthorizationManager {
        <<new>>
    }
    class StudentInstructorAuthorizationManager {
        <<reused>>
    }
    class StudentSecurityService {
        <<reused, changed>>
        +isCurrentUserCourseAdminOfStudent(studentId) boolean
    }
    class ActiveAccountJwtAuthenticationConverter {
        <<new>>
        +convert(jwt) AbstractAuthenticationToken
    }
    class EvaluationService {
        <<reused, changed>>
        +addPeerEvaluation(evaluation)
        +updatePeerEvaluation(evaluationId, update)
        +generateWeeklyPeerEvaluationReportForSection(sectionId, week)
    }
    StudentController --> StudentService
    StudentController --> StudentDeactivationNotifier
    StudentDeactivationNotifier --> EmailService
    StudentService --> Student
    StudentCourseAdminAuthorizationManager --> StudentSecurityService
    StudentInstructorAuthorizationManager --> StudentSecurityService
    EvaluationService --> Student
```

**Backend**
- `student/`: `StudentController`, `StudentService`, `Student`, `StudentSecurityService`, new `StudentDeactivationNotifier` and `StudentAlreadyDeactivatedException`.
- `security/`: `SecurityConfiguration`, new `ActiveAccountJwtAuthenticationConverter`, new `authorizationmanagers/StudentCourseAdminAuthorizationManager`.
- `evaluation/EvaluationService`, `system/EmailService`, `system/exception/ExceptionHandlerAdvice`.

**Frontend**
- `pages/students/EditStudentForm.vue`: a Deactivate button for an active student, an `ElMessageBox.confirm` warning with the consequences in the use case's Associated Information, and the Status switch shown only for a deactivated student.
- `apis/student/`: `deactivateStudent`; its types.
- `pages/evaluations/SubmitTeamsEvaluations.vue`: leaves out a deactivated teammate, for a new form and for one showing submitted evaluations.

## Sequence

### UC-STU-deactivate-student: main success scenario

```mermaid
sequenceDiagram
    actor I as Course admin / instructor
    participant SPA as EditStudentForm.vue
    participant SEC as SecurityConfiguration
    participant API as StudentController
    participant SVC as StudentService
    participant MAIL as StudentDeactivationNotifier
    participant DB
    I->>SPA: open the student's details (steps 1-2)
    I->>SPA: choose Deactivate (step 3)
    SPA-->>I: warning with the consequences (step 4)
    I->>SPA: confirm (step 5)
    SPA->>SEC: POST /students/{studentId}/deactivate
    SEC->>SEC: instructor of her course section, or admin of her course
    SEC->>API: admitted
    API->>SVC: deactivateStudent(studentId)
    SVC->>DB: enabled=false, deactivatedAt=now(clock), commit (step 6)
    SVC-->>API: student
    API->>MAIL: notify(student), after the commit (step 6)
    MAIL-->>API: noticeSent
    API-->>SPA: Result { student, noticeSent }
    SPA-->>I: alert that she is deactivated (step 6)
```

### UC-STU-deactivate-student: a session already open

```mermaid
sequenceDiagram
    participant SPA as Deactivated student's SPA
    participant F as ActiveAccountJwtAuthenticationConverter
    participant DB
    SPA->>F: any request with a JWT still in date
    F->>DB: read the account by the userId claim
    F-->>SPA: 401, and the SPA redirects to login
    SPA->>F: POST /users/login (HTTP Basic)
    Note over SPA,F: Login already refuses her through MyUserPrincipal.isEnabled, 401
```

## API contract

| Endpoint or job | Caller (who may) | Request | Success | Errors, by extension |
|---|---|---|---|---|
| `POST /students/{studentId}/deactivate` (new) | Course admin of her course, or an instructor assigned to her course section | None | 200, `{ student, noticeSent }` | 3a: 403, also for an unknown id. 6a: 409. 6b is a success with `noticeSent=false` |
| `GET /students/{studentId}` (rule widened) | Today's callers, plus the course admin of her course | None | Unchanged | 403 otherwise |
| `PUT /students/{studentId}` (behavior changed) | Unchanged | `enabled` applies only from false to true, and clears `deactivatedAt` | Unchanged | A request to disable is ignored, as a student's own `enabled` is today |
| `POST /evaluations`, `PUT /evaluations/{evaluationId}` (behavior changed) | Unchanged | Unchanged | Unchanged | UC-EVA-submit-evaluation 9a: 400 if the evaluatee is deactivated |
| Any request bearing a JWT (behavior changed) | Any | Unchanged | Unchanged | 401 if the account is deactivated or gone (FR-SEC-active-account) |

## Key decisions

- **A dedicated route, not `enabled` on `PUT`.** Rejected keeping the flag on `PUT`: it cannot enforce the warning or record the date, and it mixes with name and email edits. `updateStudent` therefore ignores a change to disabled, so there is one way to deactivate.
- **Authorization.** The route's rule is `AuthorizationManagers.anyOf(studentInstructorAuthorizationManager, studentCourseAdminAuthorizationManager)`, the shape the reminder routes use. Rejected reusing `UserOwnershipAuthorizationManager`: it shuts out a course admin not assigned to the course section and admits the student herself, both against BR-student-lifecycle. The `GET` rule gains the same course admin, or she could not reach step 2.
- **Check the account in a wrapper around `JwtAuthenticationConverter`.** The wrapper reads `UserRepository` and throws `DisabledException`, which reaches the existing 401 through `CustomBearerTokenAuthenticationEntryPoint`. Rejected a servlet filter (a second place to order and handle errors), a token blocklist (storage for what one read answers), and shortening the token's life (it narrows the gap but does not close it). Cost: one primary-key read per authenticated request. It applies to instructors too, as FR-SEC-active-account requires.
- **Email in the controller, after the service commits.** Rejected sending inside the transaction: it holds the transaction across SMTP, and a failure could undo the deactivation. Rejected `@TransactionalEventListener(AFTER_COMMIT)`: its result cannot reach the response, and 6b needs it. The notifier catches the mail failure, as `ReminderService` does.
- **She stays on her team.** Rejected removal: the course section report filters on team, an instructor who may deactivate may not remove a student from a team (BR-team-admin-only), and reactivation would leave her teamless.
- **One nullable `deactivatedAt`.** Rejected a table of deactivated periods as too heavy for a Rare, Low-priority use case. Accepted cost: after a reactivation, the weeks in between show no peer evaluations.
- **The report rule.** For week W, a student on a team is listed if she is active; or she has a date and W is before the week of that date; or she has no date and received a peer evaluation for W (BR-deactivated-student-report-weeks). Week keys are zero-padded ISO, so string comparison orders them, and `WeekKeys.of` gives the date's key.
- **Refuse on the server and filter in the SPA.** The filter alone misses a stale page and a direct call. The refusal alone fails a teammate's submission, since the form saves evaluations one at a time.

## Data model

Adds a nullable `deactivated_at DATETIME` to `peer_evaluation_user` (single-table inheritance; only `Student` maps it), in Flyway `V7__student_deactivated_at.sql`. No backfill: a student disabled earlier has no date, which the report rule handles. The rest of the model is the SRS's Business Domain Model.

## Reuse & cross-cutting

Login through `MyUserPrincipal.isEnabled`; `WeekKeys`; the injected `Clock`; `StudentInstructorAuthorizationManager`; the lookup pattern of `SectionSecurityService.isSectionOwner`; `CI-email-notifications` through `EmailService`; the SPA's existing 401 redirect in `utils/request.ts`.

## Tests

| Use case, flow | Level (unit / integration) | Asserts |
|---|---|---|
| Main: instructor assigned to her course section | Integration | 200; `enabled` false; `deactivatedAt` from the clock; `noticeSent` true; her weekly activity reports and peer evaluations still exist |
| Main: course admin not assigned to that course section | Integration | Can read her details and deactivate her. The test arranges this; the seed has no such course admin |
| Main: warning, steps 3 to 6 | Cypress, stubbed API | Consequences shown before any request; Confirm sends one POST; success alert shown |
| Main: notification | Unit | Notifier sends one email to her address and returns true |
| POST-2: team | Integration | She is still on her team after deactivation |
| 3a: another course's admin, an unassigned instructor, another student, herself, an unknown id | Integration | 403 and no change |
| 4a: cancel | Cypress, stubbed API | No POST sent; Status unchanged |
| 6a: already deactivated | Integration | 409; original `deactivatedAt` kept; no email |
| 6b: mail fails | Unit and integration | 200; `noticeSent` false; still deactivated |
| 6b: alert | Cypress, stubbed API | The alert says she could not be notified |
| Open session (FR-SEC-active-account) | Integration | Her earlier JWT gets 401 on the next request |
| Login (FR-SEC-active-account) | Integration | Login returns 401 |
| Active account | Integration | A valid JWT still passes the converter |
| Deactivated instructor (FR-SEC-active-account) | Integration | A valid JWT gets 401 |
| Reminders (BR-submission-owed) | Unit | She owes nothing, and her teammates do not owe a peer evaluation of her |
| UC-EVA-submit-evaluation 9a: new evaluation of her | Unit and integration | 400; nothing saved |
| BR-deactivated-evaluatee: edit of an evaluation of her, inside its window | Unit and integration | 400; unchanged |
| BR-deactivated-evaluatee: evaluations she wrote | Unit | Still count in her teammates' averages |
| UC-EVA-submit-evaluation 2a: the form | Cypress, stubbed API | She is left out of new and submitted forms; submitting sends nothing for her |
| Report, date recorded | Unit | Deactivated in week W: listed for W-1, not for W or later |
| Report, no date | Unit | Listed only for weeks with peer evaluations |
| Report, active student with no peer evaluations | Unit | Still listed |
| `PUT`: disable | Unit and integration | Ignored, even from an instructor. Replaces the assertion in `StudentServiceTest` near line 177 |
| `PUT`: enable | Unit and integration | Enabled; date cleared; team kept |
| Status switch | Cypress, stubbed API | Hidden for an active student; shown for a deactivated one |

The Cypress specs need the dev stack and are not in CI. If they are not run, Verify stays `🟡 Partial`.

## Open questions / risks

- Two simultaneous deactivations can both pass the 6a check and email twice. Harmless on a Rare flow, so no locking is added.
- A course admin not assigned to the course section can open the edit form but cannot save it (OI-66).
- A student who withdraws mid-week is not rated for that week or the week after, per BR-deactivated-evaluatee and BR-deactivated-student-report-weeks. Her weekly activity reports for those weeks remain.
- OI-65 is a separate gap on the neighboring delete route and is not addressed here.
