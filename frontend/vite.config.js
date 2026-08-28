import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

/** Where the API lives in development. Shared by the dev server and `preview`. */
const apiProxy = {
  '/api': {
    target: process.env.VITE_API_PROXY_TARGET || 'http://localhost:8080',
    changeOrigin: true,
  },
}

export default defineConfig({
  plugins: [react()],

  /*
   * Pre-bundle these at startup instead of letting Vite discover them.
   *
   * pdf.js and MSAL are reached through dynamic imports now, so Vite does not
   * see them when it scans the entry graph. It finds them the first time a
   * screen actually needs one, and re-optimising mid-session costs a pause and
   * a full page reload - exactly the stall you get on the first PDF or the
   * first Microsoft sign-in. Naming them here moves that work to startup.
   */
  optimizeDeps: {
    include: [
      'react',
      'react-dom',
      'react-router-dom',
      'axios',
      'pdfjs-dist',
      '@azure/msal-browser',
    ],
  },

  server: {
    port: 5173,
    // The API base URL is relative in development, so /api is proxied to Spring Boot.
    proxy: apiProxy,
  },

  /* `npm run preview` serves the production build. Same proxy, so the built
     app can be tried against the real API - the honest way to judge speed,
     since the dev server ships unminified modules one file at a time. */
  preview: {
    port: 4173,
    proxy: apiProxy,
  },

  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.js',
    css: false,
  },
})
