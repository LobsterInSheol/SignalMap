# ============================================
# Stage 1: Build aplikacji React
# ============================================
FROM node:18-alpine AS builder


WORKDIR /app


COPY package.json package-lock.json ./


RUN npm cache clean --force && \
    npm install


COPY public/ ./public/
COPY src/ ./src/


ENV NODE_ENV=production
ENV GENERATE_SOURCEMAP=false
ENV CI=false


RUN NODE_OPTIONS="--max-old-space-size=4096" npm run build

# ============================================
# Stage 2: Nginx do serwowania aplikacji
# ============================================
FROM nginx:1.25-alpine

RUN rm -rf /etc/nginx/conf.d/*

COPY nginx.conf /etc/nginx/conf.d/default.conf

COPY --from=builder /app/build /usr/share/nginx/html


EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]