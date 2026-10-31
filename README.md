# Evolved Warfare — Minecraft 1.21.1 (Fabric)

NPCs, ejércitos, trabajadores, comercio, facciones con diplomacia, asedio, armas medievales y un árbol de habilidades al estilo *Skyrim*. Inspirado en *Ancient Warfare*, con código, nombre y texturas propios.

Requiere **Fabric API**. Opcional: **Mod Menu** (pantalla de configuración).

**Guía**: la primera vez que entras en un mundo te dan la **Guía de Evolved Warfare** (clic derecho para leerla). Es
un libro con cuatro secciones cortas, con una cinta de color para cada una: **Ciudades y comercio**, **Tu gente**,
**Facciones y guerra** y **Habilidades**. Cada página tiene tres cosas en una o dos frases, con su icono. Se cambia de
sección con las cintas, las flechas o las teclas 1-4. Si la pierdes, se fabrica otra con un libro y un papel.

**Moneda: la Glory Coin.** El mod tiene su propia moneda. No se fabrica: se gana vendiendo a los comerciantes de las ciudades y al mercado, y sale en los cofres de campamentos, fuertes, asentamientos y castillos y en el botín de bandidos, vikingos y piratas. Con ella se paga a los NPCs, se compra en las tiendas, se pagan tributos y treguas y se hace el contrato de reclutamiento. Como un montón de monedas solo llega a 64, la **Bolsa de Gloria** (cordel arriba, cuero, Glory Coin y cuero en medio, 3 de cuero abajo) guarda todas las que quieras en un hueco: clic derecho mete las monedas sueltas, Mayús + clic derecho saca 64, y en el inventario se meten y se sacan con clic derecho como en un saco. Las tiendas cobran de la bolsa y pagan en ella (si no llevas ninguna y te pagan más de 64, te dan una). (Ya no usa la Golden Coin del mod *Golden Coin Merchants* ni esmeraldas como moneda: si tenías ese mod, los dos pueden convivir, pero no se mezclan.)

## Hoja de ruta

| Fase | Contenido | Estado |
|---|---|---|
| 1 | **NPC base (el recluta)** y dónde vive: ciudades, campamentos y torres generados; contrato, dueño, equipo, órdenes, paga diaria. Aldeas vanilla con empedrado, farolas y empalizada | ✅ Hecha |
| 2 | **Soldados y bandidos**: guerrero, bárbaro, arquero, ballestero, médico; bandidos hostiles en fuertes, campamentos, patrullas y asaltos; niveles y bastón de mando | ✅ Hecha |
| 3 | **Trabajadores**: 14 oficios con su puesto de trabajo (granja, tala, mina, excavación, pesca, ganado, corral, colmenas, huerto, forja, cocina, carbonera, cantería, flechas) y ayuntamiento que paga solo | ✅ Hecha |
| 4 | **Logística y comercio**: almacén en red con buscador, mensajero (automático o con hoja de ruta), comerciante con tienda para jugadores y venta de excedentes (Glory Coins) | ✅ Hecha |
| 5 | **Facciones y diplomacia**: vikingos y piratas con sus asentamientos, patrullas y asaltos (también a tu pueblo); reputación con cada facción, bandera blanca, tributos, treguas, alianzas, ayuda de aliados y mercenarios | ✅ Hecha |
| 6 | **Estructuras**: escáner de estructuras, planos, mesa del arquitecto con biblioteca y constructor que los levanta; ciudades del desierto y del norte; castillos y fortalezas del caudillo bandido | ✅ Hecha |
| 7 | **Asedio**: catapulta, balista, trebuchet y ariete (para ti, para tus soldados y en los asaltos enemigos); rastrillo y puente levadizo con torno | ✅ Hecha |
| 8 | **Armas medievales** (daga, lanza, mandoble, alabarda, martillo de guerra, mangual, hacha de batalla, arco largo y jabalina), **árbol de habilidades** al estilo *Skyrim* (11 habilidades que suben usándolas, 79 ventajas), **equilibrio** y **configuración** (archivo, comando y pantalla con Mod Menu) | ✅ Hecha |
| 9 | **Comerciantes y niveles de las ciudades**: ciudades de nivel 1 a 10 según la distancia; tendero, armero, cantero, joyero, sastre, mercader de especias y peletero con tienda de catálogo completo por Glory Coins (Compra y Venta, al menos 20 de cada cosa al día); más nivel, mejor calidad (de gastado a obra maestra encantada) y más caras | ✅ Hecha |
| 10 | **Armas de fuego primitivas**: mosquete de chispa (modelo 3D animado, recarga y disparo con humo), balas de mosquete, bolsa de munición (1000 balas) y **daño locacional** (cabeza, torso, brazos y piernas) | 🔨 En curso |
| 11 | **Hordas semanales**: contador de días y reloj en pantalla; cada 7 días una horda de bandidos, vikingos o piratas ataca tu base, en oleadas y cada semana más dura | ✅ Hecha |
| 12 | **Armaduras de las facciones**: 4 por facción (por rango), 12 en total, con modelo 3D; las llevan sus guerreros, se fabrican con ventajas de forja, salen en sus cofres y las vende el armero | ✅ Hecha |

## Fase 1 — El recluta

### Dónde encontrarlos
Los NPCs **no se fabrican ni aparecen solos**: solo viven en las estructuras que el mod genera por el mundo.
Cada uno tiene nombre y aspecto al azar (6 aspectos) y pasea cerca de su sitio esperando a que alguien lo contrate.

| Estructura | Dónde | Qué tiene |
|---|---|---|
| **Ciudad medieval** | Llanuras, praderas, sabanas, bosques, taiga y llanuras nevadas | Plaza (fuente o patio de armas), calles empedradas con farolas y puertas de entrada, casas de entramado, casas de dos plantas, cuartel, taberna, herrería, torres de guardia, puestos de mercado y huertos. Se genera por piezas, así que cada ciudad es distinta: unos 40 edificios y una docena de reclutas de media. |
| **Campamento de mercenarios** | Bosques, taigas, llanuras, praderas y sabanas | Hoguera, tiendas de lona, armero y suministros. 2-6 reclutas. |
| **Torre de vigilancia** | Llanuras, bosques, sabanas, taigas, colinas y costas rocosas | **Cada una distinta** (sale de la semilla del mundo): cuadrada, octogonal o redonda, de 7 a 11 de ancho y de 2 a 4 pisos; arriba almenas con bandera, hoguera de señales, mirador con campana o garita de entramado; a veces voladizo con matacanes, pináculos, contrafuertes, balcón, cuadra adosada, cerca con portón, farolas, pozo, leñera o dianas. Dentro: cuerpo de guardia, literas y armería o sala de mapas, con cofres. Piedra, mampostería, adobe, arenisca, pizarra o toba según el bioma (con nieve en las taigas nevadas). 1-2 reclutas (uno arriba vigilando). |

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
También: papel + saco de tinta + 2 Glory Coins.

Clic derecho con el contrato sobre un recluta **libre** → firma, te sigue y te sirve **solo a ti**.
Sin contrato en la mano, te dice que busca trabajo. Mientras está libre no cobra.

### Órdenes y menú
Clic derecho sobre tu recluta abre su menú:
- **Equipo**: casco, pechera, grebas, botas, mano principal y segunda mano. Lo que le pongas se ve puesto y lo suelta entero si muere.
- **Provisiones**: 2 ranuras para su paga.
- **Botones**: *Seguir* (te sigue; si se queda a más de 24 bloques aparece a tu lado), *Quieto*, *Ir a casa* y *Casa aquí* (fija su casa donde está y se queda por allí paseando).
- Muestra su vida, la orden actual y para cuántos días tiene paga.

Atajo: clic derecho con comida (o Glory Coins) en la mano → se la guarda como provisiones sin abrir el menú.

### Paga
- Cada día de juego consume **1 provisión**: pan, patata asada, carne o pescado cocinados, zanahoria dorada, tarta de calabaza o Glory Coin.
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

Sueltan pepitas de oro, flechas, pan, alguna Glory Coin y, a veces, esmeraldas; el capitán suelta esmeraldas, hierro y a veces una manzana dorada.

Para encontrarlos: `/locate structure evolvedwarfare:bandit_camp` o `evolvedwarfare:bandit_fort`. En creativo hay un **huevo de bandido** (clase al azar).

## Fase 3 — Trabajadores

### Cómo poner a alguien a trabajar
1. Fabrica el **puesto** del oficio y ponlo donde quieras (el dibujo del frente mira hacia ti). Se abre su **menú de edificio** (ver abajo): elige un diseño y pulsa **Construir**, o **Sin edificio**.
2. Ten cerca (a menos de 24 bloques) un **recluta** tuyo.
3. Abre el puesto (clic derecho) y pulsa **Asignar**: tu recluta más cercano pasa a ser trabajador de ese oficio, con su nombre, equipo y paga.
4. Pon en la fila de **suministros** los materiales del edificio; cuando lo termine, lo que gaste su oficio (semillas, combustible...). Dale su **herramienta** en la mano (desde su menú). Lo que produce va a las dos filas de **producción**.

La **zona de trabajo** está delante del puesto (el campo, el bosque, el cercado...) y el edificio detrás y a los lados, así que no se estorban. El minero y el excavador trabajan en un pozo detrás.

Trabajan de día dentro de la zona de su puesto y de noche descansan junto a él (dentro de su edificio, si lo tiene). Huyen de monstruos y bandidos. Si no cobran, hacen huelga. **Ver zona** marca el borde de la zona con partículas. **Despedir** lo convierte otra vez en recluta (y te sigue). Si rompes el puesto, su trabajador vuelve a ser recluta y lo que había dentro cae al suelo.

Con la herramienta de su oficio trabajan más rápido (más cuanto mejor sea: madera < piedra < hierro < diamante < netherita < oro; Fortuna y Toque de seda funcionan) y gastan su durabilidad; sin ella van a la mitad. Si se les rompe y hay otra en los suministros, la cogen solos. El menú del puesto muestra quién trabaja, cuántas tareas lleva y qué está haciendo (o qué le falta: suministros, combustible, agua, materiales de obra, sitio en el almacén...). El botón **[?]** explica el oficio.

Las **tolvas** meten cosas en los suministros por arriba y los lados, y sacan la producción por abajo, así que puedes encadenar puestos con cofres.

### El edificio de cada puesto
Al poner un puesto se abre su menú de edificio (luego, con el botón **Edificio** del puesto):
- **Vista previa en 3D** del diseño: se gira arrastrando con el ratón y se acerca con la rueda. El borde de color en el suelo marca la zona de trabajo.
- Cada diseño sale de una **semilla**: tamaño, madera, paredes (tablones, zócalo de piedra, troncos, entramado, piedra), tejado, ventanas, puerta y detalles. **Otra semilla** genera otro; **Anterior** vuelve atrás (guarda las últimas 32).
- **Materiales**: lo que necesita, por tipo. Cada bloque gasta uno de su tipo, de cualquier variante: escaleras, losas, vallas, puertas o barriles gastan un tablón (de cualquier madera); los troncos, un tronco; los ladrillos y escaleras de piedra, piedra (roca, piedra, ladrillos de piedra...); las ventanas, cristal o paneles; las antorchas y faroles, antorchas; la lana y las alfombras, lana; las pacas, heno. La madera y la piedra que se ven las decide la semilla, no lo que pagues.
- **Construir**: el trabajador lo levanta antes de ponerse con su oficio. Primero limpia el sitio de lo natural (tierra, piedra, hierba, árboles; lo que saca va a la producción), rellena con tierra los huecos bajo el suelo y pone los bloques de abajo arriba. Nunca rompe lo que no es natural (cosas tuyas): esas piezas se quedan sin poner. Si le falta un material, para y el puesto avisa («Falta: Cristal»).
- **Sin edificio**: trabaja sin él (puedes elegir uno más tarde). **Cancelar** deja la obra a medias.

| Oficio | Edificio |
|---|---|
| Granjero | Granja con pacas de heno y compostador |
| Leñador | Cabaña de troncos con leñera cubierta y tocón |
| Minero | Granero sobre la entrada de la mina, con portón y farol |
| Excavador | Cobertizo abierto (el pozo empieza detrás) |
| Pescador | Caseta con barriles y farol en un poste |
| Pastor | Establo y **cercado** alrededor de toda su zona (se entra por la casa; cancela al fondo para ti) |
| Criador | Gallinero con franja de color y **cercado** igual que el pastor |
| Apicultor | Colmenar con tejadillo y jardineras |
| Hortelano | Invernadero de cristal con bancales y compostador |
| Herrero | Forja de piedra con chimenea y hogar encendido |
| Cocinero | Cocina con chimenea y hogar |
| Carbonero | Tejadillo, horno de cúpula con hoguera y pila de troncos |
| Cantero | Taller de piedra con bloques apilados |
| Flechero | Taller con dianas de paja |
| Mensajero | Casa de postas con barriles, arcón y barandilla para atar caballos |
| Comerciante | Puesto de mercado con mostrador, toldo de lana a rayas y género |

### Los 14 oficios
Receta de todos los puestos: 7 tablones alrededor, un cofre abajo y el objeto del oficio en el centro.

| Oficio | Puesto (objeto central) | Zona | Qué hace | Qué necesita |
|---|---|---|---|---|
| **Granjero** | Azada | 11×11 delante | Ara la tierra con agua a 4 bloques (como en vanilla), siembra, cosecha y resiembra | Semillas, patatas, zanahorias o remolacha. Harina de hueso opcional |
| **Leñador** | Hacha | 15×15 delante | Tala árboles enteros (solo árboles naturales, nunca casas de troncos), replanta en el tocón, recoge brotes, palos y manzanas y planta brotes en cuadrícula | Brotes. Harina de hueso opcional |
| **Minero** | Pico | Pozo 5×5 bajo tierra | Cava una entrada de 5 escalones detrás del puesto (dentro de su granero) y, desde el rellano del fondo, vacía un pozo bajo tierra hasta la roca madre (la superficie queda intacta). No toca cofres ni bloques junto a lava. Las menas que su pico no puede romper las deja | Un pico. Antorchas opcionales |
| **Excavador** | Pala | Pozo 7×7 | Abre un pozo detrás del puesto (dejando un pasillo de un bloque) y saca tierra, arena, grava, arcilla, barro... hasta 16 capas | Nada (con pala, mucho más rápido) |
| **Pescador** | Caña | 13×13 delante | Pesca en el agua cercana (al menos 4 bloques de agua) | Nada (con caña, más rápido) |
| **Pastor** | Tijeras | 17×17 delante | Esquila ovejas, ordeña vacas y cabras, recoge la lana, cría y mantiene el rebaño en 8 adultos por especie | Trigo, cubos, tijeras |
| **Criador** | Bala de heno | 17×17 delante | Gallinas, cerdos y conejos: los cría, recoge huevos y plumas y mantiene 8 adultos por especie | Semillas, zanahorias, patatas o dientes de león |
| **Apicultor** | Panal | 17×17 delante | Vacía colmenas y nidos llenos sin enfadar a las abejas y planta flores | Frascos de cristal (o tijeras para panales). Flores opcionales |
| **Hortelano** | Harina de hueso | 13×13 delante | Melones y calabazas de tallo, caña, cactus, bambú, bayas dulces y luminosas, cacao y verrugas del Nether (replanta estas dos) | Nada |
| **Herrero** | Alto horno | — | Funde menas y metal en bruto (recetas de alto horno) | Menas y combustible |
| **Cocinero** | Ahumador | — | Cocina carne, pescado y patatas (recetas de ahumador) y hornea pan con 3 de trigo | Comida cruda o trigo, y combustible |
| **Carbonero** | Horno | — | 5 troncos → 4 de carbón vegetal, sin combustible | Troncos |
| **Cantero** | Cortapiedras | — | Roca y piedra → ladrillos de piedra; andesita, diorita y granito pulidos; arenisca; ladrillos de pizarra, piedra negra y toba | Esos bloques (con pico, más rápido) |
| **Flechero** | Mesa de flechería | — | Pedernal + palo + pluma → 4 flechas; grava → pedernal | Esos materiales |

El pastor y el criador nunca sacrifican animales con nombre ni atados.

**La mina bajo tierra** (minero): detrás del puesto deja un pasillo de un bloque y cava una escalera de 5 escalones (3 de alto, se puede bajar andando) que acaba en un rellano a 5 bloques de profundidad. Desde ahí vacía el pozo, que empieza bajo tierra con 3 bloques de techo, así que en la superficie solo se ve la boca de la escalera, dentro de su granero. Si le das antorchas, pone una en la escalera y otra en el rellano. **Ver zona** marca en la superficie dónde irá el pozo y por dónde baja la escalera.

### Ayuntamiento
Receta: lingote de oro, libro y lingote de oro arriba; tablón, cofre y tablón en medio; 3 tablones abajo. Guarda comida o Glory Coins dentro y cada pocos segundos rellena las provisiones de todos tus NPCs (trabajadores, soldados y reclutas) a menos de 48 bloques, hasta 3 días de paga. Así no tienes que ir dándoles de comer uno a uno.

## Fase 4 — Logística y comercio

### Almacén
Receta: tablón, libro y tablón arriba; cofre, tablón y cofre en medio; 3 tablones abajo. El almacén no guarda nada él
mismo: **une todos los cofres y barriles a menos de 16 bloques** (8 de alto) en una red. Al abrirlo ves todo lo que hay
en la red junto, ordenado por cantidad, con un **buscador**:
- clic: sacar un montón · clic derecho: medio montón · Mayús + clic: directo a tu inventario;
- clic con algo en el cursor: guardarlo (clic derecho: de uno en uno) · Mayús + clic en tu inventario: guardar ese montón.

Lo que metes va primero junto a lo que ya hay del mismo tipo y luego a huecos libres. Solo lo puede usar su dueño.

### Mensajero
Un oficio más (puesto con una silla de montar). Busca el almacén tuyo más cercano a menos de 48 bloques y lleva hasta
9 montones encima.

**Automático** (sin hoja de ruta), por este orden:
1. Si lleva algo, lo entrega (al puesto que lo pidió o, si no, al almacén).
2. Lleva del almacén lo que falta: el **material** que le falta a una obra, lo que **gasta cada oficio** (semillas y harina
   de hueso al granjero, brotes al leñador, antorchas al minero, menas y carbón al herrero, comida cruda al cocinero,
   troncos al carbonero, piedra al cantero, pedernal, palos, plumas y grava al flechero...), lo que **se está acabando** en
   los suministros (si lo pusiste tú) y la **paga del ayuntamiento** (si tiene menos de 32).
3. Recoge la **producción** de los puestos con 16 objetos o más (o llenos) y la lleva al almacén. Deja 16 de lo que el
   oficio también usa (semillas, brotes).

**Hoja de ruta** (receta: papel, tinta y pluma). Con ella en la mano, clic derecho en cofres, barriles, puestos, el
almacén o el ayuntamiento para añadirlos como parada (otra vez para quitarla; hasta 6). Clic derecho al aire abre la
hoja: en cada parada eliges **Recoger** o **Entregar** y hasta 3 **filtros** (clic con un objeto; vacío = todo). Pon la
hoja en los suministros del mensajero y hará esa ruta en bucle en vez de repartir solo, saltándose las paradas donde no
hay nada que hacer. En los puestos recoge de la producción y entrega en los suministros.

### Comerciante
Un oficio más (puesto con una esmeralda). Hace dos cosas:
- **Tienda para jugadores**: lo que pongas en los suministros del puesto se vende. Los demás jugadores hacen clic derecho
  en el puesto y compran con monedas, que caen en la producción. Tú pones el precio y el lote (1, 2, 4... 64) de cada
  hueco con el botón **Tienda** del menú del puesto. La tienda solo abre si el comerciante está a menos de 16 bloques.
- **Venta de excedentes**: cada 2 minutos va al almacén y vende en el mercado lo que sobra (deja 64 de cada cosa), hasta
  8 lotes por viaje, a precios parecidos a los de los aldeanos (20 de trigo, 15 de carbón o 4 lingotes de hierro por
  1 moneda...). Las monedas van a la producción, y el mensajero las lleva al almacén y, de ahí, al ayuntamiento.

**Moneda**: la Glory Coin.

## Fase 5 — Facciones y diplomacia

### Las facciones
| Facción | Dónde | Clases | Detalles |
|---|---|---|---|
| **Bandidos** | Llanuras, bosques, sabanas, badlands (no en tierras frías) | Guerrero, bárbaro, arquero, ballestero, curandero, capitán | No respetan la bandera blanca y nunca serán tus aliados, pero se les puede sobornar. |
| **Vikingos** | Taigas, llanuras nevadas, arboledas, colinas | Huscarl (hacha o espada con escudo), berserker, arquero, völva (curandera), jarl | Más duros. El **berserker** entra en furia con poca vida (fuerza, velocidad y resistencia). El **jarl** toca el cuerno de guerra y da fuerza a los suyos. Escudos rojos y blancos. |
| **Piratas** | Playas y costas | Pirata (alfanje), tirador (ballesta, a veces con flechas incendiarias), matón (hacha), cirujano de a bordo, capitán | No se ahogan. El **capitán** se toma un trago de ron cuando está herido. Escudos negros con calavera. |

Todas las facciones son enemigas entre sí: si se cruzan, pelean.

**Voces**: cada facción tiene la suya, propia del mod (ya no suenan como saqueadores, vindicadores o aldeanos). Cada NPC
tiene además su tono, un poco más grave o más agudo, que no cambia.

| Quién | Su voz | Al recibir un golpe |
|---|---|---|
| **Bandidos** | Voz ronca y con mucho aliento: risitas entre dientes, bufidos, un «eeeh…» desconfiado | «¡Ugh!», «¡agh!» |
| **Vikingos** | La más grave, de pecho y rota: gritos de guerra cortos («¡huu!», «¡ja-joo!») y gruñidos | «¡Raagh!», «¡ohh!» |
| **Piratas** | Áspera, con la erre vibrante: «¡arrr!», «¡yo-jo!» y risotadas | «¡Argh!», «¡au!» |
| **Tu gente** (reclutas, soldados y trabajadores) | Tranquila: «¿mm?», «mm-jm» y resoplidos | «¡Uf!», «¡ah!» |
| **Comerciantes** | Amable: «hmmm…» pensando un precio, «¡a-já!», «mm-mm» | «¡Oh!», «¡ay!» |

Al morir se quejan más grave. Los reclutas dicen «mm-jm» al contratarlos; los comerciantes, «¡a-já!» al cerrar un trato
y «mm-mm» cuando no sale. Las facciones hablan cada 8 s o más; tu gente, cada 15; los comerciantes, cada 10. Todas
tienen subtítulos.

**Asentamiento vikingo** (taigas y nieve): casa larga del jarl con hogar alargado, trono, bancos y cabezas de dragón en
el tejado; alrededor, cabañas, herrería, círculo de piedras rúnicas, atalaya, leñera y un **drakkar** varado con vela a
rayas y escudos en la borda.

**Cala pirata** (playas, costas rocosas y manglares): un **barco pirata** varado con casco negro, velas negras rotas, cañones asomando por las
troneras, cofa con bandera negra, camarote del capitán y bodega con tesoro; alrededor, tiendas, un chiringuito, una
batería de cañones, un vigía y un tesoro enterrado bajo una X roja.

**Cada cuánto se encuentran**:
- **Asentamiento vikingo**: uno por cada zona de unos 380×380 bloques que caiga en tierras frías (taigas, nieve,
  arboledas, colinas). En una región fría grande sale más o menos uno cada 400 bloques: el doble que las aldeas.
- **Cala pirata**: hay un intento cada unos 190×190 bloques, pero solo prospera si cae en playa, costa rocosa o
  manglar. Siguiendo una costa, cuenta con una cada 500-800 bloques; en los manglares, bastantes más.
- **Guaridas de bandidos**: en llanuras, bosques, sabanas y badlands (las tierras frías son de los vikingos, así que no
  se pisan).
- **Mapas**: los cartógrafos venden el *Mapa del tesoro pirata* (aprendiz: 11 esmeraldas y una brújula) y el *Mapa de
  asentamiento vikingo* (oficial: 13 esmeraldas y una brújula). También salen en cofres: los vikingos guardan cartas de
  las calas piratas, los piratas de los asentamientos vikingos, y los fuertes de bandidos de ambos.

**Nivel de los asentamientos**: cada campamento, fuerte, asentamiento o cala tiene un nivel del 1 al 10. Sube uno
cada 750 bloques de distancia al punto de aparición del mundo, más 0-2 al azar (siempre el mismo para cada sitio); los
fuertes de bandidos, uno más. **Cada nivel por encima del 1 da a su guarnición un 10 % más de vida y de daño** (también
el de sus flechas): nivel 5 = +40 %, nivel 10 = +90 %. Al acercarte a menos de 24 bloques sale su nombre,
su nivel (verde 1-3, dorado 4-6, rojo 7-8, morado 9-10) y cómo te trata su facción, en pequeño encima de los corazones y
la barra de hambre.

**Botín según el nivel**: los cofres de los asentamientos dan más cuanto más nivel tienen.
- Más objetos: tiradas extra de lo normal desde el nivel 3, otra vez desde el 6 y otra desde el 9.
- Mejores objetos: desde el **nivel 4**, diamantes, libros encantados, equipo de hierro encantado, manzanas de oro y
  frascos de experiencia (y otra tirada desde el 6); desde el **7**, equipo de diamante bien encantado, más diamantes,
  libros de nivel 25-30 y bloques de esmeralda (otra desde el 8); desde el **9**, chatarra de netherita, manzana de oro
  encantada, tótem de la inmortalidad, piezas de diamante con encantamientos de nivel 30 y bloques de diamante (otra en
  el 10).
- Los defensores de asentamientos de nivel 4 o más pueden soltar esmeraldas, y desde el 7 algún diamante o frasco de
  experiencia.

**Patrullas y asaltos**: la facción que aparece depende de dónde estés (vikingos en el frío, piratas en la costa y
bandidos en el resto). Las patrullas solo van a por ti si sois enemigos; si no, pasan de largo. Los asaltos atacan
aldeas, ciudades y **tu pueblo** (los alrededores de tu ayuntamiento) si la facción de la zona es enemiga tuya. Cada
facción tiene su barra de asalto y su aviso.

### Reputación
Cada jugador tiene una reputación de -100 a 100 con cada facción (empieza en -50 con los bandidos y -40 con vikingos y
piratas):

| Reputación | Trato | Qué pasa |
|---|---|---|
| Menos de -20 | **Hostil** | Te atacan nada más verte (a ti y a tus NPCs) y asaltan tu pueblo |
| -20 a 19 | **Neutral** | Te dejan en paz mientras no les ataques |
| 20 a 59 | **Amistoso** | Puedes contratar mercenarios |
| Alianza firmada (60+) | **Aliado** | Sus guerreros luchan a tu lado y no se hacen daño contigo; puedes pedirles ayuda |

- Matar a uno de los suyos baja la reputación (4, o 12 si es su jefe; la mitad si lo hacen tus NPCs) y la sube un poco
  con las demás facciones.
- Atacar a uno de ellos la baja 2 y les enfada un minuto aunque no fuerais enemigos.
- Rechazar un asalto sube 3 la reputación con los enemigos de los asaltantes.
- Una alianza se rompe si la reputación baja de 40.
- Tus soldados atacan solos a las facciones enemigas tuyas (y a quien vaya a por ellos o a por ti).

### Diplomacia
- **Bandera blanca** (palo y 4 de lana blanca): mientras la lleves en una mano, vikingos y piratas no te atacan aunque
  seáis enemigos, así que puedes cruzar sus asentamientos. Si les atacas, durante un minuto ya no vale. Los bandidos no la
  respetan.
- **Sello de embajador** (papel, lingote de oro y tinte rojo): clic derecho para abrir la diplomacia. Por cada facción ves
  la reputación en una barra con las marcas de enemistad, amistad y alianza, el trato y lo que queda de tregua, y estos
  botones (se paga con Glory Coins):
  - **Tributo**: 5 monedas → +5 de reputación.
  - **Tregua** (si sois enemigos): 3 días sin ataques ni asaltos; cuesta 10 monedas más 1 por cada 5 puntos por debajo
    de cero. Si les atacas, se rompe.
  - **Alianza** (con 60 de reputación): 30 monedas. Con los bandidos no se puede. **Romper alianza** cuesta 25 de reputación.
  - **Pedir ayuda** (aliados, una vez al día): llegan su jefe y 3 guerreros que te siguen y te defienden 5 minutos.
  - **Mercenario** (amistosos o aliados): 12 monedas → un soldado tuyo con el equipo de esa facción y 3 días de paga.

### Comandos (administradores)
```
/evolvedwarfare reputation <jugadores>                          ver la reputación
/evolvedwarfare reputation <jugadores> <facción> set|add <n>    cambiarla
/evolvedwarfare raid <bandits|vikings|pirates> [siege]          lanzar un asalto (con máquinas de asedio)
/evolvedwarfare horde [bandits|vikings|pirates] [semana]        lanzar ya una horda contra tu base (o contra ti)
```

## Fase 6 — Estructuras

### Escáner de estructuras
Receta: panel de cristal arriba; lingote de oro, brújula y lingote de oro en medio; redstone abajo.
1. **Clic derecho en dos bloques** para marcar las esquinas de la zona (máximo 48×40×48). Mientras lo llevas, el borde
   se ve con partículas (cian si cabe, rojo si es demasiado grande). **Agachado + clic al aire** borra las esquinas.
2. **Clic al aire**: se abre la ventana de escaneo. Ponle nombre y elige cuál es la **fachada** (norte, este, sur u
   oeste): ese lado quedará mirando al puesto del constructor.
3. **Escanear** gasta 1 papel (gratis en creativo) y te da un **Plano**. El aire y el agua quedan como hueco (el
   constructor lo despeja); los bloques indestructibles, los generadores de monstruos y los bloques técnicos se ignoran.

Los planos se guardan en `config/evolvedwarfare/blueprints/`, así que sirven en todos tus mundos (y en un servidor, para
todos los jugadores).

### Mesa del arquitecto
Receta: 3 papeles arriba; tablón, mesa de cartografía y tablón en medio; dos tablones abajo (las patas).
- La **biblioteca**: tus planos y los de los demás jugadores, más **28 edificios del mod** (las casas de las tres
  ciudades, la torre de vigilancia y piezas de vikingos y piratas, drakkar incluido).
- **Vista previa en 3D** (arrastrar para girar, rueda para acercar), tamaño, número de bloques, autor y la lista de
  materiales.
- **Imprimir**: te da una copia del plano (1 papel). **Borrar**: solo el autor o un administrador.

### Constructor
Un oficio nuevo: **puesto del constructor** (7 tablones, cofre y ladrillos de piedra en el centro). Asígnale un recluta y
pon un **plano en sus suministros**:
- Levanta el edificio **detrás del puesto**, con la fachada mirando hacia el puesto, igual que los demás oficios: limpia
  lo natural, rellena con tierra bajo el suelo y pone los bloques de abajo arriba (antorchas, faroles, escaleras de
  mano, alfombras y demás cosas que se apoyan en otras, al final).
- Cada bloque gasta **su propio objeto** (una escalera de roble gasta una escalera de roble; una losa doble, dos losas).
  La tierra vale por la hierba y demás suelos, y la mitad de arriba de puertas y camas no cuesta nada.
- Si le falta algo, el puesto dice qué («Falta: Farol») y el **mensajero lo trae del almacén**.
- En el menú del puesto se ve el **progreso** de la obra; **Ver zona** marca con partículas dónde irá.
- Al terminar te avisa. Para otra obra, cambia el plano.
- En **creativo**, clic derecho con un plano en el suelo lo coloca al instante.

### Ciudades del desierto y del norte
Comparten la rejilla de las ciudades: en cada sitio sale la que encaja con el bioma (unas 40 casas y 15 reclutas, como
la ciudad medieval).
- **Ciudad del desierto** (desiertos): calles de arenisca lisa, plaza del **oasis** con estanque, palmeras y bancos;
  casas de adobe con azotea y toldo, casas con terraza, **bazares** de toldos de colores, **posada de caravanas** con
  patio y pozo, cuartel almenado y torres con farol.
- **Ciudad del norte** (llanuras y taigas nevadas): calles empedradas con nieve, plaza de la **hoguera** con un abeto;
  cabañas de troncos con chimenea humeante, casas grandes de dos plantas, **taberna** con gran hogar, herrería con fragua,
  cuartel de piedra y troncos y torres de piedra con tejado puntiagudo.

Las dos se pueden asaltar (como las aldeas y la ciudad medieval).

### Fortalezas
- **Castillo** (llanuras, praderas, bosques, taigas y sabanas; más o menos uno cada 770×770 bloques, nunca pegado a una
  ciudad): muralla con adarve y almenas, cuatro torres con trampilla a la azotea, puerta fortificada con rastrillo,
  **torre del homenaje** de tres plantas (salón del trono, aposentos del señor con el **tesoro** y armería), cuartel,
  establo con caballos, herrería, pozo y dianas. Lo guardan **reclutas** (se pueden contratar) y se puede asaltar.
- **Fortaleza del caudillo bandido** (donde los demás bandidos; 1 de cada 7 guaridas): muralla de piedra con estacas,
  cuatro torres con arqueros, puerta con calaveras, **salón del caudillo** con trono de oro y mesas del festín y, arriba,
  su tesoro; celdas con prisioneros que puedes liberar, tiendas y una guarnición numerosa con su capitán. Su **nivel es
  dos más** que el de un campamento en el mismo sitio, así que es de lo más duro del mundo.

## Fase 7 — Asedio

### Las máquinas
| Máquina | Receta | Qué hace | Munición |
|---|---|---|---|
| **Catapulta** | tronco arriba a la derecha; cuerda, tronco, cuerda; tablón, lingote de hierro, tablón | Piedras en parábola, de unos 35 a 57 bloques; rompen piedra y madera alrededor del impacto | 1 piedra o 1 vasija incendiaria |
| **Balista** | hierro, ballesta, hierro; cuerda, tronco, cuerda; tablón, —, tablón | Tiro tenso hasta más de 150 bloques: mucho daño a una sola cosa (también a otras máquinas); rompe cristal y hojas | 1 virote |
| **Trebuchet** | tronco, cuerda, tronco; tronco, **catapulta**, tronco; hierro, bloque de hierro, hierro | Casi a tamaño real (5 × 5 bloques, 6 de alto; el brazo sube hasta los 12). Pedruscos de unos 60 a 110 bloques, con un radio mayor: derriba casi cualquier muro (hasta el hierro en el centro del impacto) | 3 piedras o 1 vasija |
| **Ariete** | 3 tablones; tronco, tronco, bloque de hierro; tablón, —, tablón | Cubierto con tejado. Golpea lo que tiene delante: daña a quien esté ahí y va rompiendo el bloque (más golpes cuanto más duro: madera 4-5, puerta de hierro 8, rastrillo 10). No rompe obsidiana ni cofres | — |

- **Virotes** (lingote de hierro, palo y pluma, en columna → 4) y **vasijas incendiarias** (cuerda, ladrillo, carga ígnea y
  ladrillos → 2): la vasija prende fuego alrededor y quema a quien pille; no rompe muros. Vale cualquier piedra como
  munición: roca, piedra, pizarra abismal, andesita, diorita, granito, toba, piedra negra, arenisca...
- **Ponerla**: clic derecho en el suelo; mira hacia donde mires.
- **Manejarla**: clic derecho para subirte. **W/S** la empujan, **A/D** la giran y **Espacio** dispara (o golpea, con el
  ariete). Para apuntar, **mira el blanco**: el tiro calcula solo la elevación para caer ahí. Mientras la manejas ves el
  recorrido del tiro con una línea y, donde caerá, un círculo del tamaño de la zona que alcanza (verde si llega; rojo,
  con una cruz donde apuntas, si se queda corto o se pasa); abajo, si está lista o recargando, la munición y la vida.
  Recarga: balista 2 s, catapulta 3,5 s, trebuchet 7 s, ariete 1,5 s.
- **Vista aérea**: montado en la catapulta, la balista o el trebuchet, **mantén el clic izquierdo** y la cámara sube por
  encima del blanco. El ratón mueve el punto al que apuntas por el suelo (también detrás de los muros, que desde el
  asiento no se ven), dentro del arco que cubre la máquina; arriba pone a cuántos bloques está y si llega. **Espacio**
  dispara ahí; al soltar el clic vuelves al asiento. Montado, el clic izquierdo no pega ni rompe bloques.
- **Dónde te sientas**: en el banco de atrás, a un lado del brazo (catapulta); en el taburete detrás del arco (balista);
  en el banco de atrás, lejos del contrapeso (trebuchet); y dentro del ariete, a un lado del tronco.
- **Cargarla**: clic derecho con piedras, vasijas o virotes los mete dentro (hasta 256 de cada). Si no lleva, gasta los de
  tu inventario (con la vasija en la mano, dispara vasijas).
- **Tu soldado la maneja**: clic derecho con el **bastón de mando** y el soldado tuyo más cercano (a menos de 24 bloques)
  se sube y dispara solo a tus enemigos (facciones enemigas y monstruos) con la munición que tenga dentro. No tira a
  nada que tenga a uno de los tuyos cerca. Otro clic con el bastón y se baja.
- **Repararla**: clic derecho con tablones (+10 % de vida cada uno). Sufre el doble con hachas y el triple con fuego (la balista, el doble).
- **Desmontarla**: agachado y con la mano vacía (la que es tuya, y no en pleno combate): vuelve al inventario con su munición.
- Destrozada, suelta parte de la madera, la cuerda y el hierro.

### Rastrillo y puente levadizo
- **Rastrillo** (5 barrotes de hierro y 4 lingotes en X → 8): una reja de hierro gruesa que no se abre a mano.
- **Tablones de puente levadizo** (tablones, pepitas de hierro, tablones → 6).
- **Torno** (tablón, cadena, tablón; tronco, lingote de hierro, tronco; 3 tablones): ponlo **pegado** al rastrillo o al
  puente. La primera vez que lo usas se lo aprende entero. Clic derecho para abrir o cerrar; con **redstone**,
  encendido = abierto (rastrillo arriba, puente bajado).
  - El **rastrillo** sube tantos bloques como alto es (necesita hueco encima) y al cerrar baja hasta el suelo,
    aplastando a quien pille. Se ve **deslizarse** entero y suave (arranca y frena), con ruido de cadenas y un golpe
    metálico al tocar el suelo; mientras se mueve sigue cerrando el paso.
  - El **puente** es un rectángulo de tablones, tumbado (bajado) o de pie (subido). Pon el torno **mirando hacia donde
    tiene que bajar**: **gira** sobre la fila de dentro, que hace de bisagra, en un cuarto de vuelta (al bajar cae cada
    vez más rápido y acaba con un golpe de madera). Necesita despejado el sitio donde va a quedar.
  - Si le das al torno mientras se mueve, se apunta la orden y la hace al acabar.
  - Solo lo maneja quien lo puso (con redstone, cualquiera que llegue a la palanca).
- Los **castillos** ya traen un rastrillo subido en la puerta (su torno está en la cara del patio, sobre la puerta), una
  balista sobre la puerta y una catapulta en el patio, para quien las coja. Las **fortalezas del caudillo bandido**
  tienen una catapulta manejada por un bandido que dispara a quien se acerque.

### Asedios enemigos
Cuando una facción asalta **tu pueblo** (tu ayuntamiento) o un **castillo**, a veces (30 % en fácil, 45 % en normal,
60 % en difícil) trae máquinas: un **ariete** que va hacia el centro derribando muros y puertas, o una **catapulta**
que se queda a unos 38 bloques y bombardea el pueblo. Su dotación cuenta como asaltante. Si ganas, las máquinas se
quedan y **son tuyas** si te subes; si se retiran, se las llevan. Cualquier máquina de una facción que se quede sin
nadie dentro se puede **capturar** igual.

Reglas del mundo (`/gamerule`): `evolvedwarfareSiegeBlockDamage` (los proyectiles y el ariete rompen bloques; sí por
defecto) y `evolvedwarfareSiegeRaids` (los asaltos traen máquinas; sí por defecto). Para probar un asedio:
`/evolvedwarfare raid bandits siege`.

## Fase 8 — Armas, habilidades, equilibrio y configuración

### Armas medievales
Cada una en hierro y diamante (en la mesa de crafteo, con palos) y en netherita (en la mesa de herrería, con la plantilla
y un lingote de netherita, como las demás armas). Se encantan como las espadas (el hacha de batalla, como las hachas) y
los soldados las usan: con una lanza o una alabarda, los NPCs también golpean desde más lejos.

| Arma | Receta (M = lingote o diamante, S = palo) | Daño (hierro) | Golpes/s | Especial |
|---|---|---|---|---|
| **Daga** | M sobre S | 4 | 2,4 | Por la espalda, ×1,5 |
| **Lanza** | M arriba a la derecha y dos S en diagonal | 5 | 1,4 | +1,5 bloques de alcance |
| **Mandoble** | M, M y abajo M S M | 8 | 1,1 | Barrido amplio, +0,5 de alcance |
| **Alabarda** | M _ M, S, S | 9 | 0,9 | +2 de alcance; ×1,5 contra jinetes y monturas |
| **Martillo de guerra** | 3 M, M S M, S | 9 | 0,8 | Atraviesa armadura (+35 % de la armadura del blanco en daño) y empuja |
| **Mangual** | S, cadena, M en una fila | 8 | 1,0 | 25 %: aturde (lentitud y debilidad 2 s) |
| **Hacha de batalla** | 3 M, M S, S | 11 | 0,8 | Desarma escudos |

**En 3D y con su propio golpe.** En la mano (en primera y tercera persona), en el suelo y en los marcos se ven con
modelo 3D (en el inventario, el icono de siempre), y cada una ataca a su manera; los soldados, bandidos, vikingos y
piratas golpean igual. Las de dos manos (lanza, mandoble, alabarda y martillo) meten también la otra mano si va libre.

| Arma | El golpe | Dura |
|---|---|---|
| **Daga** | Puñalada rápida al frente | 0,25 s |
| **Lanza** | Estocada larga, la lanza en horizontal | 0,35 s |
| **Mandoble** | Tajo horizontal de derecha a izquierda | 0,45 s |
| **Alabarda** | La alza por encima de la cabeza y la deja caer | 0,5 s |
| **Martillo de guerra** | Mazazo por encima de la cabeza, con rebote al dar | 0,55 s |
| **Mangual** | Voltea la bola por encima y golpea cruzado; la cadena cuelga y se estira al girar | 0,5 s |
| **Hacha de batalla** | Hachazo en diagonal, de arriba a la derecha hacia abajo | 0,5 s |

La animación no cambia el daño ni la velocidad de ataque (los de la tabla de arriba).

**Golpe fuerte.** Con cualquiera de estas armas, **mantén pulsado clic derecho** para cargar un golpe fuerte: el
personaje echa el arma atrás (cada una a su manera) y bajo la cruz de mira sale una barrita que se va llenando. Cuando
se llena (se pone dorada, late y suena un aviso), **suelta** y el golpe sale contra lo que tengas delante, a tu alcance,
con el **doble de daño** (también cuentan el crítico, el barrido, los encantamientos y las ventajas del árbol). Si sueltas
antes de que se llene, no pasa nada: el arma vuelve a su sitio. Mientras cargas vas más despacio, como al tensar un arco.

| Arma | Carga | Postura cargada | El golpe fuerte |
|---|---|---|---|
| **Daga** | 0,5 s | La mano atrás, pegada al cuerpo | Puñalada a fondo |
| **Lanza** | 0,8 s | La lanza atrás, en horizontal | Estocada larguísima |
| **Mandoble** | 1 s | Alzado sobre el hombro | Tajo de lado a lado |
| **Alabarda** | 1,2 s | Bien alta por detrás | Hachazo hasta el suelo |
| **Martillo de guerra** | 1,3 s | Detrás de la cabeza | Mazazo con rebote |
| **Mangual** | 1 s | El brazo arriba, volteando la bola | Golpe cruzado |
| **Hacha de batalla** | 1,3 s | Atrás, por encima del hombro | Hachazo en diagonal |

La carga dura al menos lo que tarda en llenarse la barra de ataque de cada arma. Si llevas en la otra mano algo que se
usa con clic derecho (un escudo, comida, bloques, antorchas, lo que se lanza), el clic derecho sigue siendo para eso; con
el hacha de batalla sobre un tronco, lo descortezas como siempre. Los demás jugadores te ven cargar y golpear.

- **Arco largo** (palos, cuerdas y un lingote de hierro en el centro): flechas un 25 % más rápidas y rectas, y más daño.
  Se encanta como un arco.
- **Jabalina** (lingote, palo y pluma en diagonal → 2): mantén pulsado clic derecho y suéltala; hace unos 9 de daño y se
  queda clavada para recogerla.
- Los **bandidos** llevan a veces lanzas, dagas, manguales, hachas de batalla y mandobles (el capitán); los **vikingos**,
  hachas de batalla (el jarl, a veces de diamante); los **piratas**, dagas y manguales. También salen en sus cofres y en
  la armería de los castillos.

### Árbol de habilidades
Como en *Skyrim*: **las habilidades suben usándolas**, y cada vez que sube una, sube un poco tu **nivel de personaje**.
Cada nivel de personaje da **1 punto de ventaja** (empiezas con 1) para gastar en el árbol. Se abre con la **tecla K**
(se cambia en Controles) o con clic derecho en la **mesa de investigación** (libro, pluma, libro; tablón, mesa de
crafteo, tablón; dos tablones; los castillos tienen una en los aposentos del señor).

Hay **11 habilidades** (de 0 a 100) en cuatro grupos:

| Grupo | Habilidad | Sube… |
|---|---|---|
| Cuerpo a cuerpo | **Hojas** (espadas, dagas, mandobles) · **Hachas** (hachas, hachas de batalla) · **Mazas** (martillos de guerra, manguales, la maza) · **Astas** (lanzas, alabardas, tridentes) | golpeando con esa arma (según el daño) |
| A distancia | **Arquería** (arcos, ballestas, jabalinas) · **Armas de fuego** (mosquete) | acertando con flechas, virotes, jabalinas o balas |
| Defensa | **Escudo** · **Armadura pesada** (hierro, diamante, netherita) · **Armadura ligera** (cuero, cota de malla, oro, caparazón) | parando con el escudo · recibiendo golpes con esas piezas puestas |
| Mando | **Liderazgo** · **Asedio** | cuando tus soldados luchan y cobran · disparando y rompiendo con las máquinas de asedio |

La pantalla es un cielo estrellado: cada habilidad, su constelación (abajo, las pestañas; o flechas, A/D o la rueda).
Cada estrella es una **ventaja**: **dorada** si la tienes, **blanca** si la puedes coger, **gris** si aún no (te dice
qué nivel o qué ventaja anterior te falta). Clic para cogerla; algunas tienen varios rangos. Al subir una habilidad o de
nivel sale un aviso arriba en pantalla.

| Habilidad | Ventajas (nivel que piden) |
|---|---|
| Hojas | Maestría +10…50 % de daño (0/20/40/60/80) · Forja de dagas (0) · Forja de mandobles (25) · Danza de acero +15 % de velocidad (30) · Puñalada trapera +50 % por la espalda (35) · Golpe certero 10…20 % de crítico (50/70/90) · Torbellino: barrido +50 % (75) |
| Hachas | Maestría (0…80) · Leñador: talar +40 % (10) · Forja de hachas de batalla (25) · Sangrado 1…2 por segundo (30/60/90) · Hachazo: críticos +50 % (45) · Carnicero +25 % al que sangra (65) · Furia: fuerza al matar (85) |
| Mazas | Maestría (0…80) · Forja de martillos y manguales (0) · Rompehuesos: atraviesa armadura (30/60/90) · Aturdir 15 % (40) · Conmoción en los críticos (60) · Golpe de gracia +30 % a los heridos (70) · Aplastar: el crítico daña alrededor (90) |
| Astas | Maestría (0…80) · Forja de lanzas (0) · Mantener a raya +0,5 de alcance (20) · Estocada corriendo +20…50 % (30/60/90) · Forja de alabardas (40) · Contra jinetes +50 % (45) · Muro de picas: empuja al que te golpea de frente (65) · Empalar (90) |
| Arquería | Tensar +10…50 % (0…80) · Jabalinas (0) · Arco largo (20) · Cazador +30 % a animales (20) · Recuperar flechas 33 % (30) · Tiro crítico (40/60/80) · Diana: cabeza +50 % (70) · Explorador +10 % de velocidad (90) |
| Armas de fuego | Pólvora fina +10…50 % (0…80) · Recarga rápida −15…45 % (20/50/80) · Bolsillo de balas: 25 % sin gastar (30) · Pulso firme +25 % apuntando (40) · Atronador (60) · Tirador de élite: cabeza +50 % (75) |
| Escudo | Escudo recio: se gasta hasta un 75 % menos (0/30/60) · Inamovible (20) · Guardia rápida 1,5 s (30) · Contraataque 30 % (45) · Golpe de escudo (60) · Muro de escudos: tus soldados cerca, −20 % de daño (80) |
| Armadura pesada | Coloso +8…40 % de armadura (0…80) · Armería de facción: armaduras de rango 3 (20) · Bien ajustada (30) · Firme como una roca (40) · Forja de capitanes: armaduras de rango 4 (50) · Acolchado: caídas −50 % (50) · Conquistador +10 % de daño (60) · Devolver golpes 10 % (90) |
| Armadura ligera | Defensa ágil +8…40 % (0…80) · Sastrería de facción: armaduras de rango 1 y 2 (0) · Paso ligero +10 % de velocidad (20) · A medida (30) · Evasión 5…15 % (40/60/80) · Caída de gato (50) · Segundo aliento (70) |
| Liderazgo | Instrucción +20 % de vida (0) · Tácticas +15 % de daño (20) · Medicina de campaña (20) · Intendencia: 25 % de días sin paga (30) · Inspirar: fuerza cerca de ti (40) · Veteranos: doble experiencia (50) · Estandarte: resistencia cerca de ti (70) |
| Asedio | Carpintería de asedio: ariete (0) · Fortificación: rastrillo, puente levadizo y torno (0) · Máquinas de torsión: catapulta, balista y virotes (20) · Artillero: recarga −15…45 % (20/50/80) · Ahorro 25 % (30) · Fuego de asedio: vasijas (40) · Zapador +30 % contra muros (40) · Contrapeso: trebuchet (60) |

Las ventajas de **forja** son las que dejan fabricar las armas, máquinas y fortificaciones del mod: sin ellas, el
resultado de la mesa de crafteo sale vacío y te dice qué ventaja te falta (la descripción del objeto también lo dice).
Lo que encuentres en cofres, quites a los enemigos o compres al armero sí lo puedes usar. En creativo no hace falta nada
y las ventajas no gastan puntos. Las de Liderazgo valen aunque no estés conectado (tus soldados las llevan siempre).

**Si venías de la investigación de antes** (hasta la 0.19): lo que tenías investigado pasa solo al árbol como ventajas
gratis la primera vez que entras (te lo dice en el chat).

### Equilibrio
- Las armas nuevas tienen el mismo daño por segundo que una espada (o algo menos) y lo compensan con su especial.
- Lo que antes eran números fijos ahora se configura: cada cuánto hay patrullas y asaltos, la probabilidad de asalto y
  de que traiga máquinas, la vida y el daño de las facciones y de tus soldados, la mejora por nivel de los asentamientos,
  los días sin cobrar, y el daño, la fuerza contra los bloques, la vida y la recarga de las máquinas de asedio.
- Tus soldados suman tus ventajas de Liderazgo; los de las facciones, el nivel de su asentamiento y la configuración.

### Configuración
Archivo `config/evolvedwarfare.json` (se crea solo). Se puede cambiar sin salir del juego:
```
/evolvedwarfare config                     ver todas las opciones
/evolvedwarfare config <opción> <valor>    cambiar una (se guarda al momento)
/evolvedwarfare config reload | reset      volver a leer el archivo | valores de fábrica
```
Con **Mod Menu** instalado, el botón de configuración del mod abre una pantalla con pestañas (Habilidades,
Facciones, Asentamientos, Tus NPCs, Asedio, Armas). En un servidor manda la configuración del servidor.

En **Habilidades**: `skillXpRate` (lo rápido que suben; 1 = normal) y `researchRequired` (si hacen falta las ventajas de
forja para fabricar).

Habilidades de los jugadores (administradores):
```
/evolvedwarfare skills <jugadores>                              ver niveles, puntos y ventajas
/evolvedwarfare skills <jugadores> points <n>                   dar (o quitar) puntos de ventaja
/evolvedwarfare skills <jugadores> level <habilidad> <nivel>    poner una habilidad en un nivel
/evolvedwarfare skills <jugadores> perk <ventaja|all> [rango]   dar una ventaja sin pedir nada
/evolvedwarfare skills <jugadores> reset                        quitar las ventajas y devolver los puntos
```

## Fase 9 — Comerciantes y niveles de las ciudades

### Niveles de las ciudades

Las ciudades (medievales, del desierto y del norte) tienen **nivel del 1 al 10**: cuanto más lejos del punto de
aparición, más nivel (uno cada **600 bloques**, más un poco de azar fijo por ciudad). Al entrar en una sale su nombre y
su nivel, en pequeño encima de los corazones y la barra de hambre. **Todos los comerciantes venden y compran su catálogo entero en cualquier ciudad**; el nivel solo
decide **a qué precio**. Cada rareza tiene su rango de precios (lo que cuesta un lote en una ciudad de nivel 1):

| Rareza | Precio de un lote | Ejemplos |
|---|---|---|
| **Común** | 1 – 500 | comida, piedra, lana, armadura de hierro suelta, escudos |
| **Poco común** | 501 – 2.000 | pechera de hierro, armas medievales de hierro, brújulas, perlas de Ender |
| **Raro** | 2.001 – 5.000 | mosquete, diamantes, armas y armaduras de diamante, obsidiana, manzanas de oro, sillas |
| **Épico** | 5.001 – 10.000 | pechera de diamante, alabarda de diamante, bloque de diamante, aliento de dragón |
| **Legendario** | 10.001 – 500.000 | élitros, estrella del Nether, netherita, tótems, faro, manzana de Notch |

Dentro de cada rareza, lo que vale el doble cuesta el doble (lo barato se vende en lotes más grandes para llegar al
mínimo), así que fabricar o deshacer cosas no da dinero gratis. Encima, cada nivel por encima del 1 sube los precios de
venta un **10 %** (nivel 10 = +90 %, por encima del rango). Lo que **te compran** lo pagan a **la mitad del precio
nuevo**, igual en todas partes. Se puede cambiar en la configuración (categoría *Comercio*: bloques por nivel, nivel
máximo, subida por nivel y mínimo al día por objeto).

El nivel también decide la **calidad** de lo que tiene durabilidad (armas, armaduras, herramientas, arcos, escudos,
el mosquete...). Al entrar en la ciudad sale en el aviso, y en la tienda, en la descripción de cada cosa:

| Nivel | Calidad | Cómo vienen |
|---|---|---|
| 1–2 | **Desgastado** | gastados: les falta entre un 35 % y un 55 % de durabilidad |
| 3–4 | **Normal** | nuevos, sin encantar |
| 5–6 | **Bueno** | encantados como en una mesa con poca experiencia (nivel 5–10) |
| 7–8 | **Excelente** | encantados a media potencia (nivel 15–22) |
| 9–10 | **Obra maestra** | encantados casi al máximo de la mesa (nivel 26–30) |

Los encantamientos cambian cada día con la reposición, y cada comerciante tiene los suyos (lo que no se puede
encantar, como el mosquete, solo cambia en el desgaste). En la tienda se ve el brillo de lo encantado y la barra de
lo gastado.

### Los comerciantes

En los puestos de las ciudades viven **comerciantes** que comercian **siempre con Glory Coins**, la moneda del mod. No se van, no desaparecen y no se alejan más de 8 bloques de su puesto; huyen de
monstruos y asaltantes (que también van a por ellos, así que defiende el mercado).

| Comerciante | Dónde | Vende |
|---|---|---|
| **Tendero** | todas las ciudades | comida; en ciudades altas, manzanas de oro y de Notch |
| **Armero** | fraguas (siempre) y a veces en mercados | armaduras, escudos, ballestas, **mosquetes** (raro), **todas las armas medievales del mod** (de hierro, de diamante y de netherita según el nivel), arco largo, jabalinas, virotes y vasijas incendiarias |
| **Cantero** | ciudades | piedra labrada, ladrillos, cristal...; luego obsidiana, prismarina, ladrillo del End, piedra imán y faros |
| **Joyero** | ciudades | oro, gemas, brújulas...; luego diamantes, tótems, corazón del mar, netherita, élitros y estrellas del Nether |
| **Sastre** | ciudades medievales | lana, camas, estandartes...; luego sillas, armaduras de caballo y diseños de estandarte |
| **Mercader de especias** | ciudades del desierto | azúcar, cacao, tintes...; luego polvo de blaze, lágrimas de ghast, aliento de dragón, cajas de shulker |
| **Peletero** | ciudades del norte | cuero, ropa de cuero, hielo...; luego armadura de lobo, caparazón de tortuga y tótems |

### Carreta

Una plataforma de **3x3 bloques** sobre dos ruedas, con dos varas delante, para llevar tu taller o tu almacén contigo.

- **Fabricarla**: primero las piezas, luego la carreta.
  - **Rueda de carreta**: un tablón en el centro y cuatro palos alrededor (`_ S _`, `S P S`, `_ S _`).
  - **Plancha de carreta**: tres losas de madera encima de tres troncos.
  - **Carreta**: dos palos arriba (las varas, `S _ S`) y abajo rueda, plancha y rueda.
- **Ponerla**: clic derecho en el suelo; queda con las varas hacia ti. Necesita sitio libre de 3x3.
- **Tirar tú**: con una **rienda** en la mano, clic derecho en la carreta: te pones entre las varas y te sigue a donde
  vayas (algo más despacio, y sube escalones de un bloque). **Agáchate** para soltarla y recuperar la rienda. Si se
  atasca y te alejas mucho, se suelta sola.
- **Con caballo** (o burro o mula, domado): con la rienda en la mano, clic derecho en el caballo cerca de la carreta (o
  clic en la carreta con el caballo atado a ti). El caballo se coloca entre las varas; **súbete** y llévala. Para
  soltarlo: bájate y agachado + clic derecho en la carreta.
- **La carga** (clic derecho con la mano vacía): una pantalla con la carreta **vista desde arriba** y **9 huecos** (3x3,
  delante arriba). Puedes poner **cofres, barriles, hornos** (también ahumador y alto horno), **mesas de crafteo** y
  **camas**, en el orden que quieras. Cada cosa ocupa los mismos bloques que en el mundo: la **cama ocupa dos huecos**
  seguidos (se ve de su color en la pantalla).
- **Lo de encima se ve en 3D y se usa como siempre** (clic derecho en ello):
  - **Cofre y barril**: 27 huecos cada uno.
  - **Horno**: funde también en marcha (se ve encendido).
  - **Mesa de crafteo**: la de siempre.
  - **Cama**: fija tu reaparición junto a la carreta, aunque luego la muevas, y de noche puedes dormir en ella.
- **Quitar algo** de su hueco: lo que tenga dentro cae al suelo. **Romper la carreta** (a golpes; con hacha va más rápido)
  suelta la carreta, todo lo de encima y lo de dentro.

### Caja de cultivos

Los comerciantes **ya no compran los cultivos sueltos: solo en cajas llenas**. La **Caja de cultivos** se fabrica con
dos palos arriba (`S _ S`), dos tablones a los lados (`P _ P`) y tres tablones abajo (`P P P`).

- **Llenarla**: ponla en el suelo y haz clic derecho. Tiene **un hueco** donde cabe un solo cultivo, **hasta 64**. La
  pantalla te dice cuántos lleva y cuántos faltan. También la llenan las **tolvas**.
- **Se ve por dentro**: la caja vacía enseña el fondo; al meterle algo, el cultivo aparece amontonado dentro, más alto
  cuanto más lleva, sobre una cama de paja. Las calabazas, los melones y los cactus se ven como cubitos.
- **Llevártela**: **agáchate y haz clic derecho con la mano vacía**. La caja pasa a tu mano con todo lo de dentro, y en
  el inventario se llama *Caja de trigo*, *Caja de zanahorias*… con su icono lleno. Al ponerla otra vez (clic derecho en
  el suelo) sigue llena, y si la rompes suelta la caja con lo suyo (en creativo también). Las cajas iguales se apilan
  (hasta 16).
- **Con las dos manos**: la caja se lleva delante, agarrada por sus asas con las dos manos. Mientras la llevas, lo que
  tengas en la otra mano no se ve ni se puede usar, y una caja no cabe en la mano izquierda (si la pones ahí, vuelve al
  inventario).
  - **Primera persona**: la ves delante y abajo, con la tapa un poco hacia ti (se ve lo que lleva), y tus dos brazos
    agarrándola por los lados.
  - **Tercera persona**: los brazos adelante y la caja entre las manos (también la ven así los demás jugadores).
  - **Animaciones**: al cogerla del suelo, los brazos bajan a por ella y la suben hasta el pecho; andando, la caja se
    mece con cada paso; al sacarla del inventario, sube desde abajo.
- **Venderla**: el tendero y el mercader de especias compran **una caja llena por lote**, al precio de sus 64 cultivos y
  un 10 % más por venir en caja. Compran tantas cajas al día como cultivos sueltos movían antes (2 como mínimo). Una caja
  a medio llenar no la compran.
- **Qué cabe**: trigo, zanahorias, patatas, remolachas, rodajas de melón, melones, calabazas, bayas dulces, bayas
  luminosas, cacao, caña de azúcar, cactus, bambú, verrugas del Nether y manzanas. Se pueden añadir más con la etiqueta
  `evolvedwarfare:crate_crops`.
- El mercader de tu pueblo sigue vendiendo los excedentes del almacén sueltos.

### La tienda

Clic derecho a un comerciante abre su **tienda con todo el catálogo a la vez**, en fichas:

- **Cada oficio tiene su propio diseño**: el tendero, tablones de roble con toldo de rayas; el armero, acero oscuro con
  remaches; el cantero, ladrillo de piedra con esquinas cinceladas; el joyero, terciopelo morado con ribete de oro y
  gemas; el sastre, paño azul con costura; el mercader de especias, arenisca con cenefas de alfombra; y el peletero,
  abeto oscuro con nieve y escarcha. A los lados del inventario lleva cuatro objetos de su oficio.

- Dos pestañas: **Compra** (**todo** lo que vende, y todo se puede comprar) y **Venta** (**todo** lo que te compra).
  Con la rueda o la barra se recorre todo.
- Cada ficha enseña el **lote**, el **precio** en monedas y **cuántas unidades le quedan hoy**; el marco va del color de
  su rareza (gris, verde, azul, morado, dorado). El precio sale en rojo si no te llega.
- De **cada cosa** vende y compra **al menos 20 unidades al día** (lo barato, más: p. ej. 16 lotes de 4 panes); cuando se
  acaba sale *Agotado* (o *Completo* en Venta) hasta el día siguiente.
- **Clic**: un lote. **Mayús+clic**: un montón entero (al comprar) o todo lo que lleves (al vender).
- Arriba: tus monedas, el nivel de la ciudad y cuánto más caro vende. Cada comerciante tiene sus propios precios
  (±10 %), así que conviene comparar.
- **Repone todo cada mañana** (cada día del juego). Cobra de tu Bolsa de Gloria y de las monedas sueltas, y te paga en la bolsa.
- En las fichas los precios largos salen cortos (12,5k = 12.500; 1,2M = 1.200.000); el número entero, en la descripción.
- Las armas que vende el armero **no necesitan la ventaja de forja**: comprarlas es la alternativa (cara) a fabricarlas.

Dónde aparecen: en el **mercado**, la **fragua**, la **taberna** y la **plaza** de las ciudades medievales; en el
**bazar**, el **caravasar** y el **oasis** del desierto; en la **taberna**, la **herrería** y la **hoguera** del norte.
Solo en ciudades nuevas (chunks sin generar). Hay **huevo generador** en la pestaña del mod, y con comandos:
`/summon evolvedwarfare:trader ~ ~ ~ {TraderType:"jeweler"}` (`grocer`, `armorer`, `mason`, `jeweler`, `tailor`,
`spice_merchant`, `furrier`). Fuera de una ciudad, el comerciante toma el nivel que le tocaría por la distancia.

## Fase 10 — Armas de fuego primitivas

### Mosquete

Un mosquete de chispa. **No se fabrica**: lo vende el **armero** en cualquier ciudad (rareza **rara**).
Tiene 300 usos y se repara con lingotes de hierro en el yunque.

Controles (como un arma de fuego):

| Botón | Qué hace |
|---|---|
| **Clic izquierdo** | **Dispara** (si está cargado; si no, chasquido y aviso). Con el mosquete en la mano no se pega ni se pican bloques. |
| **Clic derecho** (mantener) | **Apunta con la mira metálica**: en primera persona el arma va a la cara con el punto de mira en el centro, hay un poco de zoom y la cruz de la pantalla se cambia por un punto diminuto; en tercera, se lo echa a la cara con las dos manos. Vas más despacio. Apuntando, la bala va casi exacta; desde la cadera se desvía bastante. |
| **R** | **Recarga** (2 segundos; se cambia en *Controles → Evolved Warfare*). Gasta una bala de mosquete. Si cambias de objeto, se interrumpe. |

- **Recargar** se ve en primera y en tercera persona: alza el arma con la boca hacia arriba, cae la bala por el cañón,
  la aprieta con la baqueta, baja el arma, cierra la cazoleta y **sube el rastrillo**.
- **Disparar**: el **rastrillo cae** contra el acero (que se abre), el arma da un golpe hacia atrás, la vista sube un
  poco y sale **humo** y fogonazo por la boca (y una bocanada de la cazoleta). La bala vuela muy rápido y casi recta
  y hace **5 corazones** de daño al torso (con el daño locacional: 12,5 en la cabeza, 2,5 en un brazo, 3,5 en una pierna) y **empuja un bloque** hacia atrás. Los escudos la paran.
- En tercera persona, sin apuntar, lo lleva bajado en una mano.
- En la descripción del objeto pone si está cargado.

**Balas de mosquete**: pólvora + lingote de hierro + papel (sin forma) → **9 balas**.

### Bolsa de munición

Guarda hasta **1000 balas** de un mismo tipo en un solo hueco (de momento, balas de mosquete). Receta: cordel arriba;
cuero, lingote de hierro y cuero en medio; 3 de cuero abajo.

- **Clic derecho**: mete todas las balas sueltas que lleves. **Mayús + clic derecho**: saca 64.
- En el inventario se mete y se saca con clic derecho, como un saco. La barra dorada debajo del icono enseña lo llena
  que está, y la descripción, cuántas lleva.
- **Al recargar el mosquete (R), la bala se descuenta de la bolsa.** Orden: primero una bala suelta en la otra mano (si la
  has puesto ahí), luego las bolsas de munición (la de la otra mano antes que las demás) y por último las sueltas del
  inventario. Al terminar de cargar sale cuántas balas te quedan.
- Puedes llevar varias bolsas; cada una, hasta 1000.

El modelo 3D (cañón, caja, culata, abrazaderas, llave con rastrillo, acero y cazoleta, guardamonte y baqueta) lo
genera `tools/ew_musket.py`, que también dibuja vistas previas de la recarga y el disparo sin abrir el juego. Los
sonidos (disparo, bala, baqueta, rastrillo, clic sin balas) los sintetiza `tools/ew_sounds.py`.

### Daño locacional

A los **jugadores** y a los **NPCs del mod** (reclutas, soldados, trabajadores, comerciantes, bandidos, vikingos y
piratas) se les divide el cuerpo en cuatro partes, y cada una recibe distinto daño:

| Parte | Daño | Dónde está |
|---|---|---|
| **Cabeza** | **2,5x** | el cuarto de arriba |
| **Torso** | 1,0x | el centro del cuerpo |
| **Brazos** | 0,5x | a los lados del torso |
| **Piernas** | 0,7x | el 37,5 % de abajo |

- **Proyectiles** (balas de mosquete, flechas, jabalinas, tridentes...): cuenta donde corta su trayectoria.
- **Golpes de un jugador**: cuenta donde apunta.
- **Golpes de criaturas y NPCs** (no apuntan): al azar según el tamaño de cada parte (cabeza 15 %, torso 40 %,
  brazos 20 %, piernas 25 %; de media, casi igual que sin daño locacional).
- El multiplicador va antes de la armadura. Caídas, fuego, explosiones, magia y las piedras de las máquinas de asedio
  no cambian. Tumbado, nadando o planeando, todo cuenta como torso.
- Un **tiro a la cabeza** suelta chispas y, si lo das tú, suena un "ding".
- En la configuración (pestaña *Combate*) se puede apagar, cambiar cada multiplicador y extenderlo a las criaturas
  con forma de persona de Minecraft (zombis, esqueletos, aldeanos, illagers, brujas y piglins). A quién se aplica
  está en las etiquetas `evolvedwarfare:locational_damage` y `evolvedwarfare:locational_damage_vanilla`.

## Fase 11 — Hordas semanales

### Contador de días y reloj
Arriba a la izquierda, en pequeño: el reloj de Minecraft (con el sol y la luna), **Día N** y la hora en formato 24 h
(el día empieza al amanecer, a las 6:00). Se esconde con F1 y con la pantalla de depuración (F3), y se puede quitar en
la configuración (pestaña *Hordas*). En el Nether y el End la hora sale como `--:--`.

### Hordas
**Cada 7 días de juego** (días 7, 14, 21...) el contador de días se pone **rojo**: ese día viene una horda de
**bandidos, vikingos o piratas**, al azar.

- **Avisos**: la víspera, al anochecer ("mañana viene una horda") y al amanecer del día rojo.
- **Llegada**: a media mañana (las 9:00), con cuerno de guerra, por un lado al azar a unos 44 bloques.
- **A dónde va**: a tu **base**, que es tu **ayuntamiento** (el más cercano, a menos de 96 bloques); si no tienes, tu
  **cama**; y si estás lejos de los dos, **va a por ti** allá donde estés y te sigue. Los jugadores que estén juntos (o en
  la misma base) se enfrentan a una sola horda, más numerosa.
- **Oleadas**: la siguiente llega cuando la de ahora está casi derrotada (o a los 3 minutos). Arriba sale una barra con
  la oleada y los que quedan.
- **No mira la reputación**: ataca aunque seas aliado de su facción, no respeta la bandera blanca y matarlos no baja tu
  reputación. Tus soldados (y tus máquinas de asedio) van a por ellos.
- **Final**: si la derrotas entera, **Glory Coins** para los que estabais allí (2 + 3 por semana, hasta 64) y las
  máquinas de asedio que traía se quedan para ti. Al anochecer (18:00) la que quede se retira.

**Cada semana es más dura** (en normal, con un jugador; en fácil empiezan 4 por oleada y en difícil 8, con un capitán más;
cada jugador de más suma 2 por oleada):

| Semana | Por oleada | Oleadas | Capitanes por oleada | Vida | Daño | Equipo de élite | Máquinas de asedio |
|---|---|---|---|---|---|---|---|
| 1 (día 7) | 6 | 1 | — | x1 | x1 | — | — |
| 2 (día 14) | 8 | 1 | 1 en la última | x1,12 | x1,07 | 15 % | — |
| 3 (día 21) | 9 | 2 | 1 | x1,24 | x1,14 | 30 % | ariete |
| 4 (día 28) | 11 | 2 | 1 | x1,36 | x1,21 | 45 % | ariete |
| 5 (día 35) | 12 | 3 | 1 | x1,48 | x1,28 | 60 % | ariete y catapulta |
| 6 (día 42) | 14 | 3 | 2 | x1,6 | x1,35 | 75 % | ariete y catapulta |
| 8 (día 56) | 17 | 4 | 2 | x1,84 | x1,49 | 100 % | 2 arietes y catapulta |

Desde ahí sigue subiendo hasta un máximo de 24 por oleada, 3 capitanes, vida x3 y daño x2.

No hay hordas en pacífico ni con `doMobSpawning` desactivado. Mientras dura una horda no hay otros asaltos.

**Configuración** (pestaña *Hordas*): `hordeEveryDays` (cada cuántos días; 0 = nunca), `hordeGrowth` (lo que se
endurece cada semana; 0 = siempre como la primera) y `showDayClock` (el contador y el reloj). Para probarla:
`/evolvedwarfare horde [bandits|vikings|pirates] [semana]` lanza ya una horda contra tu base (o contra ti).

## Fase 12 — Armaduras de las facciones

Cada facción tiene **4 armaduras**, de menos a más rango, cada una con casco, peto, grebas y botas (48 piezas). Se ven
**en 3D** (cascos con cuernos, tricornios, capuchas, capas, hombreras, faldones...), sobre el jugador, los NPCs de las
facciones, tus soldados y en el soporte de armaduras.

| Rango | Bandidos | Vikingos | Piratas | Protección | Cuenta como |
|---|---|---|---|---|---|
| 1 | **Ropas de forajido**: capucha con pañuelo rojo, chaleco, calzas remendadas, botas vendadas | **Pieles de cazador**: capucha de lobo, chaleco de pieles, calzas con vendas, botas de pieles | **Ropa de grumete**: pañuelo de lunares, camisa a rayas, calzones de lona, zapatos de hebilla | 7 (como el cuero) | ligera |
| 2 | **Cuero de salteador**: gorro con orejeras y embozo, jubón tachonado, botas altas | **Cota de guerrero vikingo**: yelmo de nasal, cota de malla, calzas vendadas | **Atuendo de bucanero**: sombrero de ala ancha, casaca de cuero con bandolera, botas de vuelta | 12 (como la malla) | ligera |
| 3 | **Brigantina de bandolero**: capacete oxidado sobre almófar de malla, brigantina roja tachonada | **Armadura de huscarle**: yelmo de anteojos con malla, coraza de láminas | **Equipo de corsario**: tricornio, coraza de acero sobre casaca azul | 15 (como el hierro) | pesada |
| 4 | **Armadura del caudillo**: yelmo cerrado negro con calavera y cresta, coraza negra, capa roja | **Armadura del jarl**: yelmo con cuernos y oro, cota escamada, manto y capa de oso | **Galas del capitán pirata**: tricornio con calavera y plumas, casaca roja con galones de oro | 19 (dureza 1,5) | pesada |

- **Cada facción tiene su toque**: las vikingas son más duras (+0,5 de dureza desde el rango 2; el jarl, algo de
  resistencia al empuje), las piratas se encantan mejor y las de los bandidos duran más.
- Para el árbol de habilidades, las de rango 1 y 2 son **armadura ligera** y las de 3 y 4, **pesada**.
- Se encantan, se reparan (cuero las de rango 1, hierro las demás) y valen para Remiendo como las de siempre.

**Cómo se consiguen**
- **Las llevan sus guerreros** según su clase: el capitán, la de rango 4 entera; los guerreros, la de rango 2 (3 en difícil
  y en las hordas de élite); arqueros y médicos, la de rango 1, y ballesteros, la de 2; los berserkers van sin peto. A veces sueltan alguna pieza. Los
  mercenarios que te prestan los aliados vienen con la de su facción.
- **Fabricarlas** (mesa de crafteo, con forma de casco, peto, grebas o botas): cuero (rango 1), cadenas (2) o lingotes
  de hierro (3 y 4, con un diamante las de rango 4), más el material de la facción en el centro: **tinte negro** los
  bandidos, **piel de conejo** los vikingos y **pepitas de oro** los piratas. Hacen falta las ventajas de forja:
  *Sastrería de facción* (Armadura ligera; rangos 1 y 2), *Armería de facción* (Armadura pesada, nivel 20; rango 3) y
  *Forja de capitanes* (Armadura pesada, nivel 50; rango 4).
- **En sus cofres**: campamentos, fuertes y fortalezas de bandidos, cabañas y casas largas vikingas, campamentos, barcos y
  tesoros piratas (cuanto mejor el sitio, más rango).
- **El armero** de las ciudades las vende todas: rango 1 común, 2 poco común, 3 rara y 4 épica (según el nivel de la
  ciudad).

## Herramientas (`tools/`)
Las estructuras se generan con scripts de Python (no hace falta tocarlos para jugar):
- `ew_structures.py` — diseña cada edificio bloque a bloque (tres estilos de ciudad, campamentos, asentamientos,
  castillo y fortalezas) y escribe los `.nbt` y los JSON de generación.
- `ew_jigsaw_sim.py` — simula cómo el juego monta las ciudades (encaje de piezas, tamaño, nº de NPCs).
- `ew_preview.py` — dibuja vistas previas isométricas.
- `ew_textures.py` — genera las texturas (aspectos de los reclutas, contrato, interfaz).
- `ew_buildings.py` — compila el generador de edificios (Java puro) y dibuja los diseños de cada oficio con varias semillas.
- `ew_blocks.py` — texturas, modelos, recetas y botín de los puestos de trabajo, el ayuntamiento, el almacén y la hoja de ruta.
- `ew_factions.py` — aspectos de vikingos y piratas, bandera blanca y sello de embajador.
- `ew_loot.py` — botín de los cofres de vikingos, piratas, castillos y fortalezas, y de los propios vikingos y piratas
  (con las armaduras de cada facción).
- `ew_armor.py` (+ `ew_armor_sets.py`, `ew_armor_paint.py`, `ew_armor_raster.py`) — las armaduras de las facciones: los
  diseños 3D (cubos sobre cabeza, cuerpo, brazos y piernas), las texturas, los iconos, `FactionArmorModels.java`, los
  modelos de objeto, las recetas, las etiquetas y los textos; con `--preview` las dibuja puestas (delante, detrás y
  andando).
- `ew_architect.py` — texturas, modelos y recetas de la mesa del arquitecto, el escáner y el plano.
- `ew_weapons.py` — iconos, recetas (con la mejora a netherita) y etiquetas de las armas medievales, el arco largo y la
  jabalina, y la mesa de investigación.
- `ew_melee.py` (+ `ew_melee_preview.py`) — las armas cuerpo a cuerpo en 3D: piezas, texturas por material, las clases
  `MeleeModels.java`, `MeleeAnimations.java` (fotogramas clave del golpe, de la carga y del golpe fuerte, en primera y
  tercera persona) y `MeleeSwing.java` (duración de cada golpe y de cada carga), los modelos de objeto y vistas previas
  de cada golpe y del golpe fuerte.
- `ew_siege.py` — iconos de las máquinas de asedio y su munición, y texturas, modelos y recetas del rastrillo, el
  puente levadizo y el torno.
- `ew_siege_models.py` (+ `ew_siege_preview.py`) — los modelos 3D de la catapulta, la balista, el trebuchet y el ariete:
  piezas, textura, la clase `SiegeModels.java`, los asientos y vistas previas (cargada, disparando y el balanceo del
  trebuchet).
- `ew_towers.py` (+ `java/TowerDump.java`) — vistas previas de las torres de vigilancia: compila `TowerDesigner.java` (Java
  puro, el mismo que usa el juego) y dibuja las torres de varias semillas y estilos, avisando si alguna no se puede subir.
- `ew_village.py` — prototipo de las aldeas vanilla mejoradas (mismo algoritmo que `VillageExtras.java`) con vista previa.
- `ew_skills.py` — los textos del árbol de habilidades (nombres y descripciones de las 11 habilidades y las 79 ventajas
  en todos los idiomas, comprobando que cuadran con `Perk.java`) y el icono del sangrado.
- `ew_musket.py` (+ `ew_musket_preview.py`) — el mosquete en 3D: piezas, textura, la clase `MusketModel.java` y vistas
  previas de la recarga y el disparo en primera y tercera persona.
- `ew_sounds.py` — sonidos sintetizados del mosquete (`.ogg`).
- `ew_voices.py` — las voces de los NPCs, sintetizadas por formantes (un timbre por facción, sus frases y quejas), sus
  entradas en `sounds.json` y los subtítulos; con `--preview carpeta` dibuja el espectrograma de cada voz.
- `ew_pouch.py` — iconos de la Bolsa de Gloria y de la bolsa de munición.
- `ew_cart.py` — la carreta: iconos de la rueda, la plancha y la carreta, la pantalla de la carga vista desde arriba, las
  recetas y los textos; con `--preview carpeta`, vistas en 3D (vacía, cargada, tirando tú y con caballo) con las mismas
  medidas que `CartEntityRenderer.java`.
- `ew_guide.py` — la Guía: icono, el libro abierto de la pantalla, la receta y los textos de las cuatro secciones;
  comprueba con los anchos de la letra del juego que cada página cabe, y con `--preview carpeta` dibuja cómo quedan.
- `ew_crate.py` (+ `ew_crate_preview.py`) — la Caja de cultivos: texturas, modelo con asas, pantalla, receta, botín
  (suelta la caja llena), la etiqueta de lo que cabe y los textos; y vistas previas de cómo se lleva con las dos manos
  (primera y tercera persona, al cogerla y andando), con las mismas cuentas que `CrateCarry.java`.

```
python3 tools/ew_structures.py src/main/resources/data/evolvedwarfare
python3 tools/ew_jigsaw_sim.py city 60          # o desert_city, snow_city
```

## Compilar
Cada vez que se suben cambios, GitHub Actions compila el mod (pestaña **Actions** → artefacto `evolved-warfare`).
En un PC: `./gradlew build` (Java 21+). Para probar: `./gradlew runClient`.

**Pruebas dentro del juego**: `./gradlew runGametest` arranca un servidor sin pantalla que coloca cada máquina de asedio
(también a los pies del jugador), se sube y dispara, comprueba que el ariete golpea y que el tiro de la vista aérea va
al punto marcado; que el golpe con cada arma dura lo que le toca; y 800 torres de vigilancia (se suben, todos sus bloques
existen, salen distintas) y una colocada de verdad con su botín y sus reclutas; y que el árbol de habilidades está bien
hecho, que las habilidades suben al usarlas y dan puntos, que las ventajas desbloquean la fabricación y suman daño, y que
la investigación de antes pasa al árbol; y que cada facción habla con su voz y todos sus sonidos existen; y que la
Caja de cultivos guarda un solo cultivo hasta 64, se coge y se pone sin perder nada, se lleva con las dos manos, y que
los comerciantes solo compran los cultivos en cajas llenas; y que la guía se da una sola vez; y la carreta (recetas,
la cama de dos huecos, lo de dentro al quitar algo, el horno que funde, tirar tú, el caballo, dormir en ella y romperla);
y que el golpe fuerte cargado hace el doble, que soltarlo antes no golpea y que con escudo el clic derecho sigue siendo
del escudo; y las hordas (cada 7 días, más duras cada semana, van a por ti aunque seas aliado de su facción, la base es
tu ayuntamiento y derrotarlas da Glory Coins); y las armaduras de las facciones (cada rango protege más, ligeras y
pesadas, recetas con su ventaja de forja y los NPCs vestidos según su rango); y que el rastrillo y el puente levadizo
se mueven con animación y quedan en su sitio (rastrillo arriba y abajo, puente de pie y tumbado). GitHub las pasa en cada cambio
(trabajo `gametest`) y deja el registro en la rama `gametest-logs`. El código está en `src/gametest` y no va en el `.jar`.
