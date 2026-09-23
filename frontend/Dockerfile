FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
# Higher retry tolerance and lower concurrency: this network drops long-lived/parallel
# connections mid-download (observed repeatedly with default settings).
RUN npm ci --fetch-retries=8 --fetch-retry-mintimeout=20000 --fetch-retry-maxtimeout=120000 --maxsockets=3
COPY . .
RUN npm run build -- --configuration production

FROM nginx:alpine
COPY --from=build /app/dist/frontend /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
