from app import app
from models import User
from notification_service import send_multicast_notification
import sys

def send_test_push(mode="event"):
    with app.app_context():
        # 1. Find users with keyword "전시회" (or just all users for demo)
        keyword = "전시회"
        users = User.query.filter(User.fcm_token.isnot(None)).all()
            
        if not users:
            print("No users found with FCM tokens.")
            return

        tokens = [user.fcm_token for user in users]
        print(f"Sending {mode} notification to {len(tokens)} devices...")
        
        if mode == "event":
            # Data message for "New Event" (Dummy Data - Exhibition Theme)
            data_payload = {
                "type": "dummy_event",
                "title": "키워드 알림: 전시회",
                "body": "'인천 현대 미술 특별전'이 새로 등록되었습니다!",
                "event_id": "9999",
                "event_title": "인천 현대 미술 특별전",
                "event_date": "20251215",
                "event_location": "인천문화예술회관",
                "event_description": "인천의 현대 미술을 한눈에 볼 수 있는 특별 전시회입니다. 다양한 작가들의 작품을 감상하세요.",
                "event_category": "전시/공연",
                "event_image": "https://via.placeholder.com/600x400/0000FF/FFFFFF?text=Art+Exhibition" 
            }
            # Note: We send ONLY data to force onMessageReceived in background
            success = send_multicast_notification(tokens, None, None, data=data_payload)
            
        elif mode == "scheduled":
            # Data message for "Scheduled Notification" (Dummy Data)
            data_payload = {
                "type": "scheduled_simulation",
                "title": "이벤트 알림",
                "body": "'인천 현대 미술 특별전'이 1시간 후에 시작됩니다!",
                "event_id": "9999",
                "event_title": "인천 현대 미술 특별전",
                "event_date": "20251215",
                "event_location": "인천문화예술회관",
                "event_description": "인천의 현대 미술을 한눈에 볼 수 있는 특별 전시회입니다. 곧 시작되니 놓치지 마세요!",
                "event_category": "전시/공연",
                "event_image": "https://via.placeholder.com/600x400/0000FF/FFFFFF?text=Art+Exhibition"
            }
            success = send_multicast_notification(tokens, None, None, data=data_payload)

        if success:
            print(">> Test notification sent successfully!")
        else:
            print(">> Failed to send test notification.")

if __name__ == "__main__":
    mode = "event"
    if len(sys.argv) > 1:
        mode = sys.argv[1]
    send_test_push(mode)
