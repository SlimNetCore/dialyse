# ============================================================
# Frontend Dockerfile - Angular 22 (build) + Nginx (runtime)
# Multi-arch : fonctionne sur x86_64 et ARM64 (Oracle Ampere)
# ============================================================

# ---- Stage 1 : Build Angular ----
FROM node:22-alpine AS builder

WORKDIR /app

# Cache des dépendances (.npmrc contient legacy-peer-deps=true)
COPY package.json package-lock.json .npmrc ./

# Le lockfile référence @sbourahla/ng-table via un lien local (../../ngTable/dist/ng-table)
# absent du contexte Docker : on retire ces entrées pour le résoudre depuis le registre npm
# (version publiée ^1.0.0). Les autres versions restent verrouillées par le lockfile.
RUN node -e "const fs=require('fs');const l=JSON.parse(fs.readFileSync('package-lock.json','utf8'));\
for(const k of Object.keys(l.packages||{})){if(k==='node_modules/@sbourahla/ng-table'||k.endsWith('/ngTable/dist/ng-table')||k==='../../ngTable/dist/ng-table'){delete l.packages[k];}}\
fs.writeFileSync('package-lock.json',JSON.stringify(l,null,2));" \
 && npm install --no-audit --no-fund

COPY . .

# Environnement de production "même origine" : le navigateur appelle /api, /ws et
# /actuator sur le domaine servi par Nginx, qui les proxifie vers le backend.
# (Remplace l'URL onrender.com codée en dur pour les builds Docker uniquement.)
RUN printf "export const environment = {\n  production: true,\n  apiBaseUrl: '/api/v1',\n  wsBaseUrl: null as string | null,\n  healthCheckUrl: '/actuator/health',\n};\n" \
    > src/environments/environment.prod.ts

# Node limité en RAM sur les petites VM : borne le heap
ENV NODE_OPTIONS=--max-old-space-size=2048
RUN npx ng build --configuration production

# ---- Stage 2 : Runtime Nginx ----
FROM nginx:1.27-alpine AS runtime

LABEL description="Hemodialyse Frontend (Angular + Nginx)"

RUN rm -f /etc/nginx/conf.d/default.conf
COPY nginx.docker.conf /etc/nginx/conf.d/default.conf

# Angular 17+ (application builder) produit dist/<projet>/browser
COPY --from=builder /app/dist/*/browser /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://127.0.0.1/health || exit 1



