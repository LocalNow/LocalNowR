from flask import request
from flask_socketio import SocketIO, emit, join_room, leave_room
from geopy.distance import geodesic
import math

socketio = SocketIO(cors_allowed_origins="*")

# Store user locations: {sid: (lat, lng)}
user_locations = {}
# Store user rooms: {sid: room_id}
user_rooms = {}

def init_socketio(app):
    socketio.init_app(app)
    return socketio

@socketio.on('connect')
def handle_connect():
    print(f"Client connected: {request.sid}")
    emit('response', {'data': 'Connected'})

@socketio.on('disconnect')
def handle_disconnect():
    print(f"Client disconnected: {request.sid}")
    if request.sid in user_locations:
        del user_locations[request.sid]
    if request.sid in user_rooms:
        room = user_rooms[request.sid]
        leave_room(room)
        del user_rooms[request.sid]

@socketio.on('join')
def handle_join(data):
    room = data.get('room')
    nickname = data.get('nickname', 'Anonymous')
    if room:
        join_room(room)
        user_rooms[request.sid] = room
        print(f"Client {request.sid} ({nickname}) joined room: {room}")
        emit('system_message', {'message': f'{nickname}님이 입장하셨습니다.'}, to=room)

@socketio.on('leave')
def handle_leave(data):
    room = data.get('room')
    nickname = data.get('nickname', 'Anonymous')
    if room:
        leave_room(room)
        if request.sid in user_rooms:
            del user_rooms[request.sid]
        print(f"Client {request.sid} ({nickname}) left room: {room}")
        emit('system_message', {'message': f'{nickname}님이 퇴장하셨습니다.'}, to=room)

@socketio.on('update_location')
def handle_location(data):
    lat = data.get('lat')
    lng = data.get('lng')
    user_locations[request.sid] = (lat, lng)
    # print(f"User {request.sid} location updated: {lat}, {lng}")

@socketio.on('send_message')
def handle_message(data):
    nickname = data.get('nickname')
    message = data.get('message')
    room = user_rooms.get(request.sid)

    if not room:
        # Fallback for global chat (if needed) or error
        return

    # Calculate distance for users in the same room (optional, but good for context)
    sender_loc = user_locations.get(request.sid)
    
    # Broadcast to everyone in the room
    # Note: We can still send distance info if we want, but 'room' is the primary filter now
    emit('receive_message', {
        'nickname': nickname,
        'message': message,
        'distance': 0 # Distance calculation can be refined per recipient if needed, but for room broadcast it's complex
    }, to=room)


def calculate_distance(lat1, lng1, lat2, lng2):
    R = 6371000 # Earth radius in meters
    d_lat = math.radians(lat2 - lat1)
    d_lng = math.radians(lng2 - lng1)
    a = math.sin(d_lat / 2) * math.sin(d_lat / 2) + \
        math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * \
        math.sin(d_lng / 2) * math.sin(d_lng / 2)
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return R * c
