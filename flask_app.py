from flask import Flask, request, jsonify
from flask_sqlalchemy import SQLAlchemy
from sqlalchemy import inspect
import decimal
from math import sin, cos, atan2, sqrt, pi, log10

app = Flask(__name__)
app.config['SQLALCHEMY_DATABASE_URI'] = 'mysql+pymysql://kmekhovichlbs:lbspassword@kmekhovichlbs.mysql.pythonanywhere-services.com/kmekhovichlbs$default'
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False
db = SQLAlchemy(app)

class Wifi(db.Model):
    bssid = db.Column(db.String(80), primary_key=True)
    lat = db.Column(db.Numeric(10, 6), nullable=False)
    lon = db.Column(db.Numeric(10, 6), nullable=False)
    level = db.Column(db.Integer, nullable=False)
    frequency = db.Column(db.Float, nullable=False)

    def __repr__(self):
        return '<Bssid %r>' % self.bssid

@app.route('/create', methods=['GET'])
def create():
    with app.app_context():
        db.create_all()
    return 'created'

@app.route('/lbsget', methods=['GET'])
def get_lbs():
    wifis = Wifi.query.all()
    return jsonify([{'bssid': wifi.bssid} for wifi in wifis])

@app.route('/get_all', methods=['GET'])
def get_all():
    wifis = Wifi.query.all()
    response = {
        "wifi": []
    }
    for wifi in wifis:
        response["wifi"].append({"lat": wifi.lat, "lon": wifi.lon})
    return jsonify(response)

@app.route('/lbs', methods = ['POST'])
def lbs():
    data = request.get_json()

    if not data:
        return jsonify({"count": 0}), 400

    cnt = 0
    upd = 0
    for item in data:
        bssid = item.get('BSSID')
        wifi = Wifi.query.get(bssid)
        new_wifi_values = {
            "bssid": bssid,
            "lat": item.get('lat'),
            "lon": item.get('lon'),
            "frequency": item.get('frequency'),
            "level": item.get('level'),
        }
        if not wifi:
            new_wifi = Wifi(**new_wifi_values)
            db.session.add(new_wifi)
            cnt += 1
        elif new_wifi_values['level'] < wifi.level:
            wifi.lat = new_wifi_values['lat']
            wifi.lon = new_wifi_values['lon']
            wifi.frequency = new_wifi_values['frequency']
            wifi.level = new_wifi_values['level']
            upd += 1

    db.session.commit()

    return jsonify({"count": cnt, "upd": upd}), 200



def dist(lat1, lon1, lat2, lon2):
    R = 6371000 # Earth radius
    f1 = lat1 * pi / 180.
    f2 = lat2 * pi / 180.
    delta_f = (lat2 - lat1) * pi / 180.
    delta_l = (lon2 - lon1) * pi / 180.
    a = sin(delta_f/2) ** 2 + cos(f1) * cos(f2) * (sin(delta_l/2) ** 2)
    c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return R * c

def calculateDistance(level, frequency):
    return 10.0 ** ((27.55 - (20 * log10(frequency)) - level)/20)

@app.route('/detect', methods = ['POST'])
def detect():
    data = request.get_json()

    if not data:
        return jsonify({"lat": 0, "lon": 0, "count": 0}), 400

    wifis = []
    for item in data:
        bssid = item.get('BSSID')
        print(bssid)
        wifi = Wifi.query.get(bssid)
        if wifi is not None:
            wifis.append({"lat": float(wifi.lat), "lon": float(wifi.lon), "level": float(item.get('level')), "frequency": float(item.get('frequency'))})
    if len(wifis) == 0:
        return jsonify({"lat": 0., "lon": 0., "count": 0, "wifi": []}), 404

    levels = []
    for index, first in enumerate(wifis):
        dist_from_level = calculateDistance(first["level"], first["frequency"])
        if len(wifis) <= 5 or dist_from_level <= 100.0:
            levels.append((dist_from_level, index))
    levels = sorted(levels)
    cnt = max(1, int(float(len(levels)) * 0.68))
    new_wifis = []
    for c in range(cnt):
        new_wifis.append(wifis[levels[c][1]])
    wifis = new_wifis


    distances = []
    for index, first in enumerate(wifis):
        summary = 0.
        for second in wifis:
            summary += dist(first["lat"], first["lon"], second["lat"], second["lon"])
        distances.append((summary, index))
    distances = sorted(distances)

    cnt = max(1, int(float(len(distances)) * 0.68))

    new_wifis = []
    sumlat = 0.
    sumlon = 0.
    for i in range(cnt):
        sumlat += wifis[distances[i][1]]["lat"]
        sumlon += wifis[distances[i][1]]["lon"]
        new_wifis.append(wifis[distances[i][1]])


    return jsonify({"lat": sumlat / cnt, "lon": sumlon / cnt, "count": cnt, "wifi": new_wifis}), 200
