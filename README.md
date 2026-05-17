# CuarzoPolar — Password Android

Aplicación Android del módulo teatral `password`. Se ejecuta en el teléfono del show y actúa como falsa app segura donde el hook crea contraseñas durante la actuación.

## Qué es

La app forma pareja con `password_qt`. Su papel es capturar una contraseña en el móvil, enviarla al módulo Qt y mostrar el resultado teatral del ataque:

- contraseña comprometida, revelada en rojo
- contraseña segura, confirmada en verde

## Flujo funcional

1. El hook introduce una contraseña en la app.
2. La app la envía al módulo Qt.
3. `password_qt` ejecuta la simulación teatral de ataque.
4. `password_qt` devuelve un veredicto.
5. La app muestra el resultado.

## Pantallas

| Pantalla | Descripción |
|---|---|
| Formulario | Dos campos de contraseña, confirmación y envío |
| Espera | Animación mientras Qt procesa |
| Resultado | Estado seguro o comprometido |

## Arquitectura y comunicación

- Aplicación Android nativa.
- Cliente WebSocket hacia `password_qt`.
- Comunicación pensada para funcionar sobre ADB reverse.
- La app conecta a `localhost:8767`.

Protocolo:

**Android -> Qt**

```json
{"type": "password", "value": "the-password"}
```

**Qt -> Android**

```json
{"type": "verdict", "cracked": true,  "password": "the-password"}
{"type": "verdict", "cracked": false}
```

## Tecnología

| Capa | Tecnología |
|---|---|
| Plataforma | Android |
| Comunicación | WebSocket |
| Cliente WS | OkHttp 4.12.0 |
| UI | AndroidX AppCompat, ConstraintLayout, Material |
| Min SDK | 26 |

## Estado actual

- Emparejada con `password_qt`.
- Conexión prevista por `localhost:8767`.
- Flujo principal de formulario, espera y resultado definido.
