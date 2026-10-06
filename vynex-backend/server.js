const path = require("path");
const fs = require("fs");
require("dotenv").config({ path: path.join(__dirname, ".env"), override: true });
const express = require("express");
const cors = require("cors");
const multer = require("multer");
const admin = require("firebase-admin");

const app = express();
const PORT = process.env.PORT || 8081;
const BASE_URL = process.env.BASE_URL || `http://localhost:${PORT}`;
const rawStoragePath = process.env.STORAGE_PATH || "./storage";
const STORAGE_PATH = path.isAbsolute(rawStoragePath)
  ? rawStoragePath
  : path.resolve(__dirname, rawStoragePath);

// Ensure storage directories exist
const profileDir = path.join(STORAGE_PATH, "profile_photos");
const chatFilesDir = path.join(STORAGE_PATH, "chat_files");
fs.mkdirSync(profileDir, { recursive: true });
fs.mkdirSync(chatFilesDir, { recursive: true });

app.use(cors());
app.use(express.json());

// ----------------------------------------------------
// 1. Firebase Admin Initialization (for FCM Notifications)
// ----------------------------------------------------
let firebaseInitialized = false;
let hasValidCredentials = false;
const rawServiceAccountPath = process.env.FIREBASE_SERVICE_ACCOUNT_PATH;
const serviceAccountPath = rawServiceAccountPath
  ? (path.isAbsolute(rawServiceAccountPath) ? rawServiceAccountPath : path.resolve(__dirname, rawServiceAccountPath))
  : null;
const projectId = process.env.FIREBASE_PROJECT_ID || "vynex-mess-app";

try {
  if (serviceAccountPath && fs.existsSync(serviceAccountPath)) {
    const serviceAccount = JSON.parse(fs.readFileSync(serviceAccountPath, "utf8"));
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount),
      projectId: serviceAccount.project_id || projectId
    });
    firebaseInitialized = true;
    hasValidCredentials = true;
    console.log(`[FIREBASE] Initialized with service account file for project: ${serviceAccount.project_id || projectId}`);
  } else if (process.env.GOOGLE_APPLICATION_CREDENTIALS) {
    admin.initializeApp({
      credential: admin.credential.applicationDefault(),
      projectId: projectId
    });
    firebaseInitialized = true;
    hasValidCredentials = true;
    console.log(`[FIREBASE] Initialized with application default credentials for project: ${projectId}`);
  } else if (projectId) {
    admin.initializeApp({
      projectId: projectId
    });
    firebaseInitialized = true;
    console.log(`[FIREBASE] Initialized with Project ID: ${projectId}`);
    console.log("[FIREBASE_NOTICE] Place firebase-service-account.json in vynex-backend/ to enable Firestore listener and FCM push notifications.");
  } else {
    admin.initializeApp();
    firebaseInitialized = true;
    console.log("[FIREBASE] Initialized with ambient project credentials.");
  }
} catch (e) {
  console.warn("[FIREBASE_WARN] Could not initialize Firebase Admin SDK:", e.message);
  console.warn("[FIREBASE_WARN] Push notification worker will remain idle until service account is configured in .env");
}

// ----------------------------------------------------
// 2. Health & Status Endpoints
// ----------------------------------------------------
app.get("/health", (req, res) => {
  res.status(200).json({
    status: "OK",
    service: "Vynex Messenger Self-Hosted Backend",
    timestamp: new Date().toISOString(),
    firebaseConnected: firebaseInitialized
  });
});

// ----------------------------------------------------
// 3. Media & File Storage Endpoints
// ----------------------------------------------------

// Multer storage for profile photos (named by userId)
const profileStorage = multer.diskStorage({
  destination: (req, file, cb) => cb(null, profileDir),
  filename: (req, file, cb) => {
    const userId = req.body.userId || req.query.userId || req.params.userId || "unknown";
    cb(null, `${userId}.jpg`);
  }
});
const uploadProfile = multer({
  storage: profileStorage,
  limits: { fileSize: 10 * 1024 * 1024 } // 10MB limit
});

// Upload profile photo
// POST /api/profile/upload (multipart form with 'photo' or 'image' and 'userId')
app.post("/api/profile/upload", uploadProfile.single("photo"), (req, res) => {
  try {
    const userId = req.body.userId || req.query.userId;
    if (!userId) {
      return res.status(400).json({ error: "Missing required field: userId" });
    }
    const publicUrl = `${BASE_URL}/api/profile/${userId}`;
    res.status(200).json({
      success: true,
      userId,
      url: publicUrl,
      updatedAt: Date.now()
    });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// Serve profile photo
// GET /api/profile/:userId
app.get("/api/profile/:userId", (req, res) => {
  const userId = req.params.userId;
  const filePath = path.join(profileDir, `${userId}.jpg`);
  if (fs.existsSync(filePath)) {
    res.setHeader("Content-Type", "image/jpeg");
    res.setHeader("Cache-Control", "public, max-age=86400");
    fs.createReadStream(filePath).pipe(res);
  } else {
    res.status(404).json({ error: "Profile photo not found" });
  }
});

// Delete profile photo
// DELETE /api/profile/:userId
app.delete("/api/profile/:userId", (req, res) => {
  const userId = req.params.userId;
  const filePath = path.join(profileDir, `${userId}.jpg`);
  if (fs.existsSync(filePath)) {
    fs.unlinkSync(filePath);
    res.status(200).json({ success: true, message: "Profile photo deleted" });
  } else {
    res.status(404).json({ error: "Profile photo not found" });
  }
});

// Multer storage for chat images / files
const chatStorage = multer.diskStorage({
  destination: (req, file, cb) => cb(null, chatFilesDir),
  filename: (req, file, cb) => {
    const uniqueSuffix = Date.now() + "-" + Math.round(Math.random() * 1e9);
    const ext = path.extname(file.originalname) || ".jpg";
    cb(null, `chat-${uniqueSuffix}${ext}`);
  }
});
const uploadChatFile = multer({
  storage: chatStorage,
  limits: { fileSize: 25 * 1024 * 1024 } // 25MB limit
});

// Upload chat image
// POST /api/chat/:chatId/image
app.post("/api/chat/:chatId/image", uploadChatFile.single("image"), (req, res) => {
  try {
    const chatId = req.params.chatId;
    if (!req.file) {
      return res.status(400).json({ error: "No image file provided" });
    }
    const fileId = req.file.filename;
    const fileUrl = `${BASE_URL}/api/files/${fileId}`;
    res.status(200).json({
      success: true,
      chatId,
      fileId,
      url: fileUrl,
      size: req.file.size
    });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// Serve chat file
// GET /api/files/:fileId
app.get("/api/files/:fileId", (req, res) => {
  const fileId = req.params.fileId;
  const filePath = path.join(chatFilesDir, fileId);
  if (fs.existsSync(filePath)) {
    res.sendFile(filePath);
  } else {
    res.status(404).json({ error: "File not found" });
  }
});

// Delete chat file
// DELETE /api/files/:fileId
app.delete("/api/files/:fileId", (req, res) => {
  const fileId = req.params.fileId;
  const filePath = path.join(chatFilesDir, fileId);
  if (fs.existsSync(filePath)) {
    fs.unlinkSync(filePath);
    res.status(200).json({ success: true, message: "File deleted" });
  } else {
    res.status(404).json({ error: "File not found" });
  }
});

// ----------------------------------------------------
// 4. Notification Background Worker (FCM)
// ----------------------------------------------------
const processedMessages = new Set();
const START_TIME = Date.now();

function startNotificationListener() {
  if (!firebaseInitialized) {
    console.log("[LISTENER_SKIP] Firebase not initialized. Notification listener skipped.");
    return;
  }
  if (!hasValidCredentials) {
    console.log("[LISTENER_NOTICE] Notification listener paused: firebase-service-account.json is required for Firestore collection listener.");
    console.log("[LISTENER_NOTICE] Media upload and storage APIs (/api/profile, /api/chat) are fully active.");
    return;
  }

  console.log("[LISTENER] Starting Firestore listener on collectionGroup('messages')...");
  const db = admin.firestore();

  db.collectionGroup("messages").onSnapshot(
    (snapshot) => {
      snapshot.docChanges().forEach((change) => {
        if (change.type === "added") {
          const message = change.doc.data();
          const messageTime = message.timestamp?.toMillis
            ? message.timestamp.toMillis()
            : message.timestamp?._seconds
            ? message.timestamp._seconds * 1000
            : Number(message.timestamp);

          if (messageTime && messageTime >= START_TIME) {
            handleNewMessage(message);
          }
        }
      });
    },
    (error) => {
      console.error("[LISTENER_ERROR] Error listening to messages:", error.message);
      if (error.message && (error.message.includes("Project Id") || error.message.includes("Could not load the default credentials") || error.message.includes("PERMISSION_DENIED"))) {
        console.error("[LISTENER_ERROR] Authentication failed. Ensure firebase-service-account.json is placed in vynex-backend/.");
      } else {
        setTimeout(startNotificationListener, 15000);
      }
    }
  );
}

async function handleNewMessage(message) {
  try {
    const { id, chatId, senderId, receiverId, content } = message;
    if (!id || processedMessages.has(id) || !senderId || !receiverId) return;

    processedMessages.add(id);
    if (processedMessages.size > 10000) {
      const iter = processedMessages.values();
      processedMessages.delete(iter.next().value);
    }

    const db = admin.firestore();
    const senderDoc = await db.collection("users").doc(senderId).get();
    const senderName = senderDoc.exists
      ? senderDoc.data().displayName || senderDoc.data().username
      : "New Message";

    const receiverDoc = await db.collection("users").doc(receiverId).get();
    if (!receiverDoc.exists) return;

    const fcmToken = receiverDoc.data().fcmToken;
    if (!fcmToken) return;

    // Send high-priority DATA-ONLY message so Android can enforce Mute & Privacy
    const payload = {
      token: fcmToken,
      data: {
        title: senderName,
        body: content || "",
        chatId: chatId || "",
        senderId: senderId || "",
        messageId: id || ""
      },
      android: {
        priority: "high"
      }
    };

    await admin.messaging().send(payload);
    console.log(`[FCM_SUCCESS] Push notification sent for message ${id} to ${receiverId}`);
  } catch (err) {
    console.error(`[FCM_ERROR] Failed sending notification for message ${message?.id}:`, err.message);
  }
}

// ----------------------------------------------------
// 5. Start Server
// ----------------------------------------------------
app.listen(PORT, () => {
  console.log(`===============================================`);
  console.log(` Vynex Backend Server listening on port ${PORT}`);
  console.log(` Base URL: ${BASE_URL}`);
  console.log(` Health endpoint: ${BASE_URL}/health`);
  console.log(` Media Storage: ${STORAGE_PATH}`);
  console.log(`===============================================`);
  startNotificationListener();
});
