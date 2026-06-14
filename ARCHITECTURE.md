# Architecture

---

## Backend

### Layer Structure

```
                    HTTP Request
                         │
                         ▼
┌───────────────────────────────────────────────────────┐
│  Controller  (ItemController)                         │
│  • Validates input (blank fields, page/size bounds)   │
│  • Maps HTTP ↔ service calls                          │
│  • Returns ApiResponse envelope on every response     │
└────────────────────────┬──────────────────────────────┘
                         │
                         ▼
┌───────────────────────────────────────────────────────┐
│  Service  (ItemService)                               │
│  • Owns business logic (upsert sequence, pagination)  │
│  • synchronized — protects multi-step operations      │
└─────────────────────────┬─────────────────────────────┘
                         │
                         ▼
┌───────────────────────────────────────────────────────┐
│  Repository  (ItemRepository)                         │
│  • Only knows about the in-memory maps                │
│  • No business logic — pure read/write operations     │
└────────────────────────┬──────────────────────────────┘
                         │
                         ▼
┌───────────────────────────────────────────────────────┐
│  In-Memory Store                                      │
│  itemLocationMap:   ConcurrentHashMap<String,String>  │
│  locationItemsMap:  ConcurrentHashMap<String,Set>     │
│  itemCreatedAtMap:  ConcurrentHashMap<String,Instant> │
│  itemUpdatedAtMap:  ConcurrentHashMap<String,Instant> │
└───────────────────────────────────────────────────────┘
```

Each layer has one responsibility. The Controller never touches the maps directly, and the Repository never makes business decisions.

---

### Data Flow — Upsert Item

```
POST /api/item  {"item": "Widget-A", "location": "Warehouse-B"}
       │
       ▼ Controller: validate (not blank)
       │
       ▼ Service (synchronized):
       │   1. itemLocationMap.get("Widget-A")                       → "Warehouse-A" (old)
       │   2. locationItemsMap["Warehouse-A"].remove("Widget-A")
       │   3. locationItemsMap["Warehouse-B"].add("Widget-A")
       │   4. itemLocationMap.put("Widget-A", "Warehouse-B")
       │   5. itemCreatedAtMap.putIfAbsent("Widget-A", now())       ← no-op if exists
       │   6. itemUpdatedAtMap.put("Widget-A", now())               ← always updated
       │
       ▼ Controller: wrap in ApiResponse → 200 OK
```

Step 2 → 3 is why `synchronized` is required. Without it, a concurrent GET between those two steps would see Widget-A in neither location.

**createdAt vs updatedAt**

`putIfAbsent` on `itemCreatedAtMap` means the creation timestamp is written exactly once — the first time an item appears — and survives every subsequent move unchanged.

`put` on `itemUpdatedAtMap` overwrites on every upsert, so it always reflects the most recent create-or-move operation.

**Why `synchronized` over `ReadWriteLock`**

`ReadWriteLock` allows concurrent reads (multiple GETs run in parallel) while `synchronized` serializes everything including reads. The tradeoff:

| | `synchronized` | `ReadWriteLock` |
|---|---|---|
| Concurrent reads | ❌ Serialized | ✅ Parallel |
| Write starvation risk | ✅ None | ⚠️ Continuous reads can starve writes |
| Code safety | ✅ JVM releases lock automatically | ⚠️ Must use try-finally or risk deadlock |
| Overhead at low concurrency | ✅ Lower | ❌ More internal state tracking |

`synchronized` was chosen for three reasons:

1. **Safety** — the JVM releases the lock automatically even if an exception is thrown. `ReadWriteLock` requires explicit `try-finally`; a missed `unlock()` deadlocks the entire server permanently.
2. **No write starvation risk** — in an inventory system, upserts happen frequently alongside reads. With `ReadWriteLock`, a continuous stream of read requests can prevent writes from ever acquiring the lock.
3. **Scale fits the requirement** — for 1,000 items the critical section completes in microseconds, so serializing reads has no measurable impact.

If profiling ever showed concurrent read contention was a bottleneck, switching to `ReentrantReadWriteLock(true)` (fair mode) would be the next step.

---

### Data Flow — Get Items by Location

```
GET /api/locations/Warehouse-A/items?page=1&size=20
       │
       ▼ Controller: validate (page ≥ 0, size > 0)
       │
       ▼ Service (synchronized):
       │   1. repository.findByLocation("Warehouse-A", page=1, size=20)
       │      └─ stream().skip(10).limit(10)
       │            .map(name → new ItemDto(name, location, createdAt, updatedAt))
       │            .toList()
       │   2. repository.countByLocation("Warehouse-A") → 25
       │   3. totalPages = ceil(25 / 10) = 3
       │
       ▼ Controller: wrap in ApiResponse → 200 OK
```

---

### Error Handling

All errors return the same `ApiResponse` envelope so clients never need to handle different error shapes.
4xx errors are logged at `WARN`; unhandled exceptions are logged at `ERROR` with full stack trace.

```
┌──────────────────────────────────┬────────────────────────────────────────┐
│ Scenario                         │ Handler                                │
├──────────────────────────────────┼────────────────────────────────────────┤
│ Blank item or location           │ Controller — manual check → 400        │
│ page < 0 or size ≤ 0             │ Controller — manual check → 400        │
│ page=abc (wrong type)            │ GlobalExceptionHandler → 400 + WARN    │
│ Malformed / missing JSON body    │ GlobalExceptionHandler → 400 + WARN    │
│ Unknown route                    │ GlobalExceptionHandler → 404 + WARN    │
│ Unhandled runtime exception      │ GlobalExceptionHandler → 500 + ERROR   │
└──────────────────────────────────┴────────────────────────────────────────┘
```

---

### Package Structure

```
com.sicpa.inventory/
├── controller/
│   └── ItemController.java          # HTTP layer
├── service/
│   └── ItemService.java             # Business logic
├── repository/
│   └── ItemRepository.java          # In-memory data access (4 maps)
├── dto/
│   ├── common/
│   │   ├── ApiResponse.java         # Universal response envelope
│   │   └── PagedItemsResponse.java  # GET response payload
│   ├── item/
│   │   ├── ItemDto.java             # Per-item record (item, location, createdAt, updatedAt)
│   │   └── UpsertItemDto.java       # Internal service → controller carrier
│   ├── request/
│   │   └── UpsertItemRequest.java   # POST request body
│   └── response/
│       └── UpsertItemResponse.java  # POST response payload
└── exception/
    └── GlobalExceptionHandler.java  # @RestControllerAdvice + SLF4J logging
```

---

## Frontend

### Layer Structure

```
                 User Interaction
                        │
                        ▼
┌───────────────────────────────────────────────────────┐
│  Component  (ItemListComponent)                       │
│  • Owns all UI state via Angular Signals              │
│  • Handles user events (search, add, edit, paginate)  │
│  • Calls ItemService for all API communication        │
└───────────────────────┬───────────────────────────────┘
                        │
                        ▼
┌───────────────────────────────────────────────────────┐
│  Service  (ItemService)                               │
│  • Thin HTTP client — no state, no logic              │
│  • Returns Observables for the component to subscribe │
└───────────────────────┬───────────────────────────────┘
                        │
                        ▼
┌───────────────────────────────────────────────────────┐
│  Backend REST API  (http://localhost:8080)            │
└───────────────────────────────────────────────────────┘
```

---

### Signal State Map

Angular Signals are used for all reactive state. The template re-renders automatically when a signal changes.

```
Signal              Type                       Purpose
─────────────────   ────────────────────────   ──────────────────────────────────────────────
items               signal<ItemDto[]>           Current page's items from the API
loading             signal<boolean>             Shows loading bar during API calls
error               signal<string|null>         API error banner (dismissible)
toast               signal<string|null>         Success notification (auto-dismisses 3 s)
currentPage         signal<number>              Active page index (zero-based)
totalItems          signal<number>              Total items across all pages
totalPages          signal<number>              Total pages ⌈total/size⌉
pageSize            signal<number>              Current page size (20 / 50 / 100)
hasSearched         signal<boolean>             Whether any search has been performed yet
searchFormTouched   signal<boolean>             Enables search validation display
editingItem         signal<ItemDto|null>        Item currently open for inline editing
inlineAddTouched    signal<boolean>             Enables add-form validation display
inlineEditTouched   signal<boolean>             Enables inline-edit validation display
addingInline        signal<boolean>             Controls visibility of the add panel
selectedItems       signal<Set<string>>         Set of item names selected for bulk update
bulkTouched         signal<boolean>             Enables bulk-bar validation display
bulkLoading         signal<boolean>             Disables bulk button while calls are in-flight
sortField           signal<field|null>          Active sort column (null = insertion order)
sortDirection       signal<asc|desc>            Current sort direction

── Computed (derived automatically) ────────────────────────────────────────────────────────
sortedItems         computed<ItemDto[]>         items() sorted client-side on sortField
visiblePages        computed<(number|'...')[]>  Page numbers with ±2 sliding window
hasNextPage         computed<boolean>           currentPage < totalPages - 1
hasPrevPage         computed<boolean>           currentPage > 0
showingFrom         computed<number>            First item number on current page (1-based)
showingTo           computed<number>            Last item number on current page
selectedCount       computed<number>            selectedItems().size
allSelected         computed<boolean>           All visible rows are in selectedItems
someSelected        computed<boolean>           Some (but not all) visible rows selected
```

---

### Edit Item Flow (Signal as Local State)

```
User clicks edit icon on "Widget-A" row
       │
       ▼ editingItem.set(dto) ← full ItemDto stored in Signal (local state)
       │  Row edit panel expands inline below the row
       │  inlineEditLocation pre-filled with dto.location
       │
       │  User types new location "Warehouse-B"
       │
  ┌────┴──────────────────────────────────────────┐
  │ Cancel                       Save             │
  │   editingItem.set(null)        editingItem()  │
  │   panel collapses              .item + new    │
  │   no API call                  location sent  │
  │                                to Upsert API  │
  └───────────────────────────────────────────────┘
```

The Signal is what the requirement asks for: *"store that object in an Angular Signal — the signal holds the local state of the item before it is sent back to the backend."*

---

### Bulk Update Flow

```
User ticks checkboxes on rows (or header checkbox for all)
       │
       ▼ selectedItems.update(set → new Set with item added/removed)
       │  Header checkbox shows dash (indeterminate) or tick (all selected)
       │  Bulk action bar appears at top with location input
       │
User enters "Warehouse-C" and clicks Move Selected
       │
       ▼ forkJoin([
       │     upsertItem({item: "Widget-A", location: "Warehouse-C"}),
       │     upsertItem({item: "Widget-B", location: "Warehouse-C"}),
       │     ...
       │   ]).subscribe(...)
       │
       ▼ All calls fire in parallel → single success/error handler
         clearSelection() → table reloads
```

`forkJoin` fires all API calls simultaneously. If any single call fails, the error handler fires and a banner shows which items may not have been moved.

---

### Data Flow — Search & Pagination

```
User types "Warehouse-A" → presses Enter or clicks Search
       │
       ▼ currentPage.set(0), sortField.set(null), clearSelection()
       │
       ▼ ItemService.getItemsByLocation("Warehouse-A", page=0, size=10)
       │      GET /api/locations/Warehouse-A/items?page=0&size=10
       │
       ▼ Response: { items: [{item, location, createdAt, updatedAt}, ...], total: 1000, totalPages: 100 }
       │
       ▼ items.set([...])          → table renders current page rows
         totalItems.set(1000)
         totalPages.set(100)
         visiblePages recomputes   → pagination bar shows page numbers
```

Only the current page's rows exist in the DOM at any time — constant regardless of total item count.
Changing page size resets to page 0, clears selection, and reloads.

---

### Folder Structure

```
src/app/
├── app.ts                          # Root component — renders <router-outlet>
├── app.routes.ts                   # Root routes — lazy loads inventory feature
├── app.config.ts                   # App-level providers (HttpClient, Router)
│
└── features/
    └── inventory/
        ├── inventory.routes.ts     # Feature routes — lazy loads ItemListComponent
        ├── components/
        │   └── item-list/          # Main page (search, add, edit, bulk, table, pagination)
        ├── services/
        │   └── item.service.ts     # HTTP client for backend API
        └── models/
            └── item.model.ts       # TypeScript interfaces (ApiResponse, ItemDto, etc.)
```

**Lazy loading:** `inventory.routes.ts` and `ItemListComponent` are only downloaded by the browser when the user first navigates to the inventory page.

**Styling:** Tailwind CSS v3 via PostCSS. All styles are utility classes in the template — no component-scoped CSS except `:host { display: block }`. Angular's build pipeline detects `tailwind.config.js` + `postcss.config.js` and runs PurgeCSS automatically in production builds.
