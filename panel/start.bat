@echo off
cd /d "%~dp0"
python -m pip install -r requirements.txt -q
echo.
echo Mora TV Panel
echo PC:     http://127.0.0.1:8787
echo Movil:  http://TU-IP-LAN:8787
echo Admin:  admin / admin
echo.
python -m uvicorn app:app --host 0.0.0.0 --port 8787
pause
