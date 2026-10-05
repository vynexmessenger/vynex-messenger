# Vynex Messenger — Self-Hosted Backend Server

This package contains the standalone backend server for **Vynex Messenger**, ready to be hosted directly on your PC (Windows, macOS, or Linux).

It provides:
1. **Media & File Storage**: Local image uploads and downloads for user profile photos and chat media attachments.
2. **Push Notifications**: Real-time Firestore listener that delivers high-priority FCM data messages directly to Android devices.

---

## 1. Prerequisites & Required Software

- **Node.js**: Version 18.x or 20.x LTS recommended (minimum v16+).
- **npm**: Version 8+ (comes bundled with Node.js).
- **Firebase Project Credentials**: A Firebase Service Account Key JSON file (if running the push notification background worker).

---

## 2. Directory Structure

```text
vynex-backend/
├── package.json         # Dependencies and scripts
├── .env.example         # Template for environment configuration
├── server.js            # Express API server & FCM worker
├── storage/             # Local file storage (auto-created on startup)
│   ├── profile_photos/  # Stored user profile avatars
│   └── chat_files/      # Stored chat media and attachments
└── README.md            # Setup documentation (this file)
```

---

## 3. Installation

1. Copy or move the `vynex-backend` folder to your PC.
2. Open a terminal (Command Prompt, PowerShell, or bash) in the `vynex-backend` directory:
   ```bash
   cd vynex-backend
   ```
3. Install dependencies:
   ```bash
   npm install
   ```

---

## 4. Configuration (.env)

1. Copy `.env.example` to `.env`:
   ```bash
   cp .env.example .env
   ```
   *(On Windows Command Prompt: `copy .env.example .env`)*

2. Open `.env` in any text editor and configure:
   ```env
   # Server Port
   PORT=8081

   # Base URL for public access (used to return image URLs)
   # When testing locally: http://localhost:8081
   # When testing with phone via ngrok/tunnel: https://xxxx.ngrok-free.app
   BASE_URL=http://localhost:8081

   # Storage path on your PC
   STORAGE_PATH=./storage

   # Path to Firebase Admin service account key JSON (optional if only hosting media)
   FIREBASE_SERVICE_ACCOUNT_PATH=./firebase-service-account.json
   ```

3. **Firebase Service Account (For Notifications)**:
   - Go to [Firebase Console](https://console.firebase.google.com/) -> Project Settings -> **Service accounts**.
   - Click **Generate new private key**.
   - Save the downloaded JSON file as `firebase-service-account.json` inside `vynex-backend/`.

---

## 5. Starting the Server

### Normal Start:
```bash
npm start
```

### Development Mode (auto-restart on changes):
```bash
npm run dev
```

### Default Port:
The server runs on **port 8081** by default (configurable via `PORT` in `.env`).

---

## 6. Available API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/health` | Server health check and status |
| `POST` | `/api/profile/upload` | Upload user profile photo (`photo` multipart field, `userId` field) |
| `GET` | `/api/profile/:userId` | Serve user profile photo |
| `DELETE` | `/api/profile/:userId` | Delete user profile photo |
| `POST` | `/api/chat/:chatId/image` | Upload chat media (`image` multipart field) |
| `GET` | `/api/files/:fileId` | Download / view chat media file |
| `DELETE` | `/api/files/:fileId` | Delete chat media file |

---

## 7. How to Test the API Locally

### 1. Test Health Endpoint:
Open your browser or run:
```bash
curl http://localhost:8081/health
```
Response:
```json
{
  "status": "OK",
  "service": "Vynex Messenger Self-Hosted Backend",
  "firebaseConnected": true
}
```

### 2. Test Profile Image Upload:
```bash
curl -X POST http://localhost:8081/api/profile/upload \
  -F "userId=testUser123" \
  -F "photo=@/path/to/sample.jpg"
```
Response:
```json
{
  "success": true,
  "userId": "testUser123",
  "url": "http://localhost:8081/api/profile/testUser123",
  "updatedAt": 1728000000000
}
```

### 3. Test Profile Image Download:
Open in browser or test with curl:
```bash
curl http://localhost:8081/api/profile/testUser123 --output downloaded.jpg
```

---

## 8. Making Your PC Server Accessible to Your Android Phone

To connect your phone to your PC backend over the internet:
1. Use a secure tunnel such as **ngrok** or **Cloudflare Tunnel**:
   ```bash
   ngrok http 8081
   ```
2. Set `BASE_URL` in `.env` to the public HTTPS URL generated (e.g. `https://xxxx.ngrok-free.app`).
3. In the next phase, the Android app can be configured to point to this base URL for media storage.
