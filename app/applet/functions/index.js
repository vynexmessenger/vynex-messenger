const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

exports.sendNotificationOnNewMessage = functions.firestore
  .document("chats/{chatId}/messages/{messageId}")
  .onCreate(async (snap, context) => {
    const message = snap.data();
    const { chatId, messageId } = context.params;

    const senderId = message.senderId;
    const receiverId = message.receiverId;
    const content = message.content;

    if (!senderId || !receiverId) {
      console.log("Missing sender or receiver ID");
      return null;
    }

    try {
      // 1. Get the sender's details to show their name
      const senderDoc = await admin.firestore().collection("users").document(senderId).get();
      const senderName = senderDoc.exists ? (senderDoc.data().displayName || senderDoc.data().username) : "New Message";

      // 2. Get the receiver's details to get their FCM token
      const receiverDoc = await admin.firestore().collection("users").document(receiverId).get();
      if (!receiverDoc.exists) {
        console.log("Receiver does not exist");
        return null;
      }

      const fcmToken = receiverDoc.data().fcmToken;
      if (!fcmToken) {
        console.log(`No FCM token for user ${receiverId}`);
        return null;
      }

      // 3. Construct the notification payload (Data message)
      // We use a data-only message so that Android's onMessageReceived is ALWAYS triggered
      // and our custom NotificationManager can create the notification and open the correct chat.
      const payload = {
        token: fcmToken,
        data: {
          title: senderName,
          body: content,
          chatId: chatId,
          senderId: senderId
        },
        android: {
          priority: "high"
        }
      };

      // 4. Send the notification
      const response = await admin.messaging().send(payload);
      console.log("Successfully sent message:", response);
      return response;

    } catch (error) {
      console.error("Error sending notification:", error);
      
      // 5. Handle invalid tokens (cleanup)
      if (error.code === 'messaging/invalid-registration-token' ||
          error.code === 'messaging/registration-token-not-registered') {
        console.log("Token invalid/expired. Removing from Firestore...");
        await admin.firestore().collection("users").document(receiverId).update({
          fcmToken: admin.firestore.FieldValue.delete()
        });
      }
      return null;
    }
  });
