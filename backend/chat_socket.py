from flask import request
from flask_socketio import SocketIO, emit, join_room, leave_room
import math

socketio = SocketIO(cors_allowed_origins="*")

# Store user locations: {session_id: {'lat': lat, 'lng': lng}}
user_locations = {}

def init_socketio(app):
    socketio.init_app(app)
    return socketio

@socketio.on('connect')
def handle_connect():
    print(f"Client connected: {request.sid}")

@socketio.on('disconnect')
def handle_disconnect():
    if request.sid in user_locations:
        del user_locations[request.sid]
    print(f"Client disconnected: {request.sid}")

@socketio.on('update_location')
def handle_location_update(data):
    # data: {'lat': float, 'lng': float}
    lat = data.get('lat')
    lng = data.get('lng')
    if lat is not None and lng is not None:
        user_locations[request.sid] = {'lat': lat, 'lng': lng}
        # print(f"Updated location for {request.sid}: {lat}, {lng}")

@socketio.on('send_message')
def handle_message(data):
    # data: {'message': str, 'nickname': str}
    sender_sid = request.sid
    sender_loc = user_locations.get(sender_sid)
    
    if not sender_loc:
        return

    message = data.get('message')
    nickname = data.get('nickname', 'Anonymous')
    
    print(f"Message from {nickname}: {message}")

    # Broadcast to nearby users (within 500m)
    for sid, loc in user_locations.items():
        if sid == sender_sid:
            continue # Don't send back to sender (or do, depending on client logic)
            
        distance = calculate_distance(sender_loc['lat'], sender_loc['lng'], loc['lat'], loc['lng'])
        if distance <= 500: # 500 meters
            emit('receive_message', {
                'message': message,
                'nickname': nickname,
                'distance': round(distance)
            }, room=sid)

def calculate_distance(lat1, lng1, lat2, lng2):
    R = 6371000 # Earth radius in meters
    d_lat = math.radians(lat2 - lat1)
    d_lng = math.radians(lng2 - lng1)
    a = math.sin(d_lat / 2) * math.sin(d_lat / 2) + \
        math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * \
        math.sin(d_lng / 2) * math.sin(d_lng / 2)
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return R * c
