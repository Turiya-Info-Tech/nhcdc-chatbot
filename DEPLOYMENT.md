# Deploying NHCDC Chatbot to a Hostinger VPS (beginner guide)

What you will end up with:

```
WhatsApp / Turiya  --HTTPS-->  Caddy (auto SSL)  -->  Spring Boot app  -->  your EXISTING Postgres
                                                           |
                                                           +--> Redis (runs in Docker with the app)
```

Everything except Postgres runs in Docker, started by `docker-compose.vps.yml`.
Commands marked **[PC]** run on your Windows computer, **[VPS]** on the server.

---

## Step 0 - Before you start: checklist

You need:
- [ ] VPS login (IP address, root password or SSH key) from Hostinger hPanel -> VPS -> Overview
- [ ] A domain or sub-domain for the bot, e.g. `bot.yourdomain.com` (HTTPS needs a domain; WhatsApp requires HTTPS)
- [ ] Your WhatsApp provider details (Turiya API key + WABA number, or Meta token + phone number id)
- [ ] The Google Sheets credentials file `secrets/google-sheets-credentials.json` and the spreadsheet id
- [ ] Postgres admin access on the VPS (so you can create a database)

### IMPORTANT - rotate secrets first
`src/main/resources/application.yaml` contains real-looking default values (a Meta access token,
a DB password, the Turiya key, the spreadsheet id). Anyone who sees that file sees those secrets.
- Before pushing code to GitHub, replace those defaults with empty ones (e.g. `${WHATSAPP_ACCESS_TOKEN:}`).
- Generate a new Meta access token / Turiya key if the code was ever shared or pushed anywhere.
- On the server, real values go only in the `.env` file (Step 6), never in code.

---

## Step 1 - Point your domain to the VPS

1. In your domain provider's DNS settings (Hostinger -> Domains -> DNS / Nameservers), add an **A record**:
   - Name: `bot` (for `bot.yourdomain.com`)
   - Points to: your VPS IP address
2. Wait 5-30 minutes. Check from your PC (PowerShell): `nslookup bot.yourdomain.com`
   It must show your VPS IP. Do not continue until it does - Caddy cannot get an SSL certificate otherwise.

## Step 2 - Log in to the VPS

**[PC]** open PowerShell:
```
ssh root@YOUR_VPS_IP
```
Type `yes` the first time, then the password. (Hostinger also has a "Browser terminal" button in hPanel.)

## Step 3 - Open the firewall ports

Caddy needs ports 80 and 443. In hPanel -> VPS -> Security -> Firewall, allow 22, 80, 443.
If the VPS uses `ufw`, **[VPS]**:
```
ufw allow 22
ufw allow 80
ufw allow 443
ufw status
```
(Do not run `ufw enable` before allowing 22, or you lock yourself out.)

Check nothing else already uses 80/443 (e.g. nginx/apache):
```
ss -tlnp | grep -E ':80 |:443 '
```
Empty output = good. If something is listed, tell me what - Caddy would need a different setup.

## Step 4 - Install Docker (skip if `docker --version` already works)

**[VPS]**
```
curl -fsSL https://get.docker.com | sh
docker --version
docker compose version
```
Both commands should print a version.

## Step 5 - Prepare the database in your existing Postgres

Do NOT use your existing databases. Create a new, separate database and user for the bot, so
it cannot touch your other data. The app creates all its tables itself on first start
(Flyway runs `V1__init_schema.sql`, which also adds centres, services and bot messages).

First find out how your Postgres runs - **[VPS]**:
```
ss -tlnp | grep 5432
docker ps
```
- Postgres installed directly on the server -> follow **5A**
- Postgres is a Docker container (you see it in `docker ps`) -> follow **5B**

### 5A - Postgres installed directly on the VPS

1. Create the database and user (choose your own strong password):
```
sudo -u postgres psql
```
Then inside `psql`:
```sql
CREATE USER nhcdc WITH PASSWORD 'PUT-A-LONG-RANDOM-PASSWORD';
CREATE DATABASE nhcdc_chatbot OWNER nhcdc;
\q
```
2. The app runs inside Docker, so Postgres must accept connections from Docker's network.
   Find the config files: `sudo -u postgres psql -c "SHOW config_file;" -c "SHOW hba_file;"`
3. In `postgresql.conf`, set (keep localhost so your other apps still work):
```
listen_addresses = 'localhost,172.17.0.1'
```
   (`172.17.0.1` is Docker's default gateway address. Confirm with `ip addr show docker0`.)
4. In `pg_hba.conf`, add one line at the end:
```
host    nhcdc_chatbot    nhcdc    172.16.0.0/12    scram-sha-256
```
   This only lets the `nhcdc` user reach the `nhcdc_chatbot` database from Docker networks.
5. Allow it through ufw: `ufw allow from 172.16.0.0/12 to any port 5432`
6. Restart: `systemctl restart postgresql`
7. Leave `DB_HOST` unset in `.env` (the compose file defaults to `host.docker.internal`).

   Do NOT open port 5432 to the internet.

### 5B - Postgres runs in a Docker container

1. Create database and user (replace `YOUR_PG_CONTAINER` with the name from `docker ps`):
```
docker exec -it YOUR_PG_CONTAINER psql -U postgres
```
```sql
CREATE USER nhcdc WITH PASSWORD 'PUT-A-LONG-RANDOM-PASSWORD';
CREATE DATABASE nhcdc_chatbot OWNER nhcdc;
\q
```
2. Join the bot to that container's Docker network. Find the network name:
```
docker inspect YOUR_PG_CONTAINER --format '{{json .NetworkSettings.Networks}}'
```
   Then in `docker-compose.vps.yml`, under the `app:` service add:
```yaml
    networks: [default, pgnet]
```
   and at the very bottom of the file add:
```yaml
networks:
  pgnet:
    external: true
    name: THE_NETWORK_NAME_YOU_FOUND
```
3. In `.env` set `DB_HOST=YOUR_PG_CONTAINER` (the container name) and `DB_PORT=5432`.
   (You can then remove the `extra_hosts` lines from the app service.)

## Step 6 - Get the code onto the VPS

**Option 1 - GitHub (recommended, easy to update later).** Push the project to a *private* GitHub repo
(`.env`, `secrets/`, `logs/` and `target/` are already in `.gitignore`). Then **[VPS]**:
```
cd /opt
git clone https://github.com/YOUR_USER/nhcdc-chatbot.git
cd nhcdc-chatbot
```
(For a private repo use a GitHub personal access token as the password.)

**Option 2 - copy from your PC.** **[PC]** in the project folder (PowerShell):
```
scp -r src pom.xml Dockerfile Caddyfile docker-compose.vps.yml .env.example .dockerignore root@YOUR_VPS_IP:/opt/nhcdc-chatbot/
```
(Run `ssh root@YOUR_VPS_IP "mkdir -p /opt/nhcdc-chatbot"` first.)

## Step 7 - Upload the Google credentials

**[PC]**
```
ssh root@YOUR_VPS_IP "mkdir -p /opt/nhcdc-chatbot/secrets"
scp secrets\google-sheets-credentials.json root@YOUR_VPS_IP:/opt/nhcdc-chatbot/secrets/
```
Make sure the Google Sheet is shared (Editor) with the service-account email found inside that JSON file.

## Step 8 - Create the `.env` file

**[VPS]**
```
cd /opt/nhcdc-chatbot
cp .env.example .env
nano .env
```
Fill in every value (nano: edit, then `Ctrl+O`, `Enter`, `Ctrl+X` to save):

| Key | What to put |
|---|---|
| `DOMAIN` | `bot.yourdomain.com` |
| `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | exactly what you created in Step 5 |
| `DB_HOST` | only for 5B (container name); otherwise leave commented |
| `WHATSAPP_PROVIDER` | `turiya` or `meta` |
| `TURIYA_API_KEY`, `TURIYA_WABA_NUMBER` | if provider is `turiya` |
| `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_ACCESS_TOKEN` | if provider is `meta` |
| `WHATSAPP_WEBHOOK_VERIFY_TOKEN` | any long random text; you will paste the same into Meta |
| `ADMIN_API_USERNAME` / `ADMIN_API_PASSWORD` | strong values (these protect `/api/**`) |
| `GOOGLE_SHEETS_SPREADSHEET_ID` | the id from the sheet URL (`/d/<this part>/edit`) |

Lock the file down: `chmod 600 .env`

## Step 9 - Build and start

**[VPS]**
```
cd /opt/nhcdc-chatbot
docker compose -f docker-compose.vps.yml up -d --build
```
The first build takes 3-8 minutes (it downloads Maven dependencies). Then:
```
docker compose -f docker-compose.vps.yml ps
docker compose -f docker-compose.vps.yml logs -f app
```
Healthy start = you see `Started NhcdcChatbotApplication` and Flyway lines like
`Successfully applied 1 migration`. Press `Ctrl+C` to stop watching (the app keeps running).

Common errors:
- `Connection refused` / `timeout` to the database -> Step 5 networking (5A: `listen_addresses`, `pg_hba.conf`, ufw).
- `password authentication failed` -> `.env` DB values don't match the user you created.
- `Missing required configuration: ...` -> a value in `.env` is empty.
- `Flyway ... checksum mismatch` -> the database already has an old schema; use a brand-new, empty database.

## Step 10 - Test

1. HTTPS works (Caddy fetches the certificate automatically, may take a minute): open
   `https://bot.yourdomain.com/api/centres` in a browser. It should ask for a username/password -
   enter your `ADMIN_API_*` values and you should see the centres JSON.
2. Webhook check (use your verify token):
   `https://bot.yourdomain.com/webhook/whatsapp?hub.mode=subscribe&hub.verify_token=YOUR_TOKEN&hub.challenge=123`
   should print `123`.
3. Register the webhook:
   - **Meta provider:** Meta developer dashboard -> WhatsApp -> Configuration -> Webhook. Callback URL
     `https://bot.yourdomain.com/webhook/whatsapp`, Verify token = your `WHATSAPP_WEBHOOK_VERIFY_TOKEN`,
     then subscribe to the `messages` field.
   - **Turiya provider:** give Turiya the same callback URL.
4. Send "hi" to the WhatsApp number from your phone. You should get the welcome message and menu.
5. Complete a test booking and check the lead appears in `https://bot.yourdomain.com/api/leads` and in the Google Sheet.

## Step 11 - Daily operations

| Task | Command (in `/opt/nhcdc-chatbot`) |
|---|---|
| See logs | `docker compose -f docker-compose.vps.yml logs -f app` |
| Restart | `docker compose -f docker-compose.vps.yml restart app` |
| Stop all | `docker compose -f docker-compose.vps.yml down` |
| Deploy an update | `git pull` then `docker compose -f docker-compose.vps.yml up -d --build` |
| Change a bot text/service | edit the row in the `bot_settings` / `service_offerings` tables (cached up to 10 minutes) |

The containers have `restart: unless-stopped`, so they come back after a VPS reboot.

## Step 12 - Backups (do this, it is your leads data)

Daily database backup, **[VPS]** (5A example; for 5B use `docker exec YOUR_PG_CONTAINER pg_dump -U nhcdc nhcdc_chatbot`):
```
mkdir -p /opt/backups
crontab -e
```
Add this line (runs 2 AM daily, keeps files you can prune later):
```
0 2 * * * PGPASSWORD='YOUR_DB_PASSWORD' pg_dump -h localhost -U nhcdc nhcdc_chatbot | gzip > /opt/backups/nhcdc_$(date +\%F).sql.gz
```
Also enable Hostinger's weekly VPS snapshots in hPanel.

## Quick troubleshooting

| Symptom | Check |
|---|---|
| Site not loading / no SSL | DNS A record (Step 1), ports 80/443 (Step 3), `docker compose ... logs caddy` |
| WhatsApp messages get no reply | `logs app` while sending; webhook URL + verify token; provider keys in `.env` |
| Leads not in Google Sheet | sheet shared with the service-account email; `GOOGLE_SHEETS_SPREADSHEET_ID`; sheet tab named `Leads` |
| After editing `.env` nothing changes | `docker compose -f docker-compose.vps.yml up -d` (recreates containers) |
