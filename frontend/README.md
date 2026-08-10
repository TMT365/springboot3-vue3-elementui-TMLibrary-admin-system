# TMLibrary Frontend

Vue 3 + TypeScript + Vite admin SPA.

## Scripts

```bash
# Install dependencies (run once after clone)
npm install

# Dev server with HMR (defaults to http://localhost:5173)
npm run dev

# Production build (runs vue-tsc type-check then vite build)
npm run build

# Preview the production build locally
npm run preview
```

## Structure

```
src/
├── App.vue       # Root component
├── main.ts       # createApp().mount('#app')
└── style.css     # Global resets
```

Add router / state / API client as the backend CRUD is wired up.