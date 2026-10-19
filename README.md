# Evolved Warfare — Minecraft 1.21.1 (Fabric)

NPCs, ejércitos, trabajadores y asedio. Inspirado en *Ancient Warfare*, con código, nombre y texturas propios.

Requiere **Fabric API**. Opcional: **Golden Coin Merchants** (los reclutas aceptan Golden Coins como paga y hay una receta de contrato con monedas).

## Hoja de ruta

| Fase | Contenido | Estado |
|---|---|---|
| 1 | **NPC base (el recluta)** y dónde vive: ciudades, campamentos y torres generados; contrato, dueño, equipo, órdenes, paga diaria. Aldeas vanilla con empedrado, farolas y empalizada | ✅ Hecha |
| 2 | **Soldados y bandidos**: guerrero, bárbaro, arquero, ballestero, médico; bandidos hostiles en fuertes, campamentos, patrullas y asaltos; niveles y bastón de mando | ✅ Hecha |
| 3 | Trabajadores y zonas de trabajo: granja, tala, mina, animales, pesca; ayuntamiento | ⏳ |
| 4 | Logística y comercio: almacén, mensajero con rutas, comerciante (Golden Coin) | ⏳ |
| 5 | Más facciones enemigas (vikingos, piratas), reputación y diplomacia | ⏳ |
| 6 | Estructuras: escáner y constructor de planos; más tipos de ciudad (desierto, nieve) y fortalezas | ⏳ |
| 7 | Asedio: catapulta, balista, trebuchet, ariete; puente levadizo y rastrillo | ⏳ |
| 8 | Armas medievales, árbol de investigación, equilibrio y configuración | ⏳ |

## Fase 1 — El recluta

### Dónde encontrarlos
Los NPCs **no se fabrican ni aparecen solos**: solo viven en las estructuras que el mod genera por el mundo.
Cada uno tiene nombre y aspecto al azar (6 aspectos) y pasea cerca de su sitio esperando a que alguien lo contrate.

| Estructura | Dónde | Qué tiene |
|---|---|---|
| **Ciudad medieval** | Llanuras, praderas, sabanas, bosques, taiga y llanuras nevadas | Plaza (fuente o patio de armas), calles empedradas con farolas y puertas de entrada, casas de entramado, casas de dos plantas, cuartel, taberna, herrería, torres de guardia, puestos de mercado y huertos. Se genera por piezas, así que cada ciudad es distinta: unos 40 edificios y una docena de reclutas de media. |
| **Campamento de mercenarios** | Bosques, taigas, llanuras, praderas y sabanas | Hoguera, tiendas de lona, armero y suministros. 2-6 reclutas. |
| **Torre de vigilancia** | Llanuras, bosques, taigas, colinas y costas rocosas | Torre de piedra con parapeto. 1-2 reclutas (uno arriba vigilando). |

Para encontrarlas rápido (con trucos activados): `/locate structure evolvedwarfare:city`, `evolvedwarfare:mercenary_camp` o `evolvedwarfare:watchtower`.

Los cofres del cuartel, la herrería, las torres y el campamento tienen botín de aldea (armas, armaduras, herramientas).

### Aldeas vanilla mejoradas
Las aldeas normales de Minecraft también cambian (solo las que se generen después de instalar el mod):
- **Calles empedradas**: la tierra de los caminos pasa a ser empedrado (adoquín con algo de andesita, grava y musgo). Las del desierto ya eran de arenisca y se quedan así.
- **Farolas** en el borde de las calles, cada 8-10 bloques, nunca delante de una puerta ni en un cruce: de noche se ve bien y no aparecen monstruos en las calles.
- **Empalizada de troncos** alrededor de toda la aldea, a unos 4 bloques de las casas, con faroles encima. La madera depende del tipo de aldea: roble (llanuras), acacia (sabana), abeto (taiga y nieve) y jungla (desierto).
- **Puertas**: normalmente 4, donde una calle llega cerca de la empalizada. Tienen postes altos con faroles y un camino empedrado que sale de la calle y atraviesa la puerta.

### Contratar
**Contrato de reclutamiento** (receta sin forma): papel + saco de tinta + pluma + lingote de oro + pan.
Con *Golden Coin Merchants* instalado también: papel + saco de tinta + 2 Golden Coins.

Clic derecho con el contrato sobre un recluta **libre** → firma, te sigue y te sirve **solo a ti**.
Sin contrato en la mano, te dice que busca trabajo. Mientras está libre no cobra.

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

## Fase 2 — Soldados y bandidos

### Clases
Las mismas cinco clases para tus soldados y para los bandidos (más el capitán, solo bandido):

| Clase | Arma | Cómo lucha |
|---|---|---|
| **Guerrero** | Espada (y escudo) | Primera línea. Con escudo para parte de los golpes que le llegan de frente. |
| **Bárbaro** | Hacha | Golpea muy fuerte, pero más despacio. |
| **Arquero** | Arco | Dispara desde unos 15 bloques, se mueve de lado y se aparta si te acercas. |
| **Ballestero** | Ballesta | Carga despacio, apunta y dispara un virote que hace mucho daño. |
| **Médico** | Botiquín | No ataca: busca al aliado más herido (NPCs y su dueño) y lo cura. |
| **Capitán** | Espada, escudo y armadura de hierro | Jefe de los fuertes de bandidos: 40 de vida. |

### Tus soldados
1. Contrata un recluta.
2. Ponle el arma en la mano principal (desde su menú).
3. Pulsa **Entrenar**: se convierte en soldado de esa clase y conserva nombre, aspecto, equipo y paga.

Sin fuego amigo: tus soldados no te hieren ni se hieren entre ellos (ni con flechas). Defienden a su dueño, atacan a quien ataque su dueño y a los monstruos y bandidos cercanos, sin alejarse de su zona: unos 20 bloques de ti si te siguen, 12 de su puesto si están quietos, 16 de su casa. Después vuelven a su sitio. Los de cuerpo a cuerpo no van a por creepers.

**Niveles**: suben con las bajas (monstruo 1, bandido 3, capitán 10; los médicos ganan 1 por cada curación). Hay 5 niveles, y cada uno da +2 de vida y +0,5 de daño cuerpo a cuerpo (+10 % de daño de flecha). El menú muestra la clase, el nivel, las bajas y la experiencia.

**Bastón de mando** (lingote de oro + lana roja + 2 palos en diagonal):
- Clic derecho al aire: todos tus NPCs a menos de 32 bloques te siguen. Con Mayús: se quedan quietos.
- Clic derecho en un bloque: van allí y lo vigilan.
- Clic derecho en un enemigo: tus soldados lo atacan.

**Botiquín** (receta sin forma: cuero + lana blanca + tinte rojo + rodaja de sandía reluciente): para entrenar médicos.

### Bandidos
Atacan a cualquier jugador, a tus reclutas y soldados y a los gólems de hierro. No aparecen en pacífico.
- **Campamentos de bandidos**: tiendas negras, hoguera y botín, con 1-6 bandidos. A veces tienen una jaula con un **prisionero**: abre la puerta y contrátalo.
- **Fuertes de bandidos**: empalizada, 4 torres con arqueros o ballesteros, sala del capitán con dos cofres de buen botín, calabozo con un prisionero y unos 10 bandidos.
- **Patrullas**: de vez en cuando (desde el día 2) aparece un grupo de 3-6 bandidos cerca de un jugador y va a por él.
- **Asaltos**: cada 3-5 días de juego, si estás dentro de una aldea o de una ciudad del mod, suena un cuerno y llega un grupo de 4-12 bandidos (con capitán en difícil). Atacan también a los aldeanos. Una barra muestra cuántos quedan; si en 10 minutos no los derrotas, se retiran.

Sueltan pepitas de oro, flechas, pan y, a veces, esmeraldas; el capitán suelta esmeraldas, hierro y a veces una manzana dorada.

Para encontrarlos: `/locate structure evolvedwarfare:bandit_camp` o `evolvedwarfare:bandit_fort`. En creativo hay un **huevo de bandido** (clase al azar).

## Herramientas (`tools/`)
Las estructuras se generan con scripts de Python (no hace falta tocarlos para jugar):
- `ew_structures.py` — diseña cada edificio bloque a bloque y escribe los `.nbt` y los JSON de generación.
- `ew_jigsaw_sim.py` — simula cómo el juego monta las ciudades (encaje de piezas, tamaño, nº de NPCs).
- `ew_preview.py` — dibuja vistas previas isométricas.
- `ew_textures.py` — genera las texturas (aspectos de los reclutas, contrato, interfaz).
- `ew_village.py` — prototipo de las aldeas vanilla mejoradas (mismo algoritmo que `VillageExtras.java`) con vista previa.

```
python3 tools/ew_structures.py src/main/resources/data/evolvedwarfare
python3 tools/ew_jigsaw_sim.py city 60
```

## Compilar
Cada vez que se suben cambios, GitHub Actions compila el mod (pestaña **Actions** → artefacto `evolved-warfare`).
En un PC: `./gradlew build` (Java 21+). Para probar: `./gradlew runClient`.
