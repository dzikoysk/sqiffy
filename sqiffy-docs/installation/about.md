---
outline: deep
aside: false
---

# About

**sqiffy** _(or just squiffy 🍹)_ - Experimental compound **SQ**L framework with type-safe DSL API generated at compile-time from scheme d**iff**.
It is dedicated for applications, plugins & libraries responsible for internal database management.

### Supported databases

| Database                                                                                                       | Support          | Notes                                                                                                                                                                                                                                                                                                             |
|----------------------------------------------------------------------------------------------------------------|------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [PostgreSQL](https://www.postgresql.org/), [Embedded PostgreSQL](https://github.com/zonkyio/embedded-postgres) | Full support     | Main target of the library.                                                                                                                                                                                                                                                                                       |
| [MariaDB](https://mariadb.org/), [MySQL](https://www.mysql.com/)                                               | Supported        | All operations should be supported, but some of the features might not be available.                                                                                                                                                                                                                              |
| [SQLite](https://www.sqlite.org/index.html)                                                                    | Work in progress | SQLite does not provide several crucial schema update queries & type system is flexible. Because of that, schema updates are based on top of the modifications applied to `sqlite_master`, but the stability of this solution is unknown. See [#2](https://github.com/dzikoysk/sqiffy/issues/2) for more details. |
| [H2 (MySQL mode)](http://www.h2database.com/html/features.html#compatibility)                                  | Unstable         | Such as SQLite, H2 implements SQL standard on their own & some of the compatibility features are just a fake mocks. In most cases, it's just better to use other databases (or their embedded variants).                                                                                                          |

