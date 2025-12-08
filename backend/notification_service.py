import firebase_admin
from firebase_admin import credentials, messaging
import os
from datetime import datetime

# Initialize Firebase Admin SDK
# Note: You need to place your serviceAccountKey.json in the backend directory
# or set the GOOGLE_APPLICATION_CREDENTIALS environment variable.
cred_path = os.path.join(os.path.dirname(__file__), 'serviceAccountKey.json')

if os.path.exists(cred_path):
    cred = credentials.Certificate(cred_path)
    firebase_admin.initialize_app(cred)
else:
    print("Warning: serviceAccountKey.json not found. Push notifications will not work.")
    # For development without keys, we can mock the initialization or just handle errors gracefully

def send_push_notification(token, title, body, data=None):
    """
    Sends a push notification to a single device.
    """
    if not token:
        return False

    if not firebase_admin._apps:
        print("Firebase not initialized. Skipping notification.")
        return False

    try:
        message = messaging.Message(
            notification=messaging.Notification(
                title=title,
                body=body,
            ),
            data=data if data else {},
            token=token,
        )
        response = messaging.send(message)
        print('Successfully sent message:', response)
        return True
    except Exception as e:
        print('Error sending message:', e)
        return False

def send_multicast_notification(tokens, title, body, data=None):
    """
    Sends a push notification to multiple devices.
    """
    if not tokens:
        return False

    if not firebase_admin._apps:
        print("Firebase not initialized. Skipping notification.")
        return False

    try:
        message = messaging.MulticastMessage(
            notification=messaging.Notification(
                title=title,
                body=body,
            ) if title and body else None,
            data=data if data else {},
            tokens=tokens,
        )
        # send_multicast was deprecated/removed in newer versions?
        # Use send_each_for_multicast if available, or check docs.
        # Actually, send_multicast should still work in 6.x, but we upgraded to 7.x
        # In 7.x, send_multicast is still there but maybe we need to import it differently?
        # Let's try send_each_for_multicast which is the new standard.
        
        response = messaging.send_each_for_multicast(message)
        print(f'{response.success_count} messages were sent successfully')
        if response.failure_count > 0:
            for idx, resp in enumerate(response.responses):
                if not resp.success:
                    print(f'Failure {idx}: {resp.exception}')
        return True
    except Exception as e:
        print('Error sending multicast message:', e)
        # Fallback for older versions or if send_each_for_multicast fails
        try:
            response = messaging.send_multicast(message)
            print(f'{response.success_count} messages were sent successfully')
            return True
        except:
            return False
