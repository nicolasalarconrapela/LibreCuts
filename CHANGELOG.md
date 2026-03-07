# Changelog

## [1.0.14] - 2026-03-07
### Added
- En Home ahora se listan los proyectos guardados detectados en `files/projects`.
- Al pulsar un proyecto guardado en Home, se abre directamente en el editor.

### Changed
- Cuando no hay proyectos guardados, Home muestra un mensaje contextual indicando que no hay proyectos todavía.

## [1.0.13] - 2026-03-07
### Fixed
- Corregido guardado de proyectos: al elegir guardar al salir, ahora se crea un snapshot local del proyecto (`files/projects/autosave_project.mp4`) para restauración confiable.
- Restauración del proyecto ahora prioriza el snapshot local; si existe y es válido, se carga desde ahí.
- Al descartar proyecto se elimina también el snapshot local para evitar estados stale.

## [1.0.12] - 2026-03-07
### Added
- Al salir del editor ahora se muestra confirmación para **guardar o no guardar** el proyecto.
- Si el usuario elige "No guardar", se limpia el estado cacheado del proyecto para evitar restauraciones no deseadas.

### Changed
- El auto-guardado en `onPause/onDestroy` ahora respeta la decisión del usuario al salir.

## [1.0.11] - 2026-03-07
### Fixed
- Corregido guardado/exportación para evitar falso positivo: ahora se valida que el archivo exista y tenga tamaño mayor a 0 antes de mostrar éxito.
- Tras exportar correctamente, se ejecuta `MediaScannerConnection.scanFile(...)` para que el video aparezca en Descargas/Galería.

## [1.0.10] - 2026-03-07
### Added
- Se agregaron botones de **Deshacer** y **Rehacer** en el editor para navegar el historial de ediciones.
- Se añadió historial en memoria de cambios de video generado por operaciones FFmpeg (trim/crop/text/merge) con actualización automática de estado de botones.

## [1.0.9] - 2026-03-07
### Fixed
- Corregido `CustomVideoSeeker` para evitar doble callback de seek que provocaba saltos de tiempo erráticos.
- Restauración automática: la posición/zoom guardados ahora se aplican solo una vez por sesión restaurada, evitando reposicionamientos inesperados posteriores.
- Protección en `onDestroy` para no intentar liberar el reproductor si aún no fue inicializado.

## [1.0.8] - 2026-03-07
### Added
- Guardado automático del proyecto (video actual editado, posición de reproducción y nivel de zoom) usando `SharedPreferences`.
- Restauración automática del último proyecto guardado al abrir nuevamente el editor, si el archivo sigue disponible.

### Changed
- Tras operaciones FFmpeg exitosas, el estado del proyecto se persiste automáticamente para continuar edición sin perder contexto.

## [1.0.7] - 2026-03-07
### Fixed
- La exportación/guardado ahora conserva explícitamente la calidad original usando stream copy de video/audio/subtítulos (`-map 0 -c:v copy -c:a copy -c:s copy`), evitando recodificación al guardar.

## [1.0.6] - 2026-03-07
### Added
- Zoom en preview de video mediante gesto de pinza (1x a 4x) y botón para restablecer zoom.
- Captura de frame en calidad original (sin escalado) desde la posición actual del reproductor.
- Nuevos controles en la tira de previsualización para capturar frame y resetear zoom.

## [1.0.5] - 2026-03-07
### Added
- Se agregaron botones para avanzar frame a frame (anterior/siguiente) en la pantalla de edición.
- Los botones mueven el playhead por frame usando el frame rate del video cuando está disponible (fallback a ~33ms).

## [1.0.4] - 2026-03-07
### Added
- Se implementó el botón de descarga/guardado para exportar el video actual a la carpeta Descargas.
- Al guardar, se muestra un modal de progreso con porcentaje de 1% a 100% durante el procesamiento.

### Fixed
- El botón de descarga dejó de ser una acción vacía y ahora ejecuta una exportación real con FFmpeg.

## [1.0.3] - 2026-03-07
### Changed
- La funcionalidad de `Trim` ahora usa controles inline sobre la tira de previsualización (RangeSlider + acciones aplicar/cancelar), eliminando el modal para recortar.
- El botón de Trim ahora alterna mostrar/ocultar los controles de recorte en la misma pantalla de edición.

### Fixed
- Se mantiene el recorte sobre el video actual cargado, permitiendo recortes consecutivos sin volver al flujo inicial.

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
