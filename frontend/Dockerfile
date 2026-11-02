FROM node:24-bookworm-slim AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build -- --configuration production

FROM caddy:2.10-alpine
RUN addgroup -g 10001 sigaa \
    && adduser -D -H -u 10001 -G sigaa sigaa \
    && chown -R sigaa:sigaa /data /config
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /build/www /srv
USER 10001:10001
EXPOSE 8080
