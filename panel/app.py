import hashlib
import secrets
import sqlite3
from contextlib import contextmanager
from datetime import date, datetime
from pathlib import Path

from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles

ROOT = Path(__file__).parent
DATA = ROOT / "data"
DATA.mkdir(exist_ok=True)
DB = DATA / "mora.db"
STATIC = ROOT / "static"

app = FastAPI(title="Mora TV Panel")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])
app.mount("/static", StaticFiles(directory=STATIC), name="static")


def hash_password(password: str, salt: str | None = None) -> str:
    salt = salt or secrets.token_hex(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode(), salt.encode(), 120_000).hex()
    return f"{salt}${digest}"


def verify_password(password: str, stored: str) -> bool:
    try:
        salt, digest = stored.split("$", 1)
    except ValueError:
        return False
    return hash_password(password, salt) == stored


@contextmanager
def db():
    con = sqlite3.connect(DB)
    con.row_factory = sqlite3.Row
    try:
        yield con
        con.commit()
    finally:
        con.close()


def init_db():
    with db() as con:
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                username TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                role TEXT NOT NULL DEFAULT 'user',
                enabled INTEGER NOT NULL DEFAULT 1,
                expires_at TEXT DEFAULT '',
                playlist_type TEXT DEFAULT 'none',
                xtream_server TEXT DEFAULT '',
                xtream_user TEXT DEFAULT '',
                xtream_pass TEXT DEFAULT '',
                m3u_url TEXT DEFAULT '',
                created_at TEXT NOT NULL
            )
            """
        )
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS sessions (
                token TEXT PRIMARY KEY,
                user_id INTEGER NOT NULL,
                created_at TEXT NOT NULL
            )
            """
        )
        if con.execute("SELECT COUNT(*) c FROM users").fetchone()["c"] == 0:
            con.execute(
                """
                INSERT INTO users (name, username, password_hash, role, enabled, playlist_type, created_at)
                VALUES ('Administrador', 'admin', ?, 'admin', 1, 'none', ?)
                """,
                (hash_password("admin"), datetime.utcnow().isoformat()),
            )


def public_user(row) -> dict:
    return {
        "id": row["id"],
        "name": row["name"],
        "username": row["username"],
        "role": row["role"],
        "enabled": bool(row["enabled"]),
        "expires_at": row["expires_at"] or "",
        "playlist_type": row["playlist_type"] or "none",
        "xtream_server": row["xtream_server"] or "",
        "xtream_user": row["xtream_user"] or "",
        "xtream_pass": row["xtream_pass"] or "",
        "m3u_url": row["m3u_url"] or "",
    }


def profile_of(row) -> dict | None:
    ptype = row["playlist_type"] or "none"
    if ptype == "xtream" and row["xtream_server"] and row["xtream_user"]:
        host = row["xtream_server"].strip().rstrip("/")
        if host and not host.startswith("http"):
            host = "http://" + host
        return {
            "name": row["name"],
            "host": host,
            "username": row["xtream_user"],
            "password": row["xtream_pass"],
            "isM3u": False,
            "m3uUrl": "",
        }
    if ptype == "m3u" and row["m3u_url"]:
        return {
            "name": row["name"],
            "host": "",
            "username": "",
            "password": "",
            "isM3u": True,
            "m3uUrl": row["m3u_url"],
        }
    return None


def current_user(request: Request, authorization: str | None):
    token = None
    if authorization and authorization.lower().startswith("bearer "):
        token = authorization.split(" ", 1)[1].strip()
    token = token or request.cookies.get("mora_token")
    if not token:
        raise HTTPException(401, "No hay sesión")
    with db() as con:
        row = con.execute(
            "SELECT u.* FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token = ?",
            (token,),
        ).fetchone()
    if not row:
        raise HTTPException(401, "Sesión inválida")
    if not row["enabled"]:
        raise HTTPException(403, "Usuario deshabilitado")
    if row["expires_at"]:
        try:
            if date.fromisoformat(row["expires_at"][:10]) < date.today():
                raise HTTPException(403, "Cuenta expirada")
        except ValueError:
            pass
    return row


init_db()


@app.get("/")
def index():
    return FileResponse(STATIC / "index.html")


@app.post("/api/login")
def login(payload: dict):
    username = (payload.get("username") or "").strip()
    password = payload.get("password") or ""
    with db() as con:
        row = con.execute("SELECT * FROM users WHERE username = ?", (username,)).fetchone()
        if not row or not verify_password(password, row["password_hash"]):
            raise HTTPException(401, "Usuario o contraseña incorrectos")
        if not row["enabled"]:
            raise HTTPException(403, "Usuario deshabilitado")
        token = secrets.token_hex(24)
        con.execute(
            "INSERT INTO sessions (token, user_id, created_at) VALUES (?, ?, ?)",
            (token, row["id"], datetime.utcnow().isoformat()),
        )
    body = {"token": token, "user": public_user(row), "profile": profile_of(row)}
    resp = JSONResponse(body)
    resp.set_cookie("mora_token", token, httponly=True, samesite="lax")
    return resp


@app.get("/api/me")
def me(request: Request, authorization: str | None = Header(default=None)):
    row = current_user(request, authorization)
    return {"user": public_user(row), "profile": profile_of(row)}


@app.post("/api/logout")
def logout(request: Request, authorization: str | None = Header(default=None)):
    token = None
    if authorization and authorization.lower().startswith("bearer "):
        token = authorization.split(" ", 1)[1].strip()
    token = token or request.cookies.get("mora_token")
    if token:
        with db() as con:
            con.execute("DELETE FROM sessions WHERE token = ?", (token,))
    resp = JSONResponse({"ok": True})
    resp.delete_cookie("mora_token")
    return resp


def require_admin(row):
    if row["role"] != "admin":
        raise HTTPException(403, "Solo administrador")


@app.get("/api/admin/users")
def list_users(request: Request, authorization: str | None = Header(default=None)):
    require_admin(current_user(request, authorization))
    with db() as con:
        rows = con.execute("SELECT * FROM users ORDER BY id").fetchall()
    return [public_user(r) for r in rows]


@app.post("/api/admin/users")
def create_user(payload: dict, request: Request, authorization: str | None = Header(default=None)):
    require_admin(current_user(request, authorization))
    username = (payload.get("username") or "").strip()
    password = payload.get("password") or ""
    if not username or not password:
        raise HTTPException(400, "Usuario y contraseña de la app son obligatorios")
    try:
        with db() as con:
            cur = con.execute(
                """
                INSERT INTO users (name, username, password_hash, role, enabled, expires_at,
                    playlist_type, xtream_server, xtream_user, xtream_pass, m3u_url, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    payload.get("name") or username,
                    username,
                    hash_password(password),
                    payload.get("role") or "user",
                    1 if payload.get("enabled", True) else 0,
                    payload.get("expires_at") or "",
                    payload.get("playlist_type") or "none",
                    payload.get("xtream_server") or "",
                    payload.get("xtream_user") or "",
                    payload.get("xtream_pass") or "",
                    payload.get("m3u_url") or "",
                    datetime.utcnow().isoformat(),
                ),
            )
            row = con.execute("SELECT * FROM users WHERE id = ?", (cur.lastrowid,)).fetchone()
    except sqlite3.IntegrityError:
        raise HTTPException(400, "Ese usuario ya existe")
    return public_user(row)


@app.put("/api/admin/users/{user_id}")
def update_user(user_id: int, payload: dict, request: Request, authorization: str | None = Header(default=None)):
    require_admin(current_user(request, authorization))
    fields = {
        "name": payload.get("name"),
        "role": payload.get("role"),
        "enabled": None if "enabled" not in payload else (1 if payload.get("enabled") else 0),
        "expires_at": payload.get("expires_at"),
        "playlist_type": payload.get("playlist_type"),
        "xtream_server": payload.get("xtream_server"),
        "xtream_user": payload.get("xtream_user"),
        "xtream_pass": payload.get("xtream_pass"),
        "m3u_url": payload.get("m3u_url"),
    }
    sets, vals = [], []
    for k, v in fields.items():
        if v is not None:
            sets.append(f"{k} = ?")
            vals.append(v)
    if payload.get("password"):
        sets.append("password_hash = ?")
        vals.append(hash_password(payload["password"]))
    if not sets:
        raise HTTPException(400, "Nada que actualizar")
    vals.append(user_id)
    with db() as con:
        con.execute(f"UPDATE users SET {', '.join(sets)} WHERE id = ?", vals)
        row = con.execute("SELECT * FROM users WHERE id = ?", (user_id,)).fetchone()
    if not row:
        raise HTTPException(404, "Usuario no encontrado")
    return public_user(row)


@app.delete("/api/admin/users/{user_id}")
def delete_user(user_id: int, request: Request, authorization: str | None = Header(default=None)):
    me = current_user(request, authorization)
    require_admin(me)
    if user_id == me["id"]:
        raise HTTPException(400, "No puedes borrar tu propio usuario")
    with db() as con:
        con.execute("DELETE FROM sessions WHERE user_id = ?", (user_id,))
        con.execute("DELETE FROM users WHERE id = ?", (user_id,))
    return {"ok": True}
