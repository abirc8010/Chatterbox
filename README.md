# 💬 Chatterbox

Chatterbox is a full-stack, real-time messaging application built with React, Spring Boot, and PostgreSQL. It features live presence, rich Markdown rendering, and multimedia file sharing powered by Cloudinary.

*Note: For a deep dive into the system's data models, request flows, and detailed component responsibilities, see the [Architecture Document](ARCHITECTURE.md).*

---

##  Features

* **Real-Time Messaging:** Live chat, typing indicators, and presence updates via Spring STOMP WebSockets.
* **Rich Text & Media:** Support for multiline Markdown (with GFM), emojis, file/image attachments, and in-browser WebM voice recording.
* **Secure Authentication:** REST-based JWT authentication with 24-hour token lifecycles and BCrypt password hashing.
* **Media Management:** Direct-to-Cloudinary authenticated multipart uploads for chat media and profile avatars (includes client-side image cropping).
* **Smart Workspace:** Unread message tracking, user search by email, and active conversation state management.

---

##  Tech Stack

**Frontend**

* React 18 & TypeScript (via Vite)
* STOMP.js (WebSocket client)
* React Markdown & remark-gfm
* lucide-react & emoji-picker-react

**Backend**

* Java 21 & Spring Boot 3.3
* Spring MVC, Security, Data JPA, & WebSocket/STOMP
* JJWT (Java JWT)
* Cloudinary Java SDK

**Infrastructure**

* PostgreSQL (Primary Datastore, managed via Docker Compose locally)
* Cloudinary (Blob/Media Storage)
* GitHub Actions (CI/CD pipeline)

---


---

##  Getting Started

### Prerequisites

* Node.js 20+
* Java 21
* Docker & Docker Compose (for local database)
* A [Cloudinary](https://cloudinary.com/) account (for media storage)

### Local Setup

1. **Clone the repository:**
```bash
git clone https://github.com/your-username/chatterbox.git
cd chatterbox

```


2. **Configure Environment Variables:**
Copy the example environment file to the project root:
```bash
cp .env.example .env

```


Populate `.env` with your secure credentials:
```env
CLOUDINARY_CLOUD_NAME=your_cloud_name
CLOUDINARY_API_KEY=your_api_key
CLOUDINARY_API_SECRET=your_api_secret
CHATTERBOX_JWT_SECRET=your_secure_jwt_secret_key

```


* Never commit your `.env` file or expose your Cloudinary Secret in frontend `VITE_*` variables.*
3. **Run the Application:**
Start the local database, backend API, and Vite development server using the provided launch script:
```bash
./start.sh

```


Once started, open the local Vite URL (typically `http://localhost:5173`) in your browser.

---

##  Testing & CI

Continuous Integration is handled via GitHub Actions. On every push and pull request:

* **Frontend:** Runs `npm ci` and verifies production build viability (`npm run build`).
* **Backend:** Runs `mvn -B test`, executing unit tests (`AuthServiceTest`) and MockMvc integration tests backed by an isolated H2 database (`AuthApiIntegrationTest`).

---

