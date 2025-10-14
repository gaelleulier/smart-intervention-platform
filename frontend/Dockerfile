# syntax=docker/dockerfile:1.6

FROM node:22-alpine AS build
WORKDIR /workspace

COPY package.json package-lock.json ./
RUN npm ci

COPY angular.json tsconfig*.json ./
COPY public public
COPY src src

RUN npm run build -- --configuration=production

FROM nginx:1.27-alpine

ENV NODE_ENV=production

COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist/frontend/browser /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
