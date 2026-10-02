# ---- Build stage: compile the React UI (frontend/) ----
# Node exists only in this stage. The runtime image is nginx and carries no Node.
FROM node:22-alpine AS build
WORKDIR /web

# Dependencies first so an application-only change reuses the cached npm layer.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY frontend/ ./

# Build-time configuration. Both default to empty, which makes the app same-origin:
# `/api` goes through this nginx and `/ws/notifications` is upgraded through it too.
# Override with `--build-arg VITE_API_URL=https://api.example.com` only when the API
# is deliberately hosted on another origin (and relax the CSP connect-src to match).
ARG VITE_API_URL=""
ARG VITE_WS_URL=""
ENV VITE_API_URL=$VITE_API_URL \
    VITE_WS_URL=$VITE_WS_URL

RUN npm run build

# ---- Runtime: nginx serves the SPA + reverse-proxies /api and /ws ----
FROM nginx:1.27-alpine
# Shared security headers. Kept as a separate file because nginx discards inherited
# `add_header` sets inside any location that declares its own — see the snippet header.
# docker-compose.prod.yml bind-mounts only default.conf, so this survives the override.
COPY nginx/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /web/dist /usr/share/nginx/html
EXPOSE 80

# `/health` is nginx's own liveness endpoint (served locally, never redirected), so
# this probe stays meaningful even when the backend is down.
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -q --spider http://localhost/health || exit 1

# Production overrides this with nginx.prod.conf via a read-only bind mount in
# docker-compose.prod.yml; the built assets in /usr/share/nginx/html are untouched.