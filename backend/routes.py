from flask import Blueprint, jsonify
from crawler import DataCrawler
from venue_crawler import VenueCrawler
from models import db, Event

# 블루프린트 생성 (이름: api, 접두어: /api는 app.py에서 설정함)
api_bp = Blueprint('api', __name__)

# [API 1] 서버 상태 확인용
# 주소: http://localhost:5000/api/
@api_bp.route('/', methods=['GET'])
def health_check():
    """
    서버 상태 확인
    ---
    responses:
      200:
        description: 서버가 정상 작동 중입니다.
    """
    return jsonify({
        "status": "active",
        "message": "LocalNow API Server is running."
    })

# [API 2] 저장된 이벤트 조회 (DB)
# 주소: http://localhost:5000/api/events
@api_bp.route('/events', methods=['GET'])
def get_events():
    """
    저장된 이벤트 목록 조회
    ---
    responses:
      200:
        description: DB에 저장된 이벤트 목록을 반환합니다.
    """
    from datetime import datetime, timedelta
    today = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
    yesterday = (datetime.now() - timedelta(days=1)).strftime("%Y%m%d")
    
    # Filter by end_date (datetime) or date (string) for backwards compatibility
    events = Event.query.filter(
        db.or_(
            Event.end_date >= today,  # Use end_date if available
            db.and_(Event.end_date.is_(None), Event.date >= yesterday)  # Fallback to date field
        )
    ).order_by(Event.date).all()
    
    result = []
    for event in events:
        result.append({
            "id": event.id,
            "title": event.title,
            "category": event.category,
            "date": event.date,
            "start_date": event.start_date.strftime("%Y-%m-%d") if event.start_date else None,
            "end_date": event.end_date.strftime("%Y-%m-%d") if event.end_date else None,
            "location": event.location,
            "lat": event.lat,
            "lng": event.lng,
            "description": event.description,
            "image": event.image,
            "source": event.source,
            "link": event.link
        })
    return jsonify({
        "status": "success",
        "count": len(result),
        "data": result
    })

# [API 3] 크롤링 테스트 (실시간 - 디버깅용)
# 주소: http://localhost:5000/api/crawl/test
@api_bp.route('/crawl/test', methods=['GET'])
def test_crawling():
    try:
        # 크롤러 객체 생성
        crawler = DataCrawler()
        
        # 1. 공공데이터(축제) 수집
        print(">> [Request] 공공데이터 수집 요청 시작...")
        public_data = crawler.fetch_public_festivals()
        
        # 2. Venue 크롤링 (공식 행사장)
        print(">> [Request] Venue 크롤링 시작...")
        venue_crawler = VenueCrawler()
        venue_data = venue_crawler.crawl_all()
        venue_crawler.close()
        
        # 결과 합치기
        result = {
            "status": "success",
            "total_count": len(public_data) + len(venue_data),
            "data": {
                "public_festivals": public_data,
                "venues": venue_data
            }
        }
        return jsonify(result)

    except Exception as e:
        # 에러 발생 시 처리
        return jsonify({
            "status": "error",
            "message": str(e)
        }), 500

# [API 4] 크롤링 + DB 저장 (하루 한 번 실행용)
# 주소: http://localhost:5003/api/crawl/save
@api_bp.route('/crawl/save', methods=['POST'])
def crawl_and_save():
    """
    크롤링 후 DB에 저장
    ---
    responses:
      200:
        description: 크롤링 결과를 DB에 저장합니다.
    """
    try:
        # 크롤러 객체 생성
        crawler = DataCrawler()
        
        # 1. 공공데이터(축제) 수집
        print(">> [API] 공공데이터 수집...")
        public_data = crawler.fetch_public_festivals()
        
        # 2. Venue 크롤링
        print(">> [API] Venue 크롤링...")
        venue_crawler = VenueCrawler()
        venue_data = venue_crawler.crawl_all()
        venue_crawler.close()
        
        all_data = public_data + venue_data
        print(f">> [API] 총 {len(all_data)}개 수집 완료")
        
        # DB 저장
        new_count = 0
        for item in all_data:
            # 중복 체크 (제목과 날짜가 같으면 중복)
            exists = Event.query.filter_by(title=item['title'], date=item.get('date')).first()
            if not exists:
                new_event = Event(
                    title=item['title'],
                    category=item.get('category'),
                    date=item.get('date'),
                    start_date=item.get('start_date'),
                    end_date=item.get('end_date'),
                    location=item.get('location'),
                    lat=item.get('lat', 0),
                    lng=item.get('lng', 0),
                    description=item.get('description', ''),
                    image=item.get('image', ''),
                    source=item.get('source', ''),
                    link=item.get('link', '')
                )
                db.session.add(new_event)
                new_count += 1
        
        db.session.commit()
        print(f">> [API] {new_count}개 신규 저장 완료")
        
        return jsonify({
            "status": "success",
            "total_crawled": len(all_data),
            "new_saved": new_count,
            "message": f"크롤링 완료! {len(all_data)}개 수집, {new_count}개 신규 저장"
        })
        
    except Exception as e:
        db.session.rollback()
        return jsonify({
            "status": "error",
            "message": str(e)
        }), 500