# TMLibrary Frontend

Vue 3 + TypeScript + Vite admin SPA paired with the TMLibrary Spring Boot backend.

## Quick Start

```bash
npm install
npm run dev          # http://localhost:5173, /api/* proxied to backend
npm run build        # vue-tsc type-check + vite build
npm run preview      # serve the production build locally
npm run type-check   # vue-tsc only (no emit)
```

Dev proxy target is set in `vite.config.ts` (defaults to `http://172.21.224.134:8080`).
Change there if the backend runs elsewhere.

## Tech Stack

- **Vue 3.5** + Composition API (`<script setup lang="ts">`)
- **TypeScript 6** (strict), `vue-tsc` for type-checking
- **Vite 8** with Element Plus auto-import (`unplugin-vue-components`, `unplugin-auto-import`)
- **Element Plus 2.8** + `@element-plus/icons-vue`
- **Pinia 2** for state (no Vuex)
- **Vue Router 4**
- **Axios 1** with a single `http<T>()` wrapper (see Architecture below)

## Structure

```
src/
├── api/             Domain API modules — each wraps http<T>() and returns Promise<T>
│                    (auth.ts, book.ts, purchase.ts, user.ts)
├── components/      Reusable UI — Pager, FormDialog, SearchBar, SidebarMenu
├── composables/     useTheme (singleton light/dark theme state)
├── directives/      v-reveal (IntersectionObserver scroll-in animation)
├── layouts/         Chrome shells — AdminLayout, MallLayout, UserLayout
├── router/          routes.ts (table) + index.ts (guard + title sync)
├── stores/          Pinia — user (JWT), cart (mock localStorage), loading (global gate)
├── styles/          theme.css (CSS vars + reduced-motion + reveal animations)
├── types/           api.d.ts — hand-written backend DTO contracts
├── utils/           request.ts (http + Result unwrap + 401), format.ts, safeUser.ts
├── views/
│   ├── auth/        Login, Register
│   ├── book/        Admin book CRUD (List, Edit)
│   ├── landing/     Public landing + LandingNav + LoadingScreen + BackToTop
│   ├── mall/        Public book mall
│   ├── purchase/    Admin purchase list/create
│   └── user/        Manage (admin) + UserLayout children (Books, Cart, Profile, Orders, Settings)
├── App.vue          Root — mounts <router-view />
├── auto-imports.d.ts   Generated — DO NOT edit (auto-import)
├── components.d.ts     Generated — DO NOT edit (auto-import)
├── main.ts          createApp + Pinia + router + initTheme + mount
└── style.css        Global resets + box-sizing + color-scheme
```

## Architecture

### Router (`src/router/`)

Four layout zones:

| Path prefix | Layout | Access |
|---|---|---|
| `/`, `/login`, `/register`, `/:pathMatch(.*)*` | (none, single components) | public |
| `/mall/*` | MallLayout | public |
| `/admin/*` | AdminLayout | ADMIN or BOSS |
| `/user/*` | UserLayout | any logged-in user |

`meta` conventions: `public` (skip guard), `admin` (role gate), `title` (browser tab + breadcrumb).
`router.afterEach` syncs `document.title` from `meta.title`.

### State (`src/stores/`)

- **`user`** — JWT token + decoded `{uid, username, role}` claims. Token persisted in
  `localStorage['tm_admin_token']`. Rehydrated on store init (no API call needed).
- **`cart`** — Mock cart stored in `localStorage['tm_user_cart']`. NOT backend-synced.
- **`loading`** — Global app gate. `LoadingScreen` flips `isLoading=false` after first paint;
  Landing content uses `v-if="!loadingStore.isLoading"` so DOM doesn't render until ready.

### HTTP (`src/utils/request.ts`)

Single `http<T>(config)` wrapper used by every domain module:

1. Injects `Authorization: Bearer <token>` from the user store
2. Unwraps backend `Result<T>` → returns `T` directly to callers
3. On 401: clears store + redirects to `/login?redirect=...` (debounced 500ms)
4. On business error: `ElMessage.error(result.msg)` + throws `ApiError(code, msg)`

Path constants for case-sensitive backend routes live at the top of each `src/api/*.ts`
module (e.g. `BOOK_PATH.SEARCH_AUTHOR = '/api/books/search/Author'`) — backend camelCase
paths are pinned once so typos don't propagate.

### Theme (`src/composables/useTheme.ts` + `src/styles/theme.css`)

- Module-singleton ref; `initTheme()` is called once in `main.ts` before mount to avoid flash
- Priority: `localStorage['tm_landing_theme']` → `prefers-color-scheme` → light
- `toggleTheme()` flips and persists; components consume via `useTheme()`
- Theme tokens defined in `theme.css` as `:root` / `:root[data-theme='dark']` pairs
- Global `@media (prefers-reduced-motion: reduce)` rule kills all animations + transitions
  for a11y

### Auth

- Backend issues HS256 JWT with `{uid, sub, role}` claims
- Frontend decodes the payload (no signature check — backend's `JwtAuthFilter` already validated)
- `useUserStore.isAdmin` is `true` for `role === 'ADMIN' || 'BOSS'`

## Conventions

- **`<style scoped>` on every component** (17/17 view files comply; no exceptions).
  Global CSS lives in `main.ts` imports only (`element-plus/dist/index.css`, `theme.css`, `style.css`).
- **Auto-import** for Vue composition API and Element Plus components — no manual imports
  needed in templates. `auto-imports.d.ts` and `components.d.ts` are generated.
- **Backend DTOs** mirrored by hand in `src/types/api.d.ts`. When the Java side changes a
  field, update this file in the same commit.
- **Backend casing matters** — some endpoints use camelCase paths (`/search/Author/`,
  `/search/CreatedTime/`). See the `BOOK_PATH` constant in `api/book.ts` for the
  authoritative list.

## Common Tasks

**Add an API endpoint**
1. Add the path constant in the relevant `src/api/<domain>.ts`
2. Add the typed function calling `http<T>({ ... })`
3. Add the request/response types to `src/types/api.d.ts` if not present

**Add a page**
1. Create `src/views/<zone>/<Name>.vue`
2. Register the route in `src/router/routes.ts` (set `meta.title` and `meta.public`/`meta.admin` as needed)
3. If it's a new section of an existing layout, mount inside that layout's router-view;
   if it's a new zone, add a new layout and route it

**Add a store**
1. Create `src/stores/<name>.ts` with `defineStore('<name>', () => { ... })`
2. Use it in components via `const store = useXxxStore()` (auto-imported)
