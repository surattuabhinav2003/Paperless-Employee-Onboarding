# ---- Build stage ----
FROM node:20-alpine AS build
WORKDIR /app

# Vite inlines VITE_* values into the bundle at build time. They are not
# configuration the container can be restarted with - changing one means
# rebuilding the image.
ARG VITE_AZURE_CLIENT_ID
ARG VITE_AZURE_TENANT_ID
# Left unset deliberately. The app falls back to a relative /api, which nginx
# proxies to the backend on this same origin - so the built image does not have
# a hostname baked into it and works on whatever address the server answers on.
ARG VITE_API_BASE_URL
ENV VITE_AZURE_CLIENT_ID=$VITE_AZURE_CLIENT_ID \
    VITE_AZURE_TENANT_ID=$VITE_AZURE_TENANT_ID \
    VITE_API_BASE_URL=$VITE_API_BASE_URL

# npm ci, not npm install: it installs exactly what package-lock.json pins and
# fails if the two have drifted, which is the point of having the lock file.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---- Runtime stage ----
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
