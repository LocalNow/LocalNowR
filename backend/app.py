from flask import Flask, request
from flask_cors import CORS
from flasgger import Swagger
import os


app = Flask(__name__)
swagger = Swagger(app)

# 1. CORS 설정
CORS(app)

# 2. 데이터베이스 기본 설정 (SQLite)
basedir = os.path.abspath(os.path.dirname(__file__))
db_path = os.path.join(basedir, 'localnow.db')
app.config['SQLALCHEMY_DATABASE_URI'] = f'sqlite:///{db_path}'
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False

# 3. 기능 연결 (DB 및 API 라우트)
try:
    from models import db, User
    from routes import api_bp
    from auth_routes import auth_bp
    from bookmark_routes import bookmark_bp
    from user_routes import user_bp
    from flask_login import LoginManager
    from flask_socketio import SocketIO, emit, join_room, leave_room
    
    # DB 초기화
    db.init_app(app)
    
    # SocketIO 초기화 - 직접 생성
    socketio = SocketIO(app, cors_allowed_origins="*", logger=True, engineio_logger=True)
    
    # 사용자 룸 저장소
    user_rooms = {}

    # Socket.IO 이벤트 핸들러 등록 - 모듈 레벨에서 즉시 등록
    @socketio.on('connect')
    def handle_connect():
        print(f"✅ [HANDLER] Client connected: {request.sid}", flush=True)
        emit('response', {'data': 'Connected'})

    @socketio.on('join')
    def handle_join(data):
        room = data.get('room')
        nickname = data.get('nickname', 'Anonymous')
        print(f"✅ [HANDLER] JOIN - room:{room}, nickname:{nickname}", flush=True)
        if room:
            join_room(room)
            user_rooms[request.sid] = room
            emit('system_message', {'message': f'{nickname}님이 입장하셨습니다.'}, to=room)

    @socketio.on('send_message')
    def handle_message(data):
        nickname = data.get('nickname')
        message = data.get('message')
        room = user_rooms.get(request.sid)
        
        print(f"✅ [HANDLER] MESSAGE - from:{nickname}, text:{message}, room:{room}", flush=True)

        if not room:
            print(f"❌ [HANDLER] User not in room!", flush=True)
            return

        # Broadcast to room
        emit('receive_message', {
            'nickname': nickname,
            'message': message,
            'distance': 0
        }, to=room)
        print(f"✅ [HANDLER] Broadcast complete!", flush=True)

    @socketio.on('leave')
    def handle_leave(data):
        room = data.get('room')
        if request.sid in user_rooms:
            del user_rooms[request.sid]

    @socketio.on('update_location')
    def handle_location(data):
        pass

    @socketio.on('disconnect')
    def handle_disconnect():
        if request.sid in user_rooms:
            del user_rooms[request.sid]
        print(f"✅ [HANDLER] Client disconnected: {request.sid}", flush=True)

    with app.app_context():
        db.create_all()
    
    # LoginManager 설정
    app.secret_key = 'super-secret-key-change-this'
    login_manager = LoginManager()
    login_manager.init_app(app)
    login_manager.login_view = 'auth.login'

    @login_manager.user_loader
    def load_user(user_id):
        return User.query.get(int(user_id))
    
    # API 주소 등록
    app.register_blueprint(api_bp, url_prefix='/api')
    app.register_blueprint(auth_bp, url_prefix='/auth')
    app.register_blueprint(bookmark_bp, url_prefix='/api/bookmarks')
    app.register_blueprint(user_bp, url_prefix='/api')
    print(">> [System] Database & Routes linked successfully.")

except ImportError as e:
    print(f">>파일(models.py, routes.py)이 없거나 에러가 있습니다: {e}")
    print(">> 서버는 켜지지만 API 기능이 동작하지 않을 수 있습니다.")


# 4. 서버 실행
if __name__ == '__main__':
    from scheduler import start_scheduler
    start_scheduler()
    
    print(">> [LocalNow] Server is Starting on Port 5003 with SocketIO...")
    socketio.run(app, host='0.0.0.0', port=5003, debug=False)