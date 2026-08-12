# Deploying traits

traits deploys to a Hetzner box as a single Docker container (SQLite is an
in-process file, so there's no separate DB service). Another service on the box
already owns `127.0.0.1:8080`, so traits publishes on **`127.0.0.1:8090`**, and
host nginx proxies a subdomain to it.

Everything lives in [`backend/deploy/`](../backend/deploy/): `Dockerfile`,
`entrypoint.sh`, `docker-compose.yml`, `.env.example`, and `deploy.sh`.

## Target

- **Host**: `service@178.104.177.218`
- **Compose dir**: `/home/service/compose/traits`
- **Internal**: container listens on `8080`, published to `127.0.0.1:8090`
- **Public URL**: `https://traits.ddns.net` (DDNS hostname → the box's IP)

Host and remote dir are hard-coded near the top of
[`backend/deploy/deploy.sh`](../backend/deploy/deploy.sh) — edit there if the box moves.

```
public internet
   │  HTTPS  (traits.ddns.net, Let's Encrypt cert via host nginx + certbot)
   ▼
host nginx ── proxy_pass http://127.0.0.1:8090 ──► traits-backend container
                                                    (tapir-netty-sync; /api, /docs, SPA)
                                                        └── traits-data volume (SQLite)
```

The SQLite store lives on the `traits-data` named volume. On the **first** boot
the container seeds it from the dataset baked into the image (the curated DB
snapshotted at deploy time); afterwards the volume persists, so edits made on
the live site survive redeploys.

## One-time setup

### 1. DNS

`traits.ddns.net` already points at `178.104.177.218` (keep the DDNS updater
running so it stays pointed — Let's Encrypt re-checks on renewal). Make sure
`:80`/`:443` are reachable (needed for the Let's Encrypt HTTP-01 challenge).

### 2. Push infra + build, from your laptop

```sh
./backend/deploy/deploy.sh --infra
```

This builds the frontend + fat jar, snapshots the local DB as the seed, uploads
everything, and runs `docker compose up -d --build`. **The first run will fail
at container start because `.env` doesn't exist yet — that's expected**, fix it
next.

### 3. Write `.env` on the server

```sh
ssh -J service@192.168.1.6 service@178.104.177.218
cd /home/service/compose/traits
cp /dev/stdin .env <<'EOF'
# paste backend/deploy/.env.example and fill in the blanks:
TRAITS_ENV=prod
TRAITS_SESSION_SECRET=<openssl rand -hex 32>
TRAITS_EDITOR_PASSWORD=<a real password — NOT let-me-in>
EOF
chmod 600 .env

sudo docker compose up -d
sudo docker compose logs -f traits-backend     # watch startup; expect "seeding …" then the server line
```

`.env` is **never** overwritten by `deploy.sh`. Reads are public; the editor
password is all that gates create/edit/delete — share it only with reviewers.

### 4. Point nginx at traits + issue the cert

```sh
sudo tee /etc/nginx/sites-available/traits.ddns.net > /dev/null <<'EOF'
# Rate-limit the login endpoint (10 req/min per IP).
limit_req_zone $binary_remote_addr zone=traits_auth:10m rate=10r/m;

server {
    listen 80;
    listen [::]:80;
    server_name traits.ddns.net;

    location = /api/auth/login {
        limit_req zone=traits_auth burst=10 nodelay;
        limit_req_status 429;
        proxy_pass http://127.0.0.1:8090;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        proxy_pass http://127.0.0.1:8090;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
    }
}
EOF

sudo ln -s /etc/nginx/sites-available/traits.ddns.net \
           /etc/nginx/sites-enabled/traits.ddns.net
sudo nginx -t && sudo systemctl reload nginx
curl -s http://traits.ddns.net/api/health       # → {"status":"ok","entryCount":0}

sudo certbot --nginx -d traits.ddns.net          # pick "2: Redirect"
curl -s https://traits.ddns.net/api/health       # → {"status":"ok",...} over TLS
```

Open `https://traits.ddns.net` — the boards, entry pages, version registry and
the `/docs` API browser are all served by the one container.

## Day-to-day deploys

```sh
./backend/deploy/deploy.sh          # rebuild jar + frontend + seed, restart container
./backend/deploy/deploy.sh --infra  # also re-push Dockerfile / compose / entrypoint
```

Each run installs frontend deps with `npm ci`, so a fresh checkout deploys
without any manual setup.

Each run also re-snapshots your **local** DB as the seed, but the seed only
initialises an *empty* volume — once the live site has data, redeploys keep it.
On a fresh checkout there is no local DB (`traits-data/` is gitignored); the
script then keeps the seed already on the server instead of failing.

## Pulling the live dataset down

To work locally against production data, copy the SQLite file out of the
`traits-data` volume — **`traits.sqlite` plus its `-wal`**:

```sh
mkdir -p traits-data
ssh -J service@192.168.1.6 service@178.104.177.218 \
  "sudo docker exec traits-backend tar -C /app/data -cf - traits.sqlite traits.sqlite-wal" \
  | tar -C traits-data -xf -

# fold the WAL into the main file
python3 -c "import sqlite3; c=sqlite3.connect('traits-data/traits.sqlite'); c.execute('PRAGMA wal_checkpoint(TRUNCATE)'); c.close()"
```

The `-wal` is not optional. The DB runs in WAL mode and the live
`traits.sqlite` may not have been checkpointed for months, so copying it alone
can hand you a long-stale dataset. Skip `-shm`; SQLite rebuilds it.

The copy is only non-atomic if a write lands mid-`tar`. There is no `sqlite3` or
`python3` on the server or in the JRE image to take a proper `.backup()`
snapshot, so for a guaranteed-consistent copy stop the container first — a clean
shutdown checkpoints the WAL:

```sh
ssh -J service@192.168.1.6 service@178.104.177.218 'cd /home/service/compose/traits && sudo docker compose stop traits-backend'
# ... tar copy as above ...
ssh -J service@192.168.1.6 service@178.104.177.218 'cd /home/service/compose/traits && sudo docker compose start traits-backend'
```

Note that once a local DB exists, the next deploy snapshots it as the seed
again. That is harmless while the live volume has data — but see the next
section before dropping the volume.

## Pushing a fresh dataset (wiping live data)

If you want the live DB replaced with your current local one, drop the volume so
the next deploy re-seeds:

```sh
ssh -J service@192.168.1.6 service@178.104.177.218 'cd /home/service/compose/traits && sudo docker compose down -v'
./backend/deploy/deploy.sh
```

## Logs & verifying

```sh
ssh -J service@192.168.1.6 service@178.104.177.218 'cd /home/service/compose/traits && sudo docker compose logs -f'
curl https://traits.ddns.net/api/health     # entry count doubles as a readiness probe
```

json-file logging is capped at 10 MB × 5 files.
