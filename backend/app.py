from dotenv import load_dotenv
load_dotenv(override=True)

from flask import Flask, request, jsonify
from flask_mysqldb import MySQL
from config import Config
import bcrypt
import jwt
import datetime
import os

app = Flask(__name__)
app.config.from_object(Config)
mysql = MySQL(app)
print("JWT desde os.environ:", os.environ.get('JWT_SECRET'))
print("MySQL pass:", os.environ.get('MYSQL_PASSWORD'))
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

    # Hashear contraseña
    password_hash = bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')

    try:
        cur = mysql.connection.cursor()
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
        cur = mysql.connection.cursor()
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


if __name__ == '__main__':
    # 0.0.0.0 para que sea accesible desde la red local
    app.run(host='0.0.0.0', port=5000, debug=True)