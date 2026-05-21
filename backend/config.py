import os

class Config:
    MYSQL_HOST = 'localhost'
    MYSQL_USER = 'root'
    MYSQL_PASSWORD = os.environ.get('MYSQL_PASSWORD', '')
    MYSQL_DB = 'cazabaches'
    MYSQL_CURSORCLASS = 'DictCursor'
    JWT_SECRET = os.environ.get('JWT_SECRET')