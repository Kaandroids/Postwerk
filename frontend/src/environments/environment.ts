export const environment = {
  production: false,
  // Relative so `ng serve` routes API calls through the dev proxy
  // (proxy.conf.json → http://localhost:8080), avoiding CORS. Mirrors prod,
  // where nginx proxies /api to the backend.
  apiUrl: '/api/v1',
};
