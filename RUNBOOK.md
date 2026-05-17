# CuarzoPolar — Password Android Runbook

## Deploy

### Build local

1. Abrir este directorio en Android Studio.
2. Comprobar que `local.properties` apunta al Android SDK correcto.
3. Sincronizar Gradle.
4. Ejecutar o generar la build para el dispositivo del show.

## Arranque

- La app está pensada para ejecutarse en el teléfono del show.
- Debe tener disponible el túnel `adb reverse tcp:8767 tcp:8767`.
- La app conecta automáticamente a `localhost:8767` al arrancar.
- El túnel ADB puede prepararlo `password_qt` o el orchestrator.

## Manejo de la aplicación

1. Introducir la contraseña en los dos campos.
2. Confirmar y enviar.
3. Esperar mientras el módulo Qt procesa.
4. Revisar el resultado mostrado:
   - rojo si la contraseña ha sido comprometida
   - verde si se considera segura

## Consideraciones operativas

- Esta app depende de `password_qt` para el procesamiento y el veredicto.
- Sin túnel ADB reverse o sin el servidor Qt disponible, la conexión no funcionará.
- El puerto operativo esperado es `8767`.
