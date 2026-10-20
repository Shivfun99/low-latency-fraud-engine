# Running the project

The maintained run instructions are in [run-without-docker.md](run-without-docker.md).

For the complete Docker stack:

```bash
docker compose up --build -d
```

For applications on your machine after building them and starting infrastructure:

```bash
bash scripts/run-local.sh
```

Open http://localhost:5173 and verify with `node scripts/smoke-test.mjs`.
