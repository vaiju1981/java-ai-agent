FROM node:18-bookworm AS ui-build

WORKDIR /src/demos/analytics-ui
COPY demos/analytics-ui/package*.json ./
RUN npm ci
COPY demos/analytics-ui/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk AS app-build

WORKDIR /src
COPY . .
COPY --from=ui-build /src/demos/analytics-ui/dist demos/analytics-ui/dist
RUN ./gradlew :demos:installDist -PmainClass=dev.vaijanath.aiagent.demos.AnalyticsDashboardServer --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=app-build /src/demos/build/install/demos /app/demos
RUN mkdir -p /app/workspace

ENV ANALYTICS_HOST=0.0.0.0
ENV ANALYTICS_PORT=8080
ENV ANALYTICS_WORKSPACE=/app/workspace

EXPOSE 8080
CMD ["/app/demos/bin/demos"]
