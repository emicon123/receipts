# syntax=docker/dockerfile:1
#
# Nginx image for the receipts app — builds the React PWA, then serves it as static
# files while reverse-proxying /api/* to the backend.
# Build context is the repository root (see infra/compose.yml: nginx.build.context: ..),
# so every COPY below is relative to the repo root, not this file's directory.
#
# Mirrors investing-app's infra/nginx/Dockerfile pattern. No build-time ARGs are needed
# here (unlike investing-app's VITE_GOOGLE_CLIENT_ID/VITE_API_BASE_URL) — this app's PWA
# calls the API via relative /api/... paths on the same origin nginx serves, since there
# is no OAuth and no second client to point at a different backend.

# ── Stage 1: Build the React PWA ────────────────────────────────────────────────
FROM node:22.23.2-alpine AS builder
WORKDIR /app

# Copy manifests first so npm install is cached until they change.
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm install

COPY frontend/ ./
RUN npm run build

# ── Stage 2: Serve with Nginx ────────────────────────────────────────────────────
# Fully non-root (unlike investing-app's nginx image, which only drops its *worker*
# processes to the built-in `nginx` user while the master still starts as root to bind
# port 80): listens on unprivileged port 8080 instead, so the master process itself can
# run as the built-in `nginx` user (uid 101) from the start — no root at any point in
# this container's lifetime. Requires handing that user write access to the handful of
# dirs nginx writes to (cache, logs, pid file) and dropping the now-irrelevant `user
# nginx;` directive (harmless if left in, but it logs a startup warning since a
# non-root master can't setuid to begin with).
FROM nginx:1.31.4-alpine

RUN sed -i '/^user  *nginx;/d' /etc/nginx/nginx.conf \
    && sed -i 's#pid\s*/run/nginx.pid;#pid /tmp/nginx.pid;#' /etc/nginx/nginx.conf \
    && rm -rf /var/cache/nginx/* \
    && chown -R 101:101 /var/cache/nginx /var/log/nginx /etc/nginx

COPY --from=builder --chown=101:101 /app/dist /usr/share/nginx/html
COPY --chown=101:101 infra/nginx/nginx.conf /etc/nginx/conf.d/default.conf

USER 101:101
EXPOSE 8080
