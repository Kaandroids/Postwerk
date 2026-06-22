# Stage 1: Build
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci || npm install --no-audit
COPY . .
RUN npm run build -- --configuration=production

# Stage 2: Serve (production-hardened)
FROM nginx:alpine

# Copy built Angular app
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html

# Copy nginx config with SPA fallback routing
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Run nginx as non-root (nginx:alpine supports this via envsubst)
RUN chown -R nginx:nginx /usr/share/nginx/html && \
    chown -R nginx:nginx /var/cache/nginx && \
    chown -R nginx:nginx /var/log/nginx && \
    touch /var/run/nginx.pid && chown nginx:nginx /var/run/nginx.pid

USER nginx

EXPOSE 80

# Container health check — verifies nginx is serving content
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -q --spider http://127.0.0.1:80/ || exit 1
