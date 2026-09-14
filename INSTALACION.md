# Mora TV — datos de instalación y publicación

## Nombres
- App (visible): **Mora TV**
- Organización GitHub: **moratv-app**
- Repositorio: **mora-tv**
- Paquete interno (no visible): `com.miplayer.tv`

## Enlaces
- Repositorio: https://github.com/moratv-app/mora-tv
- Última versión (siempre la más reciente): https://github.com/moratv-app/mora-tv/releases/latest/download/mora-tv.apk

## Instalar en el MÓVIL
Descargar el APK desde el enlace de la última versión y abrirlo. Permitir "instalar
apps de origen desconocido" la primera vez. Después la app se actualiza sola.

## Instalar en la TELE (Android TV / Fire TV)
1. Instalar la app **Downloader** desde la tienda de la tele.
2. Escribir el código de Downloader (ver abajo) o el enlace completo de arriba.
3. Descargar e instalar. Después la app se actualiza sola.

### Código de Downloader
- Creado en https://go.aftvnews.com apuntando al enlace "última versión".
- CÓDIGO: **2307067**  (enlace corto: aftv.news/2307067)

## Publicar una versión nueva (desde la máquina de desarrollo)
```bash
cd /home/nebulabsai/proyectos/iptv-nestor/app-android
./gradlew assembleDebug --no-daemon
cp app/build/outputs/apk/debug/app-debug.apk /tmp/mora-tv.apk
gh release create vN /tmp/mora-tv.apk --repo moratv-app/mora-tv \
  --title "Mora TV X.Y" --notes "novedades"
```
- `vN` = el número de versionCode de esa build (v16, v17, ...). Debe subir siempre.
- El archivo del release SIEMPRE se llama `mora-tv.apk` (para que el enlace no cambie).
- Al abrir la app, si hay una versión con número mayor que la instalada, sale el aviso.

## Estado de versiones
- versionName actual: 2.5  ·  versionCode: 16  ·  primera release sugerida: v16
