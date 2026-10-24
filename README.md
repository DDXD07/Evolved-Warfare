# Evolved Warfare — Minecraft 1.21.1 (Fabric)

NPCs, ejércitos, trabajadores, comercio, facciones con diplomacia y asedio. Inspirado en *Ancient Warfare*, con código, nombre y texturas propios.

Requiere **Fabric API**. Opcional: **Golden Coin Merchants** (los reclutas aceptan Golden Coins como paga, hay una receta de contrato con monedas y es la moneda del comerciante; sin el mod, el comerciante usa esmeraldas).

## Hoja de ruta

| Fase | Contenido | Estado |
|---|---|---|
| 1 | **NPC base (el recluta)** y dónde vive: ciudades, campamentos y torres generados; contrato, dueño, equipo, órdenes, paga diaria. Aldeas vanilla con empedrado, farolas y empalizada | ✅ Hecha |
| 2 | **Soldados y bandidos**: guerrero, bárbaro, arquero, ballestero, médico; bandidos hostiles en fuertes, campamentos, patrullas y asaltos; niveles y bastón de mando | ✅ Hecha |
| 3 | **Trabajadores**: 14 oficios con su puesto de trabajo (granja, tala, mina, excavación, pesca, ganado, corral, colmenas, huerto, forja, cocina, carbonera, cantería, flechas) y ayuntamiento que paga solo | ✅ Hecha |
| 4 | **Logística y comercio**: almacén en red con buscador, mensajero (automático o con hoja de ruta), comerciante con tienda para jugadores y venta de excedentes (Golden Coin o esmeraldas) | ✅ Hecha |
| 5 | **Facciones y diplomacia**: vikingos y piratas con sus asentamientos, patrullas y asaltos (también a tu pueblo); reputación con cada facción, bandera blanca, tributos, treguas, alianzas, ayuda de aliados y mercenarios | ✅ Hecha |
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
Receta: lingote de oro, libro y lingote de oro arriba; tablón, cofre y tablón en medio; 3 tablones abajo. Guarda comida o Golden Coins dentro y cada pocos segundos rellena las provisiones de todos tus NPCs (trabajadores, soldados y reclutas) a menos de 48 bloques, hasta 3 días de paga. Así no tienes que ir dándoles de comer uno a uno.

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

**Moneda**: Golden Coin si está instalado *Golden Coin Merchants*; si no, esmeraldas.

## Fase 5 — Facciones y diplomacia

### Las facciones
| Facción | Dónde | Clases | Detalles |
|---|---|---|---|
| **Bandidos** | Llanuras, bosques, sabanas, badlands (no en tierras frías) | Guerrero, bárbaro, arquero, ballestero, curandero, capitán | No respetan la bandera blanca y nunca serán tus aliados, pero se les puede sobornar. |
| **Vikingos** | Taigas, llanuras nevadas, arboledas, colinas | Huscarl (hacha o espada con escudo), berserker, arquero, völva (curandera), jarl | Más duros. El **berserker** entra en furia con poca vida (fuerza, velocidad y resistencia). El **jarl** toca el cuerno de guerra y da fuerza a los suyos. Escudos rojos y blancos. |
| **Piratas** | Playas y costas | Pirata (alfanje), tirador (ballesta, a veces con flechas incendiarias), matón (hacha), cirujano de a bordo, capitán | No se ahogan. El **capitán** se toma un trago de ron cuando está herido. Escudos negros con calavera. |

Todas las facciones son enemigas entre sí: si se cruzan, pelean.

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
el de sus flechas): nivel 5 = +40 %, nivel 10 = +90 %. Al acercarte a menos de 24 bloques sale en pantalla su nombre,
su nivel (verde 1-3, dorado 4-6, rojo 7-8, morado 9-10) y cómo te trata su facción.

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
  botones (se paga con monedas: Golden Coin o esmeraldas):
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
/evolvedwarfare raid <bandits|vikings|pirates>                  lanzar un asalto
```

## Herramientas (`tools/`)
Las estructuras se generan con scripts de Python (no hace falta tocarlos para jugar):
- `ew_structures.py` — diseña cada edificio bloque a bloque y escribe los `.nbt` y los JSON de generación.
- `ew_jigsaw_sim.py` — simula cómo el juego monta las ciudades (encaje de piezas, tamaño, nº de NPCs).
- `ew_preview.py` — dibuja vistas previas isométricas.
- `ew_textures.py` — genera las texturas (aspectos de los reclutas, contrato, interfaz).
- `ew_buildings.py` — compila el generador de edificios (Java puro) y dibuja los diseños de cada oficio con varias semillas.
- `ew_blocks.py` — texturas, modelos, recetas y botín de los puestos de trabajo, el ayuntamiento, el almacén y la hoja de ruta.
- `ew_factions.py` — aspectos de vikingos y piratas, bandera blanca y sello de embajador.
- `ew_loot.py` — botín de los cofres de vikingos y piratas y de los propios vikingos y piratas.
- `ew_village.py` — prototipo de las aldeas vanilla mejoradas (mismo algoritmo que `VillageExtras.java`) con vista previa.

```
python3 tools/ew_structures.py src/main/resources/data/evolvedwarfare
python3 tools/ew_jigsaw_sim.py city 60
```

## Compilar
Cada vez que se suben cambios, GitHub Actions compila el mod (pestaña **Actions** → artefacto `evolved-warfare`).
En un PC: `./gradlew build` (Java 21+). Para probar: `./gradlew runClient`.
