# Local Storage

| Version | Status | Last updated |
|---|---|---|
| 1.0.0 | Accepted | 2026-11-25 |

## Summary

This spec sets where the app keeps data on the device, and how that data belongs to a library. Data that belongs to a library lives in one Room database, where every row names the library it belongs to. Secrets, settings shared by every library, and binary caches stay outside the database.

## Scope

**In scope**

- The database, its tables and how they are scoped to a library.
- What stays outside the database, and where.
- Moving today's on-device data into the database on upgrade.
- Deleting a library's data.
- Backup rules.
- Schema versions and migrations.

**Out of scope**

- When and how each kind of data syncs with the server. That belongs to the spec of the feature that owns the data.
- Notes, highlights, bookmarks, ratings, reviews and read status. They aren't kept on the device.
- Library filters, which aren't kept across launches, and shelves, which come from the server.
- Downloaded book files. No book is downloaded.

## Glossary

| Term | Meaning |
|---|---|
| Library | One saved Omnibus server: its address, a name, the username signed in to it, its session, and its data on this device. |
| Active library | The library the app is showing. There is at most one. |
| Per-library data | Data that belongs to one library and is meaningless on another server. |
| Device-wide data | Data shared by every library on the device. |
| Pending change | A change saved on the device that the server hasn't accepted yet. |

# Part 1: Requirements

## Where data lives

| Data | Scope | Where |
|---|---|---|
| Libraries, and which one is active | Device-wide | Database: `libraries` |
| Reading positions | Per library | Database: `reading_positions` |
| Listening positions, cached and pending | Per library | Database: `listening_positions` |
| Listening sessions waiting to be sent | Per library | Database: `listening_sessions` |
| Per-book playback speeds, cached and pending | Per library | Database: `playback_rates` |
| Dismissed resume offers | Per library | Database: `dismissed_resume_offers` |
| Sessions (auth tokens) | Per library | Encrypted DataStore `auth_token.preferences_pb`, one key per library |
| Offline covers | Per library | Disk cache at `files/covers/<library id>/` |
| Reader appearance settings | Device-wide | DataStore `reader_preferences.preferences_pb` |
| Library sort and grouping | Device-wide | DataStore `library_preferences.preferences_pb` |
| Export files | Device-wide, temporary | `cache/` |

## Requirements

### Scoping

- Every row of per-library data MUST name its library, and MUST be deleted with it.
- The app MUST only read and show the active library's per-library data.
- A pending change MUST only be sent to its own library's server, with that library's session.
- Changing the active library MUST NOT change any other library's data.
- A book's data in one library MUST NOT be affected by a book with the same id in another library.

### What is stored

- Each kind of data MUST keep the fields and meaning it has today:
  - a reading position: the EPUB CFI, the percent, and the device time it was saved;
  - a listening position: the position in seconds, the book file id, and the device time it was saved, as one cached and at most one pending value per book;
  - a listening session: the book, its start and end, its progress, its client id, and the device's UTC offset and time zone;
  - a playback speed: one cached and at most one pending value per book;
  - a dismissed resume offer: the source clock of the dismissed position, per book.
- A pending listening position or playback speed MUST only be cleared while it still equals the value that was sent, so a newer change made during the send is kept.
- Listening sessions MUST be sent in the order they were queued.
- A reading position that failed to send MUST NOT be retried. It stays as the book's local position, as before.
- A row that can't be read MUST read as absent, and MUST NOT stop other rows from being read.

### Outside the database

- Sessions MUST stay encrypted, with a key held in the Android Keystore. Room doesn't encrypt, so they MUST NOT be stored in the database.
- Reader appearance settings and the Library sort and grouping MUST stay device-wide.
- Covers MUST stay a file cache that the system doesn't reclaim when storage runs low, with one cache directory per library.

### Removing a library

- Removing a library MUST delete, together, its row and every per-library row that names it.
- Removing a library MUST also clear its session and delete its cover directory.
- Adding the same address again MUST start with no data from the removed library.

### Changing server before there are several libraries

- Saving an address on Connect that differs from the only saved library's address MUST remove that library and its data, and create a new library for the new address. A library's address identifies it, so a different server is a different library.

### Upgrading

- On the first launch after upgrading, when a server address is saved and no library exists, the app MUST:
  - create one library from the saved address, named after its host (with the port when the address has one), and make it active;
  - keep the reader signed in, by moving the saved session to that library;
  - move every reading position, listening position, listening session, playback speed and dismissed resume offer into the database, under that library;
  - move the covers into that library's cover directory.
- The upgrade MUST finish before any per-library data is read.
- The old files MUST only be deleted after everything they held is in the database. An upgrade that is interrupted MUST complete on the next launch, with nothing lost and nothing doubled.
- Pending changes MUST stay pending through the upgrade, so they are sent once the app is online.
- An entry that can't be read MUST be dropped, as it would be read as absent today.
- A fresh install MUST create no library until the reader connects to a server.

### Backup

- The database MUST be included in the Android backup.
- Sessions, the keyset that encrypts them, and covers MUST be excluded. A session can't be decrypted on another install, and covers can be fetched again.

### Schema

- The database schema MUST be exported and kept in the repository.
- Every schema version after the first MUST ship with a migration from the version before it, and a test of that migration.
- The database MUST NOT fall back to destroying data when a migration is missing.

# Part 2: Engineering

## Architecture

The data layer follows `docs/engineering/feature-layers.md`.

- **Database.** One Room database, `OmnibusDatabase`, provided as a singleton. Each per-library table has a `library_id` column with a foreign key to `libraries.id` that cascades on delete, and a primary key that starts with `library_id` (for example `(library_id, book_uuid)`).
- **Tables.**
  - `libraries`: id (a UUID), address (normalized with `ServerAddress.normalize`), name (blank means the host), username (null until known), when added, when last used, and whether it is active.
  - `reading_positions`, `listening_positions`, `listening_sessions`, `playback_rates`, `dismissed_resume_offers`: the fields under "What is stored", plus `library_id`.
- **DAOs.** One DAO per domain, beside that domain's code in `data/<domain>/local/`. Every query that reads or writes per-library data takes a library id.
- **Stores.** `ReadingPositionStore`, `AudioPositionStore`, `PlaybackRateCache` and `CrossFormatDismissStore` keep their interfaces. Their implementations move to Room and scope every call to the active library.
- **Active library.** Read from the database when it is first needed, never when a class is injected, because first-time setup creates the first library in the running process.
- **Sessions.** `LibraryTokenStore` keys tokens by library id (`auth_token.<library id>`) in the existing encrypted DataStore file. `AuthTokenStore` and `StoredAuthTokenProvider` mean the active library's token.
- **Covers.** The Coil disk cache's directory is the active library's cover directory. Keeping every library's covers under `files/covers/` lets one backup rule exclude them all, since backup rules don't take wildcards.
- **Upgrade.** An upgrade step runs at startup, before the stores are first used. It imports in one database transaction, moves the token to the library's key, and moves the cover files from `files/covers/` into `files/covers/<library id>/`. Then it deletes `server_configuration.preferences_pb`, `reading_positions.preferences_pb`, `audiobook_positions.preferences_pb`, `audiobook_playback_rates.preferences_pb` and `audiobook_resume_offers.preferences_pb`. Each step checks whether it has already run, so it can be repeated.
- **Backup rules.** `backup_rules.xml` and `data_extraction_rules.xml` exclude `auth_token.preferences_pb`, the `omnibus_auth_keyset` shared preferences and `files/covers/`, as they do today.

## Constants

| Name | Value | Meaning |
|---|---|---|
| Database name | `omnibus.db` | File name of the database. |
| Schema version | 1 | The first version of the schema. |
| Cover cache size | 256 MB | Largest each library's cover cache grows. |

---

## Changelog

**1.0.0** (2026-11-25)

- Initial spec.
