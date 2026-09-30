FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY index.html vite.config.js ./
COPY public ./public
COPY src ./src
ARG VITE_AUTH_ENABLED=false
ARG VITE_KEYCLOAK_URL=http://127.0.0.1:8180
ENV VITE_AUTH_ENABLED=${VITE_AUTH_ENABLED} VITE_KEYCLOAK_URL=${VITE_KEYCLOAK_URL}
RUN npm run build

FROM nginx:1.29-alpine
COPY ops/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
