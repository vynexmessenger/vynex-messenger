const admin = require("firebase-admin");
const express = require("express");

admin.initializeApp();

const app = express();
app.use(express.json());

const PORT = process.env.PORT || 8081;

app.get("/health", (req, res) => {
  res.status(200).send("OK");
});

const processedMessages = new Set();
const START_TIME = Date.now();

console.log(`[INIT] Starting Vynex Notification Server on port ${PORT}...`);
console.log(`[INIT] Firebase Admin initialized.`);

function startListener() {
  console.log(`[LISTENER] Starting Firestore listener on collectionGroup('messages')...`);
  console.log(`[LISTENER_CONFIG] Path: collectionGroup('messages')`);
  console.log(`[LISTENER_CONFIG] Query: None (In-memory filtering for timestamp >= ${START_TIME})`);
  
  const db = admin.firestore();

  db.collectionGroup("messages")
    .onSnapshot(
      (snapshot) => {
        snapshot.docChanges().forEach((change) => {
          const message = change.doc.data();
          const docPath = change.doc.ref.path;
          if (change.type === "added") {
            console.log(`[LISTENER_EVENT] Document ADDED at ${docPath}. ID: ${message.id || change.doc.id}`);
            const messageTime = message.timestamp?.toMillis
                ? message.timestamp.toMillis()
                : message.timestamp?._seconds
                    ? message.timestamp._seconds * 1000
                    : Number(message.timestamp);
                    
            console.log(`[LISTENER_TIME_DEBUG] Original:`, JSON.stringify(message.timestamp), `Converted:`, messageTime, `START_TIME:`, START_TIME, `Result:`, messageTime >= START_TIME);

            if (messageTime && messageTime >= START_TIME) {
              handleNewMessage(message);
            } else {
              console.log(`[LISTENER_IGNORE] Document ignored (old message or no timestamp). Converted Timestamp: ${messageTime}, Start: ${START_TIME}`);
            }
          } else if (change.type === "modified") {
            console.log(`[LISTENER_EVENT] Document MODIFIED at ${docPath}. ID: ${message.id || change.doc.id}`);
          } else {
            console.log(`[LISTENER_EVENT] Document ${change.type.toUpperCase()} at ${docPath}.`);
          }
        });
      },
      (error) => {
        console.error(`[LISTENER_ERROR] Error listening to messages:`, error);
        console.error(error.stack);
        setTimeout(startListener, 5000);
      }
    );
}

async function handleNewMessage(message) {
  try {
    const { id, chatId, senderId, receiverId, content, timestamp } = message;

    console.log(`[PROCESS] New message detected. ID: ${id}, Chat: ${chatId}, From: ${senderId}, To: ${receiverId}`);

    if (!id || processedMessages.has(id)) {
      console.log(`[SKIP] Message ${id} already processed or invalid ID.`);
      return;
    }

    if (!senderId || !receiverId) {
      console.log(`[SKIP] Missing sender or receiver ID for message ${id}.`);
      return;
    }
    
    processedMessages.add(id);

    if (processedMessages.size > 10000) {
      const iter = processedMessages.values();
      processedMessages.delete(iter.next().value);
    }

    await sendToUser(message);
  } catch (error) {
    console.error(`[PROCESS_ERROR] Error handling message ${message?.id}:`, error);
  }
}

async function sendToUser(messageData) {
  const { id, chatId, senderId, receiverId, content, timestamp } = messageData;
  const message = content; // map content to message just in case

  let retryCount = 0;
  const maxRetries = 3;

  while (retryCount < maxRetries) {
    try {
      console.log(`[SEND_ATTEMPT ${retryCount + 1}] Fetching user data for sender ${senderId} and receiver ${receiverId}...`);
      
      const db = admin.firestore();
      
      const senderDoc = await db.collection("users").doc(senderId).get();
      const senderName = senderDoc.exists ? (senderDoc.data().displayName || senderDoc.data().username) : "New Message";

      const receiverDoc = await db.collection("users").doc(receiverId).get();
      
      if (!receiverDoc.exists) {
        console.log(`[SEND_FAIL] Receiver ${receiverId} does not exist in Firestore.`);
        return;
      }

      const fcmToken = receiverDoc.data().fcmToken;
      if (!fcmToken) {
        console.log(`[SEND_FAIL] Receiver ${receiverId} has no FCM token registered.`);
        return;
      }

      // Mask all but first 4 and last 4 chars
      const maskedToken = fcmToken.length > 8 ? `${fcmToken.substring(0, 4)}...${fcmToken.substring(fcmToken.length - 4)}` : '***';
      console.log(`[SEND_PREP] Found FCM token for ${receiverId}: ${maskedToken}. Preparing payload...`);

      const payload = {
        token: fcmToken,
        data: {
          title: senderName,
          body: message || content || "",
          chatId: chatId || "",
          senderId: senderId || "",
          messageId: id || ""
        },
        android: {
          priority: "high"
        }
      };

      const response = await admin.messaging().send(payload);
      console.log(`[SEND_SUCCESS] Successfully dispatched push notification for message ${id}.`);
      console.log(`[SEND_SUCCESS] Firebase Admin response:`, JSON.stringify(response));
      
      return;
    } catch (error) {
      console.error(`[SEND_ERROR] Attempt ${retryCount + 1} failed for message ${id}:`, error.message);
      console.error(error.stack);
      
      if (error.code === 'messaging/invalid-registration-token' ||
          error.code === 'messaging/registration-token-not-registered') {
        console.log(`[CLEANUP] Token for user ${receiverId} is invalid/expired. Removing from Firestore...`);
        try {
          await admin.firestore().collection("users").doc(receiverId).update({
            fcmToken: admin.firestore.FieldValue.delete()
          });
          console.log(`[CLEANUP_SUCCESS] Removed invalid token for ${receiverId}.`);
        } catch (cleanupError) {
          console.error(`[CLEANUP_ERROR] Failed to remove token for ${receiverId}:`, cleanupError.message);
          console.error(cleanupError.stack);
        }
        return;
      }

      retryCount++;
      if (retryCount >= maxRetries) {
        console.error(`[SEND_FAIL] Max retries reached for message ${id}. Giving up.`);
      } else {
        const delay = Math.pow(2, retryCount) * 1000;
        console.log(`[RETRY] Waiting ${delay}ms before retrying...`);
        await new Promise(resolve => setTimeout(resolve, delay));
      }
    }
  }
}

app.listen(PORT, () => {
  console.log(`[SERVER_START] HTTP server listening on port ${PORT}`);
  startListener();
});
