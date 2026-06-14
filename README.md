# SICPA Inventory Manager

A full-stack system for tracking which items are stored in which warehouse locations.
Each item belongs to exactly one location at a time and can be moved between locations.

Built with **Spring Boot** (backend REST API) and **Angular** (frontend SPA).

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4.1 |
| Frontend | Angular 21, TypeScript, Tailwind CSS v3 |
| Storage | In-memory (`ConcurrentHashMap`) — no database, resets on restart |

---

## Requirements Fulfilled

### Backend
- [x] `POST /api/item` — creates item if it does not exist
- [x] `POST /api/item` — updates location if item already exists
- [x] `GET /api/locations/{location}/items` — returns paginated items with `createdAt` and `updatedAt`
- [x] In-memory only (no database, no JPA)
- [x] Runs with `mvn spring-boot:run`

### Frontend
- [x] Angular component that calls both APIs
- [x] Edit button stores the selected `ItemDto` in an Angular Signal
- [x] Signal state is used to call the Upsert API on save (inline expand row, no dialog)
- [x] 1000 items handled efficiently via server-side pagination
- [x] Bulk update — select multiple items via checkboxes and move them all to a new location
- [x] Client-side sort on Item Name, Location, Created At, Updated At columns

---

## Prerequisites

| Tool | Version | Download |
|---|---|---|
| Java JDK | 21 | https://adoptium.net |
| Maven | 3.9+ | https://maven.apache.org/download.cgi |
| Node.js | 20+ | https://nodejs.org |
| npm | 10+ | Included with Node.js |

> **Maven not installed?** The backend includes a Maven wrapper — run `./mvnw spring-boot:run` instead of `mvn spring-boot:run`.

---

## How to Run

### 1. Start the Backend

```bash
cd backend
./mvnw spring-boot:run
```

Runs on `http://localhost:8080`

### 2. Start the Frontend

```bash
cd frontend
npm install
npm start
```

Open `http://localhost:4200`

---

## Using the App

**Add Item**
Click **Add Item** in the top-right. An inline panel expands with Item Name and Location fields.
- If the item does not exist → it gets created in that location.
- If the item already exists → it gets moved to the new location.

**Browse Items by Location**
Enter a location name in the search bar and press Enter or click **Search**.
Results are paginated. Use the **Show X rows** dropdown to change page size and the page navigation to move between pages.
Click any column header (Item Name, Location, Created At, Updated At) to sort the current page.

**Edit an Item**
Click the edit icon on any row. An inline panel expands below the row showing the item's current location.
Change the location and click **Save** — the item moves immediately and `updatedAt` is refreshed.

**Bulk Update**
Tick the checkboxes next to multiple rows (or the header checkbox to select all on the page).
A bar appears at the top with a location input. Enter the target location and click **Move Selected** — all selected items move in parallel.

---

## API Reference

All responses use the same envelope format:

```json
{ "status": "success | error", "message": "...", "data": { ... } }
```

On error, `data` is `null`. On success (GET), `message` is `null`.

---

### POST /api/item — Upsert Item

> **Case-sensitive:** Both `item` and `location` are matched exactly as typed.
> `Widget-A`, `widget-a`, and `WIDGET-A` are treated as three different items.
> `Warehouse-A` and `warehouse-a` are treated as two different locations.

**Request Body**

| Field | Type | Required | Description |
|---|---|---|---|
| `item` | string | Yes | Item name — unique identifier, **case-sensitive** |
| `location` | string | Yes | Location to assign the item to, **case-sensitive** |

**Create a new item**
```bash
curl -X POST http://localhost:8080/api/item \
  -H "Content-Type: application/json" \
  -d '{"item": "Widget-A", "location": "Warehouse-A"}'
```

```json
{
  "status": "success",
  "message": "Item Widget-A created in location: Warehouse-A",
  "data": { "item": "Widget-A", "location": "Warehouse-A" }
}
```

**Move item to a different location** (same endpoint, same body)
```bash
curl -X POST http://localhost:8080/api/item \
  -H "Content-Type: application/json" \
  -d '{"item": "Widget-A", "location": "Warehouse-B"}'
```

```json
{
  "status": "success",
  "message": "Item Widget-A updated in location: Warehouse-B",
  "data": { "item": "Widget-A", "location": "Warehouse-B" }
}
```

**Validation error** (blank fields return 400)
```json
{
  "status": "error",
  "message": "item and location are required",
  "data": null
}
```

---

### GET /api/locations/{location}/items — Get Items by Location

> **Case-sensitive:** The location path must match exactly — `Warehouse-A` and `warehouse-a` return different (independent) result sets.

**Path Variable**

| Variable | Type | Required | Description |
|---|---|---|---|
| `location` | string | Yes | Location name to query, **case-sensitive** |

**Query Parameters**

| Parameter | Type | Required | Default | Description |
|---|---|---|---------|---|
| `page` | integer | No | `0`     | Page index, zero-based |
| `size` | integer | No | `20`    | Number of items per page |

```bash
# Minimal — uses default page=0, size=10
curl "http://localhost:8080/api/locations/Warehouse-A/items"

# With explicit pagination
curl "http://localhost:8080/api/locations/Warehouse-A/items?page=2&size=5"
```

```json
{
  "status": "success",
  "message": null,
  "data": {
    "items": [
      {
        "item": "Widget-A",
        "location": "Warehouse-A",
        "createdAt": "2024-01-15T10:30:00Z",
        "updatedAt": "2024-01-16T08:45:00Z"
      }
    ],
    "page": 0,
    "size": 10,
    "total": 1,
    "totalPages": 1
  }
}
```

**Response fields**

| Field | Description |
|---|---|
| `items[].item` | Item name (unique identifier) |
| `items[].location` | Current location |
| `items[].createdAt` | ISO-8601 UTC — when the item was first added to the system (never changes) |
| `items[].updatedAt` | ISO-8601 UTC — when the item was last created or moved |
| `page` | Current page index (zero-based) |
| `size` | Page size used |
| `total` | Total items across all pages |
| `totalPages` | Total number of pages — `⌈total / size⌉` |

---

## Seed 1000 Items

To test pagination with 1000 items, make sure the backend is running then execute:

**macOS / Linux**
```bash
for i in $(seq 1 1000); do
  curl -s -X POST http://localhost:8080/api/item \
    -H "Content-Type: application/json" \
    -d "{\"item\": \"item-$i\", \"location\": \"Warehouse-A\"}"
  echo
done
echo "Done — 1000 items seeded in Warehouse-A"
```

**Windows (PowerShell)**
```powershell
1..1000 | ForEach-Object {
  Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/item" `
    -ContentType "application/json" `
    -Body "{`"item`": `"item-$_`", `"location`": `"Warehouse-A`"}"
}
Write-Host "Done — 1000 items seeded in Warehouse-A"
```

After seeding, search for `Warehouse-A` in the UI to browse all 1000 items across 100 pages (default size 10).

---

## Architecture

For a detailed breakdown of the layer structure, data flow diagrams, and Signal state map see **[ARCHITECTURE.md](./ARCHITECTURE.md)**.

---

## Design Decisions & Assumptions

### Backend

**Why four maps?**

The API needs to answer multiple questions efficiently:
- "Where is this item?" → `itemLocationMap` — needed on every upsert to find the item's current location
- "What items are in this location?" → `locationItemsMap` — needed on every GET
- "When was this item first added?" → `itemCreatedAtMap` — creation timestamp, preserved across moves
- "When was this item last moved?" → `itemUpdatedAtMap` — updated on every upsert

Each lookup is O(1). Without separate maps, answering any of these would require scanning.

Example — after adding Widget-A and Widget-B to Warehouse-A, then moving Widget-A to Warehouse-B:

```
itemLocationMap          locationItemsMap
────────────────         ──────────────────────────────────────────
Widget-A → Warehouse-B   Warehouse-A → [Widget-B]
Widget-B → Warehouse-A   Warehouse-B → [Widget-A]

itemCreatedAtMap         itemUpdatedAtMap
────────────────         ────────────────
Widget-A → T1            Widget-A → T2   ← updated on move
Widget-B → T1            Widget-B → T1   ← unchanged
```

`createdAt` uses `putIfAbsent` — written once and preserved across every subsequent move.
`updatedAt` uses `put` — overwritten on every upsert.

`LinkedHashSet` was chosen for location values because it gives O(1) insert/contains (vs `List`'s O(n) contains) while preserving insertion order for consistent pagination across requests (unlike `HashSet`).

**Thread safety**

`ConcurrentHashMap` makes each individual map operation atomic, but an upsert is a multi-step sequence: read old location → remove from old → write to new → update timestamps. Without extra synchronization, a concurrent GET could observe an item in neither location mid-update. Both `upsertItem` and `getItemsByLocation` are `synchronized` to protect the full sequence as a unit.

For the reasoning behind choosing `synchronized` over `ReadWriteLock`, see [ARCHITECTURE.md → Why `synchronized` over `ReadWriteLock`](./ARCHITECTURE.md).

**Assumptions**

- **Item name is the system-wide unique identifier.** An item can exist in only one location at a time. Moving an item from `Warehouse-A` to `Warehouse-B` updates the same item's location; it does not create a duplicate.
- **Item name and location are both case-sensitive.** `Widget-A` and `widget-a` are two separate items; `Warehouse-A` and `warehouse-a` are two separate locations. No normalisation (lowercasing, trimming beyond leading/trailing whitespace) is applied.
- In-memory state does not persist across restarts
- Pagination is zero-indexed (`page=0` is the first page)
- `createdAt` is set once on first insert and never changes, even when the item is moved

---

### Frontend

**Angular Signal for local state (key requirement)**

When the user clicks Edit on a row, the full `ItemDto` object is stored in an Angular Signal (`editingItem`). This signal acts as local state — it holds the item being edited before any change is sent to the server. When the user confirms in the inline panel, the signal value is read and passed directly to the Upsert API. If the user cancels, the signal is cleared and no API call is made.

As a visible side effect, the row being edited expands with an input field below it, driven by the signal value.

**Inline editing (no dialog)**

Editing happens in an inline expand row directly in the table — no modal, drawer, or CDK overlay. The edit row appears immediately below the item row. This keeps the user in context (they can see the item they are editing and surrounding rows), requires no overlay state management, and avoids focus-trap complexity.

**Bulk update**

Selecting multiple rows via checkboxes activates a bulk action bar. Entering a target location and clicking **Move Selected** fires all upsert API calls in parallel using RxJS `forkJoin`. All selected items move in a single round-trip cycle, and the table refreshes once all calls complete.

**Handling 1000 items efficiently**

The backend returns one page at a time. The frontend renders only the current page's rows — DOM size stays constant regardless of whether there are 20 or 1000 total items. The **Show X rows** dropdown lets the user choose 20, 50, or 100 items per page. No virtual scroll library is needed because the heavy lifting is done server-side.

**Client-side sort**

Clicking a column header sorts the current page in memory without triggering an API call. This is intentionally scoped to the current page — a full cross-page sort would require a new backend parameter and is outside the assessment scope.
