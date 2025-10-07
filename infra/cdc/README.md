# CDC & Analytics Pipeline

This directory contains the resources required to run the local Change Data Capture (CDC) stack used by the dashboard analytics module.

## Services

`docker-compose.dev.yml` now ships the following additional containers:

- **Zookeeper** + **Kafka** (`kafka:9092`, `localhost:29092`) – event backbone.
- **Kafka Connect (Debezium)** (`http://localhost:8083`) – streams Postgres changes to Kafka.
- **Flink Job/Task Managers** (`http://localhost:8081`) – runs the aggregation job that fills `analytics.*` tables.
- **Kafka UI** (`http://localhost:8085`) – inspect topics, consumer groups and registered connectors.

## Usage

1. Start the stack:
   ```bash
   docker compose -f docker-compose.dev.yml up -d
   ```
2. Register the Debezium source connector:
   ```bash
   ./scripts/register-connectors.sh
   ```
   (the script posts `infra/cdc/connectors/sip-interventions-source.json` to Kafka Connect).
3. Ensure the Postgres JDBC driver is available for Flink:
   ```bash
   ./scripts/download-flink-deps.sh
   ```
4. Submit the PyFlink job that keeps analytics tables in sync:
   ```bash
   ./scripts/submit-flink-job.sh
   ```

The job consumes the flattened Debezium topic `sip.interventions`, calculates daily KPI aggregates, technician load snapshots, and geospatial markers, then upserts them into the `analytics` schema. The REST endpoint `POST /api/dashboard/refresh` remains available to trigger a manual recompute if needed.

## Demo Data

For demos, you can load open datasets such as:

- **NYC 311 Service Requests** (NYC Open Data) – rich geo-temporal intervention-like records.
- **Chicago Building Permits** (Chicago Data Portal) – includes technician/company assignments and timestamps.
- **European emergency interventions** posted by cities on data.gouv.fr.

Convert columns to match the `interventions` schema (reference, title, planned/completed dates, technician, latitude/longitude) and insert them via the REST API or bulk SQL. The CDC pipeline will automatically propagate them to analytics tables and Kafka topics.

