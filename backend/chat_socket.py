from flask import request
from flask_socketio import SocketIO, emit, join_room, leave_room

socketio = SocketIO(cors_allowed_origins="*", logger=True, engineio_logger=True)

# Store user rooms: {sid: room_id}
user_rooms = {}

def init_socketio(app):
    socketio.init_app(app)
    
    # Register all handlers inside this function
    @socketio.on('connect')
    def handle_connect():
        print(f"[HANDLER] Client connected: {request.sid}", flush=True)
        emit('response', {'data': 'Connected'})

    @socketio.on('disconnect')
    def handle_disconnect():
        print(f"[HANDLER] Client disconnected: {request.sid}", flush=True)
        if request.sid in user_rooms:
            del user_rooms[request.sid]

    @socketio.on('join')
    def handle_join(data):
        room = data.get('room')
        nickname = data.get('nickname', 'Anonymous')
        print(f"[HANDLER] JOIN - sid:{request.sid}, room:{room}, nickname:{nickname}", flush=True)
        if room:
            join_room(room)
            user_rooms[request.sid] = room
            print(f"[HANDLER] Successfully joined room {room}", flush=True)
            emit('system_message', {'message': f'{nickname}님이 입장하셨습니다.'}, to=room)

    @socketio.on('leave')
    def handle_leave(data):
        room = data.get('room')
        nickname = data.get('nickname', 'Anonymous')
        if room:
            leave_room(room)
            if request.sid in user_rooms:
                del user_rooms[request.sid]
            emit('system_message', {'message': f'{nickname}님이 퇴장하셨습니다.'}, to=room)

    @socketio.on('update_location')
    def handle_location(data):
        pass  # Location tracking not needed for chat demo

    @socketio.on('send_message')
    def handle_message(data):
        nickname = data.get('nickname')
        message = data.get('message')
        room = user_rooms.get(request.sid)
        
        print(f"[HANDLER] MESSAGE - from:{nickname}, text:{message}, room:{room}", flush=True)

        if not room:
            print(f"[HANDLER] ERROR: User not in any room!", flush=True)
            return

        # Broadcast to everyone in the room (including sender)
        print(f"[HANDLER] Broadcasting to room: {room}", flush=True)
        emit('receive_message', {
            'nickname': nickname,
            'message': message,
            'distance': 0
        }, to=room)
        print(f"[HANDLER] Broadcast complete!", flush=True)
    
    return socketio
