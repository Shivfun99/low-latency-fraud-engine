# Fraud Detection Project

## Problem

Fraud may go unnoticed when systems check transactions too slowly or one at a time. Rapid spending from an unfamiliar device or location can be a warning sign.

## What I built

A real-time fraud detection prototype using Kafka, Redis, Spring Boot, a C++ gRPC scorer, PostgreSQL, and a React dashboard.

## How it helps

It tracks recent activity, scores each transaction, saves suspicious cases, and streams alerts to the dashboard so an analyst can review them. The scorer uses hand-written rules and heuristics; it is not a trained machine-learning model.

## Run

```bash
docker compose up --build -d
```

Open http://localhost:5173. Or see [run-without-docker.md](run-without-docker.md) for the local setup.
