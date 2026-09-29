#!/usr/bin/env bash
# Installation Docker + préparation d'une VM Oracle Cloud Always Free (Ubuntu ou Oracle Linux).
# Usage : bash infra/oracle-vm-setup.sh
set -euo pipefail

echo "==> Swap 4 Go (utile pour compiler Maven/Angular sur petite VM)"
if ! swapon --show | grep -q swapfile; then
  sudo fallocate -l 4G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

cat <<'EOF'

Terminé. Étapes suivantes :
  1. Se reconnecter (groupe docker) : exit puis ssh à nouveau
  2. Console Oracle : VCN > Security List > ajouter une règle Ingress TCP 80 (et 443) depuis 0.0.0.0/0
  3. cp env.prod.example .env && nano .env      (mot de passe DB, JWT_SECRET)
  4. docker compose -f docker-compose.prod.yml up -d --build
  5. Ouvrir http://<IP-publique-de-la-VM>
EOF

