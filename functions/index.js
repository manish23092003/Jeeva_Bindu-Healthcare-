const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

exports.sendBloodAlert = functions.firestore
  .document("alerts/{alertId}")
  .onCreate(async (snap, context) => {
    const alert = snap.data();
    const bloodGroup = alert.bloodGroup;
    const hospitalName = alert.hospitalName;

    // Get all donors who have an FCM token
    const donorsSnap = await admin
      .firestore()
      .collection("donors")
      .where("fcmToken", "!=", "")
      .get();

    const tokens = [];
    donorsSnap.forEach((doc) => {
      const token = doc.data().fcmToken;
      if (token) tokens.push(token);
    });

    if (tokens.length === 0) return null;

    // Send notification to all donors
    const message = {
      notification: {
        title: `🚨 ${bloodGroup} Blood Needed!`,
        body: `Urgent request at ${hospitalName}. Can you help?`,
      },
      tokens: tokens,
    };

    const response = await admin.messaging().sendEachForMulticast(message);
    console.log(
      `Sent: ${response.successCount}, Failed: ${response.failureCount}`
    );
    return null;
  });