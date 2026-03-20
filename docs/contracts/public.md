# Public API Contract

Machine-readable contract: `openapi/openapi.yaml` and runtime `/v3/api-docs`.

## GET /api/public/ping

**Purpose**

Return a simple backend reachability signal.

**Authentication**

No authentication required.

**Request Body**

No request body.

**Response Body**

```json
{
  "status": "ok"
}
```

**Typical Success Status**

- `200 OK`

**Typical Error Statuses**

- None expected in normal application flow.

**curl**

```bash
curl http://localhost:8080/api/public/ping
```

**Notes**

- No session cookie required.
- No CSRF token required.
