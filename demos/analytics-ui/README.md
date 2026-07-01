# Analytics UI

React + TypeScript frontend for the analytics workbench.

## Local Development

Start the Java analytics API on a fixed port:

```bash
ANALYTICS_PORT=8080 ./gradlew :demos:run -PmainClass=dev.vaijanath.aiagent.demos.AnalyticsDashboardServer
```

Start the Vite app:

```bash
cd demos/analytics-ui
npm install
npm run dev
```

Open the Vite URL. API calls under `/api/*` proxy to `http://127.0.0.1:8080` by default.

## Build

```bash
npm run build
```

Set `VITE_ANALYTICS_API_TARGET` to proxy to a different backend during development.
