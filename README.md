# Corrector físico

Teclado para Android (IME) con corrector ortográfico en español, pensado para escribir con **teclado físico Bluetooth** en tablets donde Gboard no corrige (p. ej. Redmi Pad 2 con HyperOS).

## Qué hace

- Deja pasar todas las teclas del teclado físico tal cual.
- Al apretar **espacio**, Enter o un signo de puntuación, revisa la última palabra:
  - Repone tildes: `medico` → `médico`, `aplicacion` → `aplicación`.
  - Corrige errores de una letra: `vinlo` → `vinilo`.
- **Deshacer:** Retroceso una vez, inmediatamente después de una corrección, restaura la palabra original.
- No corrige en campos de contraseña, mail ni direcciones web.
- No toca palabras con mayúscula inicial desconocidas (marcas y nombres propios quedan en paz).
- Diccionario: 49.525 palabras en español con frecuencia de uso (corpus OpenSubtitles, proyecto FrequencyWords, licencia CC-BY).

## Cómo se compila

No hace falta PC: al subir el código a GitHub, **GitHub Actions compila solo** y deja el archivo instalable en dos lugares:

1. **Releases** del repositorio → `corrector-fisico.apk` (descarga directa, lo más cómodo desde la tablet).
2. Pestaña **Actions** → la corrida → "Artifacts" (baja un .zip con el .apk adentro).

## Instalación en la tablet

1. Descargar `corrector-fisico.apk` desde Releases.
2. Abrirlo; Android pide permiso para instalar apps de origen desconocido → permitir para Chrome o el administrador de archivos.
3. Abrir la app **Corrector físico** y seguir los dos botones: activar el teclado y elegirlo.

## Estructura

- `app/src/main/java/ar/trainingwraps/corrector/CorrectorIME.kt` — el servicio de teclado.
- `app/src/main/java/ar/trainingwraps/corrector/Corrector.kt` — el motor de corrección.
- `app/src/main/assets/es_words.txt` — el diccionario.
- `.github/workflows/build.yml` — el compilado automático.
