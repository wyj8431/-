# P1 Homepage Topic Management

## Delivered

- Added administrator-only create, edit, delete, and status transitions for `home_topic`.
- Added validation for topic codes, types, time ranges, published cover references, and template associations.
- Added transactional replacement of topic-template relations and audit events for topic mutations.
- Added `V8__home_topic_admin_lifecycle.sql` to make topic IDs auto-increment while preserving the foreign key.
- Added `/admin/home-topics` with filtering, status controls, CRUD dialogs, and operator read-only behavior.

## Verification

| Area | Command | Result |
| --- | --- | --- |
| Topic service | `server\\mvnw.cmd -Dtest=HomeTopicAdminServiceTest test` | 3 passed |
| Database migration | `server\\mvnw.cmd -Dtest=DatabaseMigrationIT test` | 11 passed; Flyway V1-V8 applied in MySQL 8.4 |
| Frontend unit | `poster-client\\npm run test:unit` | 20 files / 66 passed |
| Frontend production build | `poster-client\\npm run build -- --configLoader runner` | passed |

## Deferred

- WeChat binding and login acceptance remain intentionally deferred.
- P2 editor, schema editing, canvas interactions, image processing, export, and free canvas remain outside this slice.
