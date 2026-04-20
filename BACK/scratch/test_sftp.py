import paramiko
import os
from dotenv import load_dotenv

# Cargar .env
env_path = r"C:\CONTEOCICLICO\CONTEOCICLICO\BACK\.env"
load_dotenv(env_path)

SFTP_HOST = os.getenv("SFTP_HOST")
SFTP_USER = os.getenv("SFTP_USER")
SFTP_PASSWORD = os.getenv("SFTP_PASSWORD")
SFTP_PORT = int(os.getenv("SFTP_PORT", 22))

def test_sftp():
    print(f"Probando conexión SFTP a {SFTP_HOST}...")
    transport = None
    try:
        transport = paramiko.Transport((SFTP_HOST, SFTP_PORT))
        transport.connect(username=SFTP_USER, password=SFTP_PASSWORD)
        sftp = paramiko.SFTPClient.from_transport(transport)
        print("¡Conexión SFTP exitosa!")
        
        folders = sftp.listdir("/")
        print(f"Directorios en la raíz: {folders}")
        
        sftp.close()
    except Exception as e:
        print(f"Falla en la conexión SFTP: {e}")
    finally:
        if transport:
            transport.close()

if __name__ == "__main__":
    test_sftp()
