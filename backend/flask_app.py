from flask import Flask, request, jsonify, render_template
from flask_sqlalchemy import SQLAlchemy
from sqlalchemy import inspect, create_engine
import decimal
from math import sin, cos, atan2, sqrt, pi, log10
import os
from dotenv import load_dotenv
import requests
import json
import pytz
from datetime import datetime
import numpy as np
from sqlalchemy.exc import DisconnectionError
from sqlalchemy.orm import sessionmaker


load_dotenv('envs.sh')
app = Flask(__name__)
app.config['SQLALCHEMY_DATABASE_URI'] = os.getenv('SQLALCHEMY_DATABASE_URI')
OPEN_CELL_ID_API_KEY = os.getenv('OPEN_CELL_ID_API_KEY')
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False
app.config['SQLALCHEMY_ENGINE_OPTIONS'] = {"pool_pre_ping": True}
db = SQLAlchemy(app)

def get_cell_coordinates(mcc, mnc, lac, cellid):
    url = f"http://opencellid.org/cell/get?key={OPEN_CELL_ID_API_KEY}&mcc={mcc}&mnc={mnc}&lac={lac}&cellid={cellid}&format=json"

    response = requests.get(url)

    if response.status_code == 200:
        data = response.json()
        lat = data.get('lat')
        lon = data.get('lon')
        if lat is not None and lon is not None:
            return lat, lon
        else:
            raise Exception('No cell')
    else:
        raise Exception('Not 200')

class Wifi(db.Model):
    bssid = db.Column(db.String(80), primary_key=True)
    lat = db.Column(db.Numeric(10, 6), nullable=False)
    lon = db.Column(db.Numeric(10, 6), nullable=False)
    level = db.Column(db.Integer, nullable=False)
    frequency = db.Column(db.Float, nullable=False)

    def __repr__(self):
        obj = {
            "bssid": self.bssid,
            "lat": float(self.lat) if self.lat else None,
            "lon": float(self.lon) if self.lon else None,
            "level": self.level,
            "frequency": self.frequency
        }
        return json.dumps(obj, ensure_ascii=False)

class GlobalLog(db.Model):
    index = db.Column(db.Integer, primary_key=True, autoincrement=True)
    input_gps = db.Column(db.String(100), nullable=True)
    input_wifis = db.Column(db.String(50000), nullable=True)
    input_cells = db.Column(db.String(5000), nullable=True)
    output = db.Column(db.String(5000), nullable=False)
    error_code = db.Column(db.Integer, nullable=False)
    queries = db.Column(db.String(50000), nullable=True)
    timestamp = db.Column(db.Integer, nullable=False)
    algo = db.Column(db.String(10), nullable=False)

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

def get_error_output(code=400):
    return {"lat": 0, "lon": 0, "count": 0}, 400, []

def jsonify_response(response):
    return jsonify(response[0]), response[1]

def run_algo(data, filter=[True, True]):
    queries = []
    if not data:
        return get_error_output()

    cells = []
    for item in data['cellList']:
        mcc = item.get('mcc')
        mnc = item.get('mnc')
        lac = item.get('lac')
        cell_id = item.get('cellId')
        if mcc != 2147483647:
            try:
                coordinates = get_cell_coordinates(mcc, mnc, lac, cell_id)
            except Exception:
                continue
            queries.append({"cell": str(coordinates)})
            # , "level": float(item.get('level'))
            cells.append({"lat": float(coordinates[0]), "lon": float(coordinates[1])})

    wifis = []
    for item in data['wifiList']:
        bssid = item.get('BSSID')
        wifi = Wifi.query.get(bssid)
        if wifi is not None:
            queries.append({"wifi": wifi})
            wifis.append({"lat": float(wifi.lat), "lon": float(wifi.lon), "level": float(item.get('level')), "frequency": float(item.get('frequency'))})
    if len(wifis) == 0:
        return get_error_output(code=404)

    levels = []
    for index, first in enumerate(wifis):
        dist_from_level = calculateDistance(first["level"], first["frequency"])
        if len(wifis) <= 5 or dist_from_level <= 100.0:
            levels.append((dist_from_level, index))
    levels = sorted(levels)
    cnt = max(1, int(float(len(levels)) * 0.68)) if filter[0] else len(levels)
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

    cnt = max(1, int(float(len(distances)) * 0.68)) if filter[1] else len(distances)

    new_wifis = []
    sumlat = 0.
    sumlon = 0.
    for i in range(cnt):
        sumlat += wifis[distances[i][1]]["lat"]
        sumlon += wifis[distances[i][1]]["lon"]
        new_wifis.append(wifis[distances[i][1]])

    return {"lat": sumlat / cnt, "lon": sumlon / cnt, "count": cnt, "wifi": new_wifis, "cells": cells}, 200, queries

def fill_log(algo_name, data, output):
    log = GlobalLog()
    log.algo = algo_name
    log.input_gps = str(data.get("inputGps", ""))
    log.input_wifis = str(data.get("wifiList", ""))
    log.input_cells = str(data.get("cellList", ""))
    log.output = str(output[0])
    log.error_code = output[1]
    log.queries = str(output[2])
    log.timestamp = datetime.now(pytz.timezone('Europe/Minsk')).timestamp()


def run_algo_v0(data, filter=[True, True]):
    output = run_algo(data, filter)
    fill_log(f"v0_{filter[0]}_{filter[1]}", data, output)
    db.session.add(log)
    db.session.commit()
    return output


@app.route('/detect', methods = ['POST'])
def detect():
    data = request.get_json()
    output = None
    for filter_0 in [True, False]:
        for filter_1 in [True, False]:
            cur_output = run_algo_v0(data, filter=[filter_0, filter_1])
            if not output:
                output = cur_output
    return jsonify_response(output)

@app.route('/')
def index():
    page = request.args.get('page', 1, type=int)
    per_page = 5
    logs_pagination = GlobalLog.query.paginate(page, per_page, error_out=False)
    return render_template('index.html', logs_pagination=logs_pagination)

@app.route('/item/<int:index>')
def item(index):
    log = GlobalLog.query.get_or_404(index)
    output = json.loads(log.output.replace("\'", "\""))
    queries = json.loads(log.queries.replace("\'", "\""))
    ll = set()
    for v in output['wifi']:
        ll.add((v['lat'], v['lon']))
    r_queries = []
    for d in queries:
        for k, v in d.items():
            if not (k == "wifi" and (v['lat'], v['lon']) in ll):
                r_queries.append(d)

    dict_log = {
        "inputGps": json.loads(log.input_gps.replace("\'", "\"")),
        "inputWifis": json.loads(log.input_wifis.replace("\'", "\"")),
        "inputCells": json.loads(log.input_cells.replace("\'", "\"")),
        "output": output,
        "queries": r_queries,
    }
    return render_template('item.html', log=dict_log)

def fetch_data():
    unique_algos = db.session.query(GlobalLog.algo).distinct().all()
    results = []
    for algo in unique_algos:
        algo_name = algo[0]
        entries = GlobalLog.query.filter_by(algo=algo_name).order_by(GlobalLog.timestamp.desc()).limit(1000).all()
        distances = []
        for entry in entries:
            input_gps = json.loads(entry.input_gps.replace("\'", "\""))
            output = json.loads(entry.output.replace("\'", "\""))
            distance = dist(input_gps['lat'], input_gps['lon'], output['lat'], output['lon'])
            distances.append(distance)
        percentiles = [f"{p:.2f}" for p in np.percentile(distances, [25, 50, 75, 90, 95, 99])]
        results.append({'algo': algo_name, 'percentiles': percentiles})
    return results

@app.route('/stats')
def stats():
    data = fetch_data()
    return render_template('stats.html', data=data)

@app.route('/map', methods=['GET'])
def map():
    wifis = Wifi.query.all()
    return render_template('map.html', wifis=wifis)
