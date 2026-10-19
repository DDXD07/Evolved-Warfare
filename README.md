# Evolved Warfare — Minecraft 1.21.1 (Fabric)

NPCs, ejércitos, trabajadores y asedio. Inspirado en *Ancient Warfare*, con código, nombre y texturas propios.

Requiere **Fabric API**. Opcional: **Golden Coin Merchants** (los reclutas aceptan Golden Coins como paga y hay una receta de contrato con monedas).

## Hoja de ruta

| Fase | Contenido | Estado |
|---|---|---|
| 1 | **NPC base (el recluta)** y dónde vive: ciudades, campamentos y torres generados; contrato, dueño, equipo, órdenes, paga diaria. Aldeas vanilla con empedrado, farolas y empalizada | ✅ Hecha |
| 2 | **Soldados y bandidos**: guerrero, bárbaro, arquero, ballestero, médico; bandidos hostiles en fuertes, campamentos, patrullas y asaltos; niveles y bastón de mando | ✅ Hecha |
| 3 | **Trabajadores**: 14 oficios con su puesto de trabajo (granja, tala, mina, excavación, pesca, ganado, corral, colmenas, huerto, forja, cocina, carbonera, cantería, flechas) y ayuntamiento que paga solo | ✅ Hecha |
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
- Clic derecho al aire: todos tus NPCs a menos de 32 bloques te siguen (menos los trabajadores, que siguen en su puesto). Con Mayús: se quedan quietos.
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

## Fase 3 — Trabajadores

### Cómo poner a alguien a trabajar
1. Fabrica el **puesto** del oficio y ponlo donde quieras que trabaje (el dibujo del frente mira hacia ti).
2. Ten cerca (a menos de 24 bloques) un **recluta** tuyo.
3. Abre el puesto (clic derecho) y pulsa **Asignar recluta**: tu recluta más cercano pasa a ser trabajador de ese oficio, con su nombre, equipo y paga.
4. Dale su **herramienta** en la mano (desde su menú) y lo que gaste en la fila de **suministros** del puesto. Lo que produce va a las dos filas de **producción**.

Trabajan de día dentro de la zona de su puesto y de noche descansan junto a él. Huyen de monstruos y bandidos. Si no cobran, hacen huelga. **Ver zona** marca el borde de la zona con partículas. **Despedir** lo convierte otra vez en recluta (y te sigue). Si rompes el puesto, su trabajador vuelve a ser recluta y lo que había dentro cae al suelo.

Con la herramienta de su oficio trabajan más rápido (más cuanto mejor sea: madera < piedra < hierro < diamante < netherita < oro; Fortuna y Toque de seda funcionan) y gastan su durabilidad; sin ella van a la mitad. Si se les rompe y hay otra en los suministros, la cogen solos. El menú del puesto muestra quién trabaja, cuántas tareas lleva y qué está haciendo (o qué le falta: suministros, combustible, agua, sitio en el almacén...). El botón **[?]** explica el oficio.

Las **tolvas** meten cosas en los suministros por arriba y los lados, y sacan la producción por abajo, así que puedes encadenar puestos con cofres.

### Los 14 oficios
Receta de todos los puestos: 7 tablones alrededor, un cofre abajo y el objeto del oficio en el centro.

| Oficio | Puesto (objeto central) | Zona | Qué hace | Qué necesita |
|---|---|---|---|---|
| **Granjero** | Azada | 11×11 | Ara la tierra con agua a 4 bloques (como en vanilla), siembra, cosecha y resiembra | Semillas, patatas, zanahorias o remolacha. Harina de hueso opcional |
| **Leñador** | Hacha | 15×15 | Tala árboles enteros (solo árboles naturales, nunca casas de troncos), replanta en el tocón, recoge brotes, palos y manzanas y planta brotes en cuadrícula | Brotes. Harina de hueso opcional |
| **Minero** | Pico | Pozo 5×5 bajo tierra | Cava una entrada de 5 escalones detrás del puesto y, desde el rellano del fondo, un pozo bajo tierra hasta la roca madre (la superficie queda intacta). No toca cofres ni bloques junto a lava. Las menas que su pico no puede romper las deja | Un pico. Antorchas opcionales |
| **Excavador** | Pala | Pozo 7×7 | Abre un pozo detrás del puesto (dejando un pasillo de un bloque) y saca tierra, arena, grava, arcilla, barro... hasta 16 capas | Nada (con pala, mucho más rápido) |
| **Pescador** | Caña | 13×13 | Pesca en el agua cercana (al menos 4 bloques de agua) | Nada (con caña, más rápido) |
| **Pastor** | Tijeras | 17×17 | Esquila ovejas, ordeña vacas y cabras, recoge la lana, cría y mantiene el rebaño en 8 adultos por especie | Trigo, cubos, tijeras |
| **Criador** | Bala de heno | 17×17 | Gallinas, cerdos y conejos: los cría, recoge huevos y plumas y mantiene 8 adultos por especie | Semillas, zanahorias, patatas o dientes de león |
| **Apicultor** | Panal | 17×17 | Vacía colmenas y nidos llenos sin enfadar a las abejas y planta flores | Frascos de cristal (o tijeras para panales). Flores opcionales |
| **Hortelano** | Harina de hueso | 13×13 | Melones y calabazas de tallo, caña, cactus, bambú, bayas dulces y luminosas, cacao y verrugas del Nether (replanta estas dos) | Nada |
| **Herrero** | Alto horno | — | Funde menas y metal en bruto (recetas de alto horno) | Menas y combustible |
| **Cocinero** | Ahumador | — | Cocina carne, pescado y patatas (recetas de ahumador) y hornea pan con 3 de trigo | Comida cruda o trigo, y combustible |
| **Carbonero** | Horno | — | 5 troncos → 4 de carbón vegetal, sin combustible | Troncos |
| **Cantero** | Cortapiedras | — | Roca y piedra → ladrillos de piedra; andesita, diorita y granito pulidos; arenisca; ladrillos de pizarra, piedra negra y toba | Esos bloques (con pico, más rápido) |
| **Flechero** | Mesa de flechería | — | Pedernal + palo + pluma → 4 flechas; grava → pedernal | Esos materiales |

El pastor y el criador nunca sacrifican animales con nombre ni atados.

**La mina bajo tierra** (minero): detrás del puesto deja un pasillo de un bloque y cava una escalera de 5 escalones (3 de alto, se puede bajar andando) que acaba en un rellano a 5 bloques de profundidad. Desde ahí vacía el pozo, que empieza bajo tierra con 3 bloques de techo, así que en la superficie solo se ve la boca de la escalera. Si le das antorchas, pone una en la escalera y otra en el rellano. **Ver zona** marca en la superficie dónde irá el pozo y por dónde baja la escalera.

### Ayuntamiento
Receta: lingote de oro, libro y lingote de oro arriba; tablón, cofre y tablón en medio; 3 tablones abajo. Guarda comida o Golden Coins dentro y cada pocos segundos rellena las provisiones de todos tus NPCs (trabajadores, soldados y reclutas) a menos de 48 bloques, hasta 3 días de paga. Así no tienes que ir dándoles de comer uno a uno.

## Herramientas (`tools/`)
Las estructuras se generan con scripts de Python (no hace falta tocarlos para jugar):
- `ew_structures.py` — diseña cada edificio bloque a bloque y escribe los `.nbt` y los JSON de generación.
- `ew_jigsaw_sim.py` — simula cómo el juego monta las ciudades (encaje de piezas, tamaño, nº de NPCs).
- `ew_preview.py` — dibuja vistas previas isométricas.
- `ew_textures.py` — genera las texturas (aspectos de los reclutas, contrato, interfaz).
- `ew_blocks.py` — texturas, modelos, recetas y botín de los puestos de trabajo y el ayuntamiento.
- `ew_village.py` — prototipo de las aldeas vanilla mejoradas (mismo algoritmo que `VillageExtras.java`) con vista previa.

```
python3 tools/ew_structures.py src/main/resources/data/evolvedwarfare
python3 tools/ew_jigsaw_sim.py city 60
```

## Compilar
Cada vez que se suben cambios, GitHub Actions compila el mod (pestaña **Actions** → artefacto `evolved-warfare`).
En un PC: `./gradlew build` (Java 21+). Para probar: `./gradlew runClient`.
