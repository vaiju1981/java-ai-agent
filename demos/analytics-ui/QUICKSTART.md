# Analytics Workbench Quickstart

## Docker

```bash
docker compose up --build
```

Open `http://127.0.0.1:8080`.

The app starts with an empty workspace. Upload a CSV from Data Sources or configure a connector plan. Runtime artifacts are written to `./workspace`.

Optional Ollama summaries use only `gemma4:31b-cloud`:

```bash
OLLAMA_BASE_URL=http://host.docker.internal:11434 docker compose up --build
```

## Local Packaged Run

Build the React UI with Node 18:

```bash
cd demos/analytics-ui
nvm use 18
npm ci
npm run build
```

Run the Java server:

```bash
cd ../..
ANALYTICS_PORT=8080 \
ANALYTICS_WORKSPACE=./workspace \
./gradlew :demos:run -PmainClass=dev.vaijanath.aiagent.demos.AnalyticsDashboardServer
```

Open `http://127.0.0.1:8080`.

## Optional Startup Dataset

The packaged app does not hard-code a CSV. To start with a specific local CSV, pass it explicitly:

```bash
ANALYTICS_PORT=8080 \
ANALYTICS_WORKSPACE=./workspace \
ANALYTICS_CSV=/absolute/path/to/data.csv \
./gradlew :demos:run -PmainClass=dev.vaijanath.aiagent.demos.AnalyticsDashboardServer
```
