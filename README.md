# Chatterbox

Chatterbox is a real-time messaging workspace built around a clear split between persistent state, ephemeral events, and media transfer.

## Stack

- **Client:** React, TypeScript, Vite, STOMP.js, MediaRecorder API
- **API:** Java 21, Spring Boot 3.3, Spring WebSocket/STOMP, Spring Security boundary, Spring Data JPA
- **Data:** PostgreSQL in production, H2 for tests
- **Media:** Cloudinary adapter boundary, ready for image and WebM voice uploads

## Run locally

```bash
cd /home/abir/Documents/Projects/Chatterbox
docker compose up -d postgres
./start.sh
```

Open `http://localhost:5173/`. The launcher starts PostgreSQL, the Spring Boot API, and the Vite frontend. Contacts, messages, profile avatars, and unread counts are stored in PostgreSQL and restored after a restart.

```bash
cd backend
mvn spring-boot:run
```

Use the separate backend command only when you want to run the services manually.

Set `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, and `PORT` when connecting the API to a hosted PostgreSQL database. The defaults target a local PostgreSQL instance.

## Routes

- `GET /api/v1/health`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `GET /api/v1/users/search?email=...`
- `POST /api/v1/conversations`
- `PATCH /api/v1/messages/{id}`
- `DELETE /api/v1/messages/{id}`
- STOMP endpoint: `/ws-chat`
- STOMP sends: `/app/chat.send`, `/app/chat.typing`
- STOMP subscriptions: `/user/queue/messages`, `/user/queue/events`, `/topic/typing`
- Live subscriptions: `/topic/messages`, `/topic/typing`, `/topic/presence`

JWTs are issued on login/register and required for user search and future conversation routes. Set `CHATTERBOX_JWT_SECRET` to a long random value outside local development. Cloudinary remains behind a replaceable service boundary so the API can run without media credentials.

## Deployment shape

Build the frontend on Vercel or Netlify. Build the backend Docker image for Render. Use Neon or Supabase for PostgreSQL and Cloudinary for media. Add secrets through each provider's environment settings rather than committing them.
