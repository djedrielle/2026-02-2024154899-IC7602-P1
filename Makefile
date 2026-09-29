# Automatización del proyecto.
#   make            -> despliegue OFICIAL en Kubernetes (configura, construye, despliega, prueba y abre la UI)
#   make dev-up     -> Docker Compose: alternativa para desarrollo y pruebas locales
# Kubernetes y Compose usan los mismos puertos del host: nunca corren a la vez (cada uno detiene al otro).
# Uso: make help
#

NAMESPACE    ?= dns
UI_API_URL   ?= http://localhost:8080
KIND_CLUSTER ?=
UI_URL       ?= http://localhost:30080
CHECKERS     ?= on
COMPOSE      ?= docker compose
SERVICES     ?=
KUBECTL      := kubectl -n $(NAMESPACE)

# Puertos del host con los que se prueba Docker Compose (los mismos que usa docker-compose.yml).
API_PORT     := $(if $(SERVER_PORT),$(SERVER_PORT),8080)
UI_PORT      := $(if $(DNS_UI_PORT),$(DNS_UI_PORT),3000)
DNS_PORT     := $(if $(DNS_INTERCEPTOR_PORT),$(DNS_INTERCEPTOR_PORT),15353)

.DEFAULT_GOAL := all
.PHONY: all help env creds env-sync dev-up dev-down dev-ps dev-logs dev-test dev-free-ports \
        k8s-check k8s-build k8s-secrets k8s-deploy k8s-up k8s-status k8s-test k8s-logs \
        k8s-checkers-on k8s-checkers-off k8s-down k8s-start k8s-open k8s-free-ports

all: k8s-start ## (por defecto, solo "make") Despliegue oficial en Kubernetes: todo automatizado y abre la UI

help: ## Muestra esta ayuda
	@echo "Despliegue oficial en Kubernetes  (make sin argumentos ejecuta: make k8s-start):"
	@awk 'BEGIN{FS=":.*## "} /^(all|k8s-[a-z-]+):.*## /{printf "  make %-18s %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@echo "Docker Compose  (solo desarrollo y pruebas locales; detiene Kubernetes al levantarse):"
	@awk 'BEGIN{FS=":.*## "} /^(env|dev-[a-z-]+):.*## /{printf "  make %-18s %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@echo "Variables: CHECKERS=off (desplegar sin Health Checkers) SERVICES=\"dns-api dns-ui\" (dev-up con algunos servicios) NAMESPACE=$(NAMESPACE) UI_API_URL=$(UI_API_URL) KIND_CLUSTER=$(KIND_CLUSTER)"
	@echo "Credenciales de Supabase: variables SUPABASE_DB_URL, SUPABASE_DB_USER, SUPABASE_DB_PASSWORD, o se piden por consola."

# ---------------------------------------------------------------- configuración (.env)

env: ## Crea los .env desde los .env.example y agrega las variables nuevas que les falten
	@for d in dns_api health_checker; do \
	  if [ ! -f $$d/.env ]; then cp $$d/.env.example $$d/.env && echo "Creado $$d/.env"; continue; fi; \
	  [ "$$(tail -c1 $$d/.env | wc -l)" -gt 0 ] || echo >> $$d/.env; \
	  grep -E '^[A-Z_][A-Z0-9_]*=' $$d/.env.example | while IFS= read -r line; do \
	    k=$${line%%=*}; \
	    grep -q "^$$k=" $$d/.env || { printf '%s\n' "$$line" >> $$d/.env; echo "$$d/.env: agregada la variable $$k"; }; \
	  done; \
	done

# Completa las credenciales de Supabase en dns_api/.env cuando faltan (o son los valores de ejemplo <...>).
# Orden: variables de entorno del shell, luego un prompt si hay terminal; si no, falla con instrucciones.
creds: env
	@set -e; \
	setkv() { t=$$(mktemp); grep -v "^$$1=" dns_api/.env > $$t || true; printf '%s=%s\n' "$$1" "$$2" >> $$t; cat $$t > dns_api/.env; rm -f $$t; }; \
	missing=""; \
	for k in SUPABASE_DB_URL SUPABASE_DB_USER SUPABASE_DB_PASSWORD; do \
	  cur=$$(grep -E "^$$k=" dns_api/.env | head -1 | cut -d= -f2-); \
	  case "$$cur" in ""|*"<"*) ;; *) continue;; esac; \
	  eval "val=\$${$$k:-}"; \
	  if [ -z "$$val" ] && [ -t 0 ]; then \
	    printf "Ingresa %s: " "$$k"; \
	    if [ "$$k" = SUPABASE_DB_PASSWORD ]; then stty -echo; read -r val || true; stty echo; echo; else read -r val || true; fi; \
	  fi; \
	  if [ -n "$$val" ]; then setkv "$$k" "$$val"; echo "dns_api/.env: $$k configurado"; else missing="$$missing $$k"; fi; \
	done; \
	if [ -n "$$missing" ]; then \
	  echo "Faltan credenciales de Supabase:$$missing"; \
	  echo "Defínelas al invocar make:  SUPABASE_DB_URL=... SUPABASE_DB_USER=... SUPABASE_DB_PASSWORD=... make"; \
	  echo "o escríbelas en dns_api/.env (ver dns_api/.env.example)."; \
	  exit 1; \
	fi

# El Health Checker usa una URI de libpq; se deriva de las mismas credenciales que usa el DNS API.
# Si la contraseña tiene caracteres especiales, escribe DATABASE_URL a mano en health_checker/.env.
env-sync: creds
	@set -e; \
	url=$$(grep -E '^DATABASE_URL=' health_checker/.env | head -1 | cut -d= -f2-); \
	if [ -z "$$url" ]; then \
	  jdbc=$$(grep -E '^SUPABASE_DB_URL=' dns_api/.env | head -1 | cut -d= -f2-); \
	  user=$$(grep -E '^SUPABASE_DB_USER=' dns_api/.env | head -1 | cut -d= -f2-); \
	  pass=$$(grep -E '^SUPABASE_DB_PASSWORD=' dns_api/.env | head -1 | cut -d= -f2-); \
	  tmp=$$(mktemp); \
	  grep -v '^DATABASE_URL=' health_checker/.env > $$tmp || true; \
	  printf 'DATABASE_URL=postgresql://%s:%s@%s\n' "$$user" "$$pass" "$${jdbc#jdbc:postgresql://}" >> $$tmp; \
	  cat $$tmp > health_checker/.env; rm -f $$tmp; \
	  echo "health_checker/.env: DATABASE_URL generado desde las credenciales de dns_api/.env"; \
	fi

# ---------------------------------------------------------------- Docker Compose (desarrollo)

# Compose y Kubernetes usan los mismos puertos del host: se elimina el namespace de Kubernetes si existe.
dev-free-ports:
	@if command -v kubectl >/dev/null 2>&1 && kubectl --request-timeout=5s get namespace $(NAMESPACE) >/dev/null 2>&1; then \
	  echo "Kubernetes está desplegado: se elimina el namespace $(NAMESPACE) para liberar los puertos del host"; \
	  kubectl delete -f k8s/namespace.yaml --ignore-not-found; \
	fi

dev-up: dev-free-ports env-sync ## Compose: configura, construye y levanta (SERVICES="dns-api dns-ui" para algunos)
	$(COMPOSE) up --build -d $(SERVICES)
	@$(COMPOSE) ps

dev-down: ## Compose: detiene y elimina los contenedores
	$(COMPOSE) down

dev-ps: ## Compose: estado de los contenedores
	$(COMPOSE) ps

dev-logs: ## Compose: logs (make dev-logs S=dns-api para un solo servicio)
	$(COMPOSE) logs --tail=100 $(S)

dev-test: ## Compose: espera al API y prueba API, UI e interceptor
	@has() { [ -z "$(SERVICES)" ] || echo " $(SERVICES) " | grep -q " $$1 "; }; \
	if has dns-api; then \
	  echo "Esperando al API en :$(API_PORT) ..."; ok=0; \
	  for i in $$(seq 1 60); do \
	    [ "$$(curl -s -o /dev/null -w '%{http_code}' http://localhost:$(API_PORT)/api/records)" = "200" ] && { ok=1; break; }; sleep 2; \
	  done; \
	  [ $$ok = 1 ] || { echo "El API no respondió en 120 s. Revisa: make dev-logs S=dns-api"; exit 1; }; \
	  curl -fsS -o /dev/null -w "API  http :$(API_PORT)  -> %{http_code}\n" http://localhost:$(API_PORT)/api/records; \
	fi; \
	if has dns-ui; then curl -fsS -o /dev/null -w "UI        :$(UI_PORT)  -> %{http_code}\n" http://localhost:$(UI_PORT)/; fi; \
	if has dns-interceptor; then \
	  nslookup -type=MX -port=$(DNS_PORT) -timeout=8 gmail.com 127.0.0.1 | grep -q "mail exchanger" \
	    && echo "Interceptor UDP :$(DNS_PORT) -> MX de gmail.com resuelto" \
	    || { echo "Interceptor UDP :$(DNS_PORT) -> SIN respuesta"; exit 1; }; \
	fi

# ---------------------------------------------------------------- Kubernetes

k8s-start: k8s-free-ports k8s-up k8s-test k8s-open ## TODO en Kubernetes: configura, construye, despliega, prueba y abre la UI

# Kubernetes es el despliegue oficial: si Compose (desarrollo) está corriendo, se detiene para liberar los puertos del host.
k8s-free-ports:
	@if [ -n "$$($(COMPOSE) ps -q 2>/dev/null)" ]; then \
	  echo "Docker Compose está corriendo: se detiene para liberar los puertos del host"; \
	  $(COMPOSE) down; \
	fi

k8s-open: ## Abre la UI en el navegador ($(UI_URL))
	@echo "UI: $(UI_URL)"
	@if command -v open >/dev/null 2>&1; then open "$(UI_URL)"; \
	elif command -v xdg-open >/dev/null 2>&1; then xdg-open "$(UI_URL)" >/dev/null 2>&1 & \
	else echo "Abre esta URL en tu navegador: $(UI_URL)"; fi

k8s-check:
	@for t in docker kubectl openssl; do \
	  command -v $$t >/dev/null || { echo "Falta '$$t' en el PATH"; exit 1; }; \
	done
	@kubectl cluster-info >/dev/null 2>&1 || { echo "No hay un cluster accesible con kubectl (¿activaste Kubernetes en Docker Desktop?)"; exit 1; }
	@echo "Cluster: $$(kubectl config current-context)  |  namespace: $(NAMESPACE)"

k8s-build: k8s-check ## Construye las 4 imágenes locales (tag :local)
	docker build -t dns-api:local ./dns_api
	docker build -t dns-ui:local --build-arg NEXT_PUBLIC_DNS_API_URL=$(UI_API_URL) ./dns-ui
	docker build -t dns-interceptor:local ./dns_interceptor
	docker build -t health-checker:local ./health_checker
	@if [ -n "$(KIND_CLUSTER)" ]; then \
	  kind load docker-image dns-api:local dns-ui:local dns-interceptor:local health-checker:local --name $(KIND_CLUSTER); \
	fi

k8s-secrets: k8s-check env-sync ## Crea/actualiza los Secrets desde los .env (no se versionan)
	@kubectl apply -f k8s/namespace.yaml >/dev/null
	@set -e; tmp=$$(mktemp); trap 'rm -f $$tmp' EXIT; \
	grep -E '^(SUPABASE_DB_URL|SUPABASE_DB_USER|SUPABASE_DB_PASSWORD)=' dns_api/.env > $$tmp; \
	ssl=$$($(KUBECTL) get secret dns-api-secret -o go-template='{{index .data "SSL_KEYSTORE_PASSWORD" | base64decode}}' 2>/dev/null || true); \
	[ -n "$$ssl" ] || ssl=$$(openssl rand -hex 16); \
	printf 'SSL_KEYSTORE_PASSWORD=%s\n' "$$ssl" >> $$tmp; \
	$(KUBECTL) create secret generic dns-api-secret --from-env-file=$$tmp --dry-run=client -o yaml | $(KUBECTL) apply -f - >/dev/null; \
	grep -E '^DATABASE_URL=' health_checker/.env > $$tmp; \
	$(KUBECTL) create secret generic health-checker-secret --from-env-file=$$tmp --dry-run=client -o yaml | $(KUBECTL) apply -f - >/dev/null; \
	echo "Secrets dns-api-secret y health-checker-secret listos"

# Los archivos se aplican uno por uno: k8s/*/secret.example.yaml son plantillas y no deben aplicarse.
# Los Health Checkers se despliegan encendidos (CHECKERS=on); con CHECKERS=off se dejan en 0 réplicas.
# Escriben en la base compartida: ver docu/k8s.md.
k8s-deploy: k8s-check ## Aplica los manifiestos y espera a que estén listos (CHECKERS=off omite los Health Checkers)
	kubectl apply -f k8s/namespace.yaml
	kubectl apply -f k8s/dns-api/configmap.yaml -f k8s/dns-api/deployment.yaml -f k8s/dns-api/service.yaml
	kubectl apply -f k8s/dns-interceptor/configmap.yaml -f k8s/dns-interceptor/deployment.yaml -f k8s/dns-interceptor/service.yaml
	kubectl apply -f k8s/dns-ui/deployment.yaml -f k8s/dns-ui/service.yaml
	kubectl apply -f k8s/health-checker/configmap.yaml
	$(KUBECTL) rollout restart deploy/dns-api deploy/dns-interceptor deploy/dns-ui
	@if [ "$(CHECKERS)" = "on" ]; then \
	  kubectl apply -f k8s/health-checker/deployment-cr.yaml -f k8s/health-checker/deployment-us.yaml; \
	  $(KUBECTL) rollout restart deploy/health-checker-cr deploy/health-checker-us; \
	else \
	  echo "CHECKERS=off: los Health Checkers quedan en 0 réplicas"; \
	  $(KUBECTL) scale deploy -l app=health-checker --replicas=0 >/dev/null 2>&1 || true; \
	fi
	$(KUBECTL) rollout status deploy/dns-api --timeout=240s
	$(KUBECTL) rollout status deploy/dns-interceptor --timeout=240s
	$(KUBECTL) rollout status deploy/dns-ui --timeout=240s
	@if [ "$(CHECKERS)" = "on" ]; then \
	  $(KUBECTL) rollout status deploy/health-checker-cr --timeout=240s; \
	  $(KUBECTL) rollout status deploy/health-checker-us --timeout=240s; \
	fi

k8s-up: k8s-build k8s-secrets k8s-deploy k8s-status ## Todo en uno: imágenes, secrets, despliegue y estado

k8s-status: ## Pods y Services del namespace
	@$(KUBECTL) get pods,svc

k8s-test: ## Espera al API y prueba API (HTTP/HTTPS), UI e interceptor
	@echo "Esperando al API en :8080 ..."; ok=0; \
	for i in $$(seq 1 60); do \
	  [ "$$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/records)" = "200" ] && { ok=1; break; }; sleep 2; \
	done; \
	[ $$ok = 1 ] || { echo "El API no respondió en 120 s. Revisa: make k8s-logs S=dns-api"; exit 1; }
	@curl -fsS -o /dev/null -w "API  http  :8080  -> %{http_code}\n" http://localhost:8080/api/records
	@curl -fsSk -o /dev/null -w "API  https :8443  -> %{http_code}\n" https://localhost:8443/api/records
	@curl -fsS -o /dev/null -w "UI          :30080 -> %{http_code}\n" http://localhost:30080/
	@nslookup -type=MX -port=30053 -timeout=8 gmail.com 127.0.0.1 | grep -q "mail exchanger" \
	  && echo "Interceptor UDP :30053 -> MX de gmail.com resuelto" \
	  || { echo "Interceptor UDP :30053 -> SIN respuesta"; exit 1; }
	@if [ "$(CHECKERS)" = "on" ]; then \
	  $(KUBECTL) get deploy -l app=health-checker --no-headers | awk '{print "Health Checker " $$1 " -> listos " $$2}'; \
	fi

k8s-logs: ## Logs de un servicio: make k8s-logs S=dns-api
	@[ -n "$(S)" ] || { echo "Indica el servicio: make k8s-logs S=dns-api"; exit 1; }
	$(KUBECTL) logs deploy/$(S) --tail=100

k8s-checkers-on: k8s-check ## Enciende los 2 Health Checkers sin redesplegar todo (escriben en Supabase)
	kubectl apply -f k8s/health-checker/deployment-cr.yaml -f k8s/health-checker/deployment-us.yaml
	$(KUBECTL) rollout status deploy/health-checker-cr --timeout=240s
	$(KUBECTL) rollout status deploy/health-checker-us --timeout=240s

k8s-checkers-off: k8s-check ## Apaga los Health Checkers (0 réplicas)
	$(KUBECTL) scale deploy -l app=health-checker --replicas=0

k8s-down: k8s-check ## Elimina TODO el namespace (pods, servicios, secrets)
	kubectl delete -f k8s/namespace.yaml --ignore-not-found
