# Changelog

## [1.0.2] - 2026-03-07
### Fixed
- Corregido bug al recortar por segunda vez: ahora el recorte usa siempre el `videoUri` actual en memoria y no el URI inicial del `Intent`.
- Corregido flujo de URI de salida tras FFmpeg para usar `Uri.fromFile(...)`, evitando inconsistencias al volver a recortar sobre el archivo recién generado.
- Mejorado el manejo de ruta de entrada para recorte/crop con resolución directa desde el URI actual (`content://` y `file://`).

## [1.0.1] - 2026-03-07
### Added
- Se muestra la pantalla de carga al ejecutar comandos FFmpeg, incluyendo el recorte de video, para informar que el render está en progreso.

### Changed
- Al finalizar correctamente un recorte/render, se actualiza el archivo fuente temporal hacia el nuevo video generado para refrescar correctamente reproductor y frames.
