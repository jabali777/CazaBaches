from dotenv import load_dotenv
load_dotenv(override=True)

from flask import Flask, request, jsonify
from flask_mysqldb import MySQL
from config import Config
import bcrypt
import jwt
import datetime
import os
import MySQLdb.cursors
import re
from functools import wraps
from werkzeug.utils import secure_filename

app = Flask(__name__)
app.config.from_object(Config)
mysql = MySQL(app)

UPLOAD_FOLDER = os.path.join(os.path.dirname(__file__), 'uploads')
os.makedirs(UPLOAD_FOLDER, exist_ok=True)
ALLOWED_EXTENSIONS = {'jpg', 'jpeg', 'png'}

def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS

def token_requerido(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        auth = request.headers.get('Authorization', '')
        if not auth.startswith('Bearer '):
            return jsonify({'error': 'Token requerido'}), 401
        token = auth.split(' ', 1)[1]
        try:
            payload = jwt.decode(token, app.config['JWT_SECRET'], algorithms=['HS256'])
        except jwt.ExpiredSignatureError:
            return jsonify({'error': 'Token expirado'}), 401
        except jwt.InvalidTokenError:
            return jsonify({'error': 'Token inválido'}), 401
        return f(payload, *args, **kwargs)
    return decorated


# ─── REGISTRO ────────────────────────────────────────────────
@app.route('/api/register', methods=['POST'])
def register():
    data = request.get_json()
    email = data.get('email', '').strip().lower()
    password = data.get('password', '')

    if not email or not password:
        return jsonify({'error': 'Email y contraseña requeridos'}), 400

    if len(password) < 6:
        return jsonify({'error': 'La contraseña debe tener al menos 6 caracteres'}), 400

    password_hash = bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')

    try:
        cur = mysql.connection.cursor(MySQLdb.cursors.DictCursor)
        cur.execute("INSERT INTO usuarios (email, password_hash) VALUES (%s, %s)",
                    (email, password_hash))
        mysql.connection.commit()
        user_id = cur.lastrowid
        cur.close()

        token = generar_token(user_id, email)
        return jsonify({'token': token, 'email': email}), 201

    except Exception as e:
        print(f"ERROR REAL: {e}")
        if 'Duplicate entry' in str(e):
            return jsonify({'error': 'El email ya está registrado'}), 409
        return jsonify({'error': 'Error interno del servidor'}), 500


# ─── LOGIN ───────────────────────────────────────────────────
@app.route('/api/login', methods=['POST'])
def login():
    data = request.get_json()
    email = data.get('email', '').strip().lower()
    password = data.get('password', '')

    if not email or not password:
        return jsonify({'error': 'Email y contraseña requeridos'}), 400

    try:
        cur = mysql.connection.cursor(MySQLdb.cursors.DictCursor)
        cur.execute("SELECT id, email, password_hash FROM usuarios WHERE email = %s", (email,))
        user = cur.fetchone()
        cur.close()

        if not user:
            return jsonify({'error': 'Credenciales incorrectas'}), 401

        if not bcrypt.checkpw(password.encode('utf-8'), user['password_hash'].encode('utf-8')):
            return jsonify({'error': 'Contraseña incorrecta'}), 401

        token = generar_token(user['id'], user['email'])
        return jsonify({'token': token, 'email': user['email']}), 200

    except Exception as e:
        print(f"ERROR REAL: {e}")
        return jsonify({'error': 'Error interno del servidor'}), 500


# ─── HELPER JWT ──────────────────────────────────────────────
def generar_token(user_id, email):
    payload = {
        'user_id': user_id,
        'email': email,
        'exp': datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=7)
    }
    return jwt.encode(payload, app.config['JWT_SECRET'], algorithm='HS256')


# ─── CREAR REPORTE ───────────────────────────────────────────
@app.route('/api/reportes', methods=['POST'])
@token_requerido
def crear_reporte(payload):
    print("FILES:", request.files)
    print("FORM:", request.form)

    titulo      = request.form.get('titulo', '').strip()
    descripcion = request.form.get('descripcion', '').strip()
    latitud     = request.form.get('latitud')
    longitud    = request.form.get('longitud')

    if not titulo or not latitud or not longitud:
        return jsonify({'error': 'titulo, latitud y longitud son requeridos'}), 400

    foto_path = None
    if 'foto' in request.files:
        foto = request.files['foto']
        print("FOTO recibida:", foto.filename)
        print("UPLOAD_FOLDER:", UPLOAD_FOLDER)
        print("Folder existe:", os.path.exists(UPLOAD_FOLDER))

        if foto.filename and allowed_file(foto.filename):
            filename = secure_filename(
                f"{datetime.datetime.now().strftime('%Y%m%d%H%M%S')}_{foto.filename}"
            )
            try:
                foto.save(os.path.join(UPLOAD_FOLDER, filename))
                foto_path = filename
                print("FOTO guardada en:", os.path.join(UPLOAD_FOLDER, filename))
            except Exception as e:
                print("ERROR guardando foto:", e)
        else:
            print("FOTO rechazada: filename vacío o extensión no permitida")
    else:
        print("No se recibió ninguna foto")

    try:
        cur = mysql.connection.cursor(MySQLdb.cursors.DictCursor)
        cur.execute(
            "INSERT INTO reportes (usuario_id, titulo, descripcion, latitud, longitud, imagen_path) "
            "VALUES (%s, %s, %s, %s, %s, %s)",
            (payload['user_id'], titulo, descripcion, latitud, longitud, foto_path)
        )
        mysql.connection.commit()
        reporte_id = cur.lastrowid
        cur.close()
        return jsonify({'id': reporte_id, 'mensaje': 'Reporte creado'}), 201
    except Exception as e:
        print(f"ERROR REAL: {e}")
        return jsonify({'error': 'Error interno del servidor'}), 500

@app.route('/api/reportes', methods=['GET'])
@token_requerido
def obtener_reportes(payload):
    try:
        cur = mysql.connection.cursor(MySQLdb.cursors.DictCursor)
        cur.execute("""
            SELECT r.id, r.titulo, r.descripcion, r.latitud, r.longitud,
                   r.imagen_path, r.fecha, u.email
            FROM reportes r
            JOIN usuarios u ON r.usuario_id = u.id
            ORDER BY r.fecha DESC
        """)
        reportes = cur.fetchall()
        cur.close()

        result = []
        for r in reportes:
            result.append({
                'id': r['id'],
                'titulo': r['titulo'],
                'descripcion': r['descripcion'] or '',
                'latitud': float(r['latitud']),
                'longitud': float(r['longitud']),
                'imagen_path': r['imagen_path'] or '',
                'fecha': r['fecha'].strftime('%d/%m/%Y %H:%M'),
                'email': r['email']
            })

        return jsonify(result), 200
    except Exception as e:
        print(f"ERROR REAL: {e}")
        return jsonify({'error': 'Error interno del servidor'}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)