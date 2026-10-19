# Evolved Warfare — Minecraft 1.21.1 (Fabric)

NPCs, ejércitos, trabajadores y asedio. Inspirado en *Ancient Warfare*, con código, nombre y texturas propios.

Requiere **Fabric API**. Opcional: **Golden Coin Merchants** (los reclutas aceptan Golden Coins como paga y hay una receta de contrato con monedas).

## Hoja de ruta

| Fase | Contenido | Estado |
|---|---|---|
| 1 | **NPC base (el recluta)**: contrato, dueño, equipo, órdenes (seguir / quieto / casa), paga diaria | ✅ Hecha |
| 2 | Soldados: cuerpo a cuerpo, arquero y médico; IA de combate, patrullas, niveles, bastón de mando | ⏳ |
| 3 | Trabajadores y zonas de trabajo: granja, tala, mina, animales, pesca; ayuntamiento | ⏳ |
| 4 | Logística y comercio: almacén, mensajero con rutas, comerciante (Golden Coin) | ⏳ |
| 5 | Facciones enemigas: bandidos, vikingos, piratas; campamentos, reputación, asaltos | ⏳ |
| 6 | Estructuras: escáner y constructor de planos; fuertes y torres generados en el mundo | ⏳ |
| 7 | Asedio: catapulta, balista, trebuchet, ariete; puente levadizo y rastrillo | ⏳ |
| 8 | Armas medievales, árbol de investigación, equilibrio y configuración | ⏳ |

## Fase 1 — El recluta

### Contratar
**Contrato de reclutamiento** (receta sin forma): papel + saco de tinta + pluma + lingote de oro + pan.
Con *Golden Coin Merchants* instalado también: papel + saco de tinta + 2 Golden Coins.

Clic derecho con el contrato sobre el suelo → aparece un recluta con nombre y aspecto al azar (6 aspectos) que te sirve **solo a ti**.

### Órdenes y menú
Clic derecho sobre tu recluta abre su menú:
- **Equipo**: casco, pechera, grebas, botas, mano principal y segunda mano. Lo que le pongas se ve puesto y lo suelta entero si muere.
- **Provisiones**: 2 ranuras para su paga.
- **Botones**: *Seguir* (te sigue; si se queda a más de 24 bloques aparece a tu lado), *Quieto*, *Ir a casa* y *Casa aquí* (fija su casa donde está y se queda por allí paseando).
- Muestra su vida, la orden actual y para cuántos días tiene paga.

Atajo: clic derecho con comida (o Golden Coins) en la mano → se la guarda como provisiones sin abrir el menú.

### Paga
- Cada día de juego consume **1 provisión**: pan, patata asada, carne o pescado cocinados, zanahoria dorada, tarta de calabaza o Golden Coin.
- Si un día no cobra, te avisa por el chat. Tras **3 días sin paga deserta**: deja su equipo en el suelo y se va.
- Bien pagado, se cura poco a poco.
- Su dueño no puede herirlo por accidente. Si muere, te avisa y su equipo queda en el suelo.

## Compilar
Cada vez que se suben cambios, GitHub Actions compila el mod (pestaña **Actions** → artefacto `evolved-warfare`).
En un PC: `./gradlew build` (Java 21+). Para probar: `./gradlew runClient`.
