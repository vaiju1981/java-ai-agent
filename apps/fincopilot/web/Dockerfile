# syntax=docker/dockerfile:1
# Build the FinCopilot SPA, then serve the static bundle via nginx (which proxies /api to the backend).

FROM node:20-alpine AS build
WORKDIR /web
COPY package.json package-lock.json* ./
RUN npm install
COPY . .
RUN npm run build

# nginx-unprivileged runs as a non-root user (nginx, uid 101) and listens on 8080.
FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /web/dist /usr/share/nginx/html
USER nginx
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -q --spider http://localhost:8080/ || exit 1
