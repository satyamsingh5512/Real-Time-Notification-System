# ---- Build stage: compile the Notion-styled React UI (frontend/) ----
FROM node:22-alpine AS build
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
# VITE_API_URL empty => same-origin /api (served through this nginx).
# VITE_WS_URL empty => same-origin /ws upgrade.
RUN npm run build

# ---- Runtime: nginx serves the SPA + reverse-proxies /api and /ws ----
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /web/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -q --spider http://localhost/health || exit 1
