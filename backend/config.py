class Config:
    MYSQL_HOST = 'localhost'       # siempre localhost en local
    MYSQL_USER = 'root'            # tu usuario de MySQL
    MYSQL_PASSWORD = ''            # tu contraseña (vacía si usás XAMPP/WAMP)
    MYSQL_DB = 'cazabaches'      # el nombre de la base de datos
    MYSQL_CURSORCLASS = 'DictCursor'
    JWT_SECRET = 'goku_ssj3_whis_ssjg'