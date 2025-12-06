from apscheduler.schedulers.background import BackgroundScheduler
from crawler import DataCrawler
from venue_crawler import VenueCrawler
from models import db, Event
from app import app
import atexit
import time

def run_crawling_job():
    print(">> [Scheduler] Daily Crawling Started...")
    try:
        # 1. 공공데이터 크롤링
        crawler = DataCrawler()
        public_data = crawler.fetch_public_festivals()
        
        # 2. Venue 크롤링 (공식 행사장)
        print(">> [Scheduler] Venue Crawling Started...")
        venue_crawler = VenueCrawler()
        venue_data = venue_crawler.crawl_all()
        venue_crawler.close()
        
        all_data = public_data + venue_data
        
        print(f">> [Scheduler] Crawling Finished. {len(all_data)} items found.")
        # 2. DB 저장
        with app.app_context():
            new_count = 0
            for item in all_data:
                # 중복 체크 (제목과 날짜가 같으면 중복으로 간주)
                exists = Event.query.filter_by(title=item['title'], date=item['date']).first()
                if not exists:
                    new_event = Event(
                        title=item['title'],
                        category=item['category'],
                        date=item['date'],
                        start_date=item.get('start_date'),
                        end_date=item.get('end_date'),
                        location=item['location'],
                        lat=item['lat'],
                        lng=item['lng'],
                        description=item['description'],
                        image=item.get('image', ''),
                        source=item['source'],
                        link=item.get('link', '')
                    )
                    db.session.add(new_event)
                    new_count += 1
            
            db.session.commit()
            print(f">> [Scheduler] Crawling Finished. {len(all_data)} items found, {new_count} new items saved.")
        
    except Exception as e:
        print(f">> [Scheduler] Error during crawling: {e}")

def check_deadline_notifications():
    print(">> [Scheduler] Checking Deadline Notifications...")
    from notification_service import send_push_notification
    from models import Bookmark, NotificationHistory
    from datetime import datetime, timedelta

    with app.app_context():
        now = datetime.now()
        
        # Define thresholds
        thresholds = {
            "3d": timedelta(days=3),
            "1d": timedelta(days=1),
            "12h": timedelta(hours=12),
            "6h": timedelta(hours=6),
            "1h": timedelta(hours=1)
        }
        
        # Get all bookmarks where event has start_date
        bookmarks = db.session.query(Bookmark).join(Event).filter(Event.start_date.isnot(None)).all()
        
        for bookmark in bookmarks:
            event = bookmark.event
            user = bookmark.user
            
            if not user.fcm_token:
                continue
                
            time_diff = event.start_date - now
            
            # Check thresholds
            for label, delta in thresholds.items():
                # Allow 30 min buffer
                if delta - timedelta(minutes=30) <= time_diff <= delta + timedelta(minutes=30):
                    # Check if already sent
                    history = NotificationHistory.query.filter_by(
                        user_id=user.id, 
                        event_id=event.id, 
                        type=f"deadline_{label}"
                    ).first()
                    
                    if not history:
                        # Send notification
                        title = f"Upcoming Event: {event.title}"
                        body = f"Event starts in {label}!"
                        success = send_push_notification(user.fcm_token, title, body)
                        
                        if success:
                            # Log history
                            log = NotificationHistory(
                                user_id=user.id,
                                event_id=event.id,
                                type=f"deadline_{label}"
                            )
                            db.session.add(log)
                            print(f"Sent {label} deadline notification to {user.username}")
        
        db.session.commit()

def check_new_event_notifications():
    """
    사용자가 설정한 키워드(카테고리)와 일치하는 새 이벤트 발생 시 알림 발송
    """
    print(">> [Scheduler] Checking New Event Notifications...")
    from notification_service import send_push_notification
    from models import User, Event, NotificationHistory
    from datetime import datetime, timedelta
    
    with app.app_context():
        # 최근 2시간 내 추가된 이벤트 조회
        two_hours_ago = datetime.now() - timedelta(hours=2)
        new_events = Event.query.filter(Event.created_at >= two_hours_ago).all()
        
        if not new_events:
            return
        
        print(f">> Found {len(new_events)} new events")
        
        # 키워드 설정한 모든 사용자 조회
        users = User.query.filter(User.keywords.isnot(None), User.keywords != '', User.fcm_token.isnot(None)).all()
        
        for user in users:
            user_keywords = [kw.strip().lower() for kw in user.keywords.split(',')]
            
            for event in new_events:
                # 이벤트 카테고리나 제목이 사용자 키워드와 일치하는지 확인
                event_category = (event.category or '').lower()
                event_title = (event.title or '').lower()
                
                matched = False
                for keyword in user_keywords:
                    if keyword in event_category or keyword in event_title:
                        matched = True
                        break
                
                if matched:
                    # 이미 알림 보냈는지 확인
                    history = NotificationHistory.query.filter_by(
                        user_id=user.id,
                        event_id=event.id,
                        type='new_event'
                    ).first()
                    
                    if not history:
                        title = f"새 이벤트: {event.title[:30]}"
                        body = f"{event.category} - {event.location or '장소 미정'}"
                        success = send_push_notification(user.fcm_token, title, body)
                        
                        if success:
                            log = NotificationHistory(
                                user_id=user.id,
                                event_id=event.id,
                                type='new_event'
                            )
                            db.session.add(log)
                            print(f"Sent new event notification to {user.username} for {event.title}")
        
        db.session.commit()

def start_scheduler():
    scheduler = BackgroundScheduler()
    # 매일 자정(00:00)에 실행
    scheduler.add_job(func=run_crawling_job, trigger="cron", hour=0, minute=0)
    
    # Deadline Notification Check (Every hour)
    scheduler.add_job(func=check_deadline_notifications, trigger="interval", hours=1)
    
    # New Event Category Notification Check (Every 2 hours)
    scheduler.add_job(func=check_new_event_notifications, trigger="interval", hours=2)
    
    # 테스트용: 서버 시작 10초 후 실행 (디버깅용)
    # scheduler.add_job(func=run_crawling_job, trigger="date", run_date=datetime.now() + timedelta(seconds=10))
    
    scheduler.start()
    print(">> [Scheduler] Background Scheduler Started.")
    
    # Flask 종료 시 스케줄러도 종료
    atexit.register(lambda: scheduler.shutdown())
