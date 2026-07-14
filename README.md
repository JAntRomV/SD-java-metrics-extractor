# Módulo de Análisis Dinámico (Módulo de Carreras y Rendimiento)

### ¿Qué hace este proyecto?
Este programa es un "analizador de rendimiento" para código de Java. A diferencia del análisis estático (que solo lee el texto del código sin correrlo), este módulo **sí ejecuta el código de verdad**. 

Su trabajo es tomar un proyecto externo (como Keycloak), compilarlo, buscar todos sus métodos y usar un cronómetro de alta precisión llamado **JMH** para medir en vivo:
1. **¿Cuánto tiempo tarda** en ejecutarse cada método? (en nanosegundos).
2. **¿Cuánta memoria RAM gasta** al funcionar?

Para que tu computadora no explote ni se congele si el proyecto tiene miles de métodos, el programa es inteligente: los separa en **lotes** (grupos pequeños, por ejemplo, de 50 en 50) y guarda una lista en un archivo de texto para no tener que buscar los métodos desde cero cada vez.

---

### Herramientas que utiliza (Tecnologías)
* **Java 17 y Maven:** La base del proyecto.
* **JMH:** El cronómetro científico ultra preciso para medir el tiempo y la RAM.
* **Reflexión de Java:** Una técnica que le permite a nuestro programa abrir, leer y ejecutar archivos de otro proyecto externo sin necesidad de instalarlos dentro de nuestro código.

---

###  ¿Cómo funciona?

El programa se divide en 5 piezas que trabajan como una línea de producción:

#### 1. `CompiladorProyecto` (El Constructor)
Es el encargado de preparar el proyecto que vamos a medir.
* Revisa que la carpeta exista de forma real.
* Detecta si el proyecto usa **Maven** o **Gradle** y activa la terminal oculta para compilarlo automáticamente (`mvn compile`).


#### 2. `EscaneadorMetodos` (El Filtro de Seguridad)
Entra a las carpetas del proyecto ya compilado y busca qué métodos están listos para competir. Aplica los siguientes filtros estrictos:
* Descarta clases raras o invisibles del sistema (las que tienen un `$` en el nombre).
* Solo acepta clases que tengan un constructor vacío (es decir, que se puedan crear de forma básica).
* Solo acepta métodos que **no reciban parámetros** (0 variables de entrada).
* Al final, anota los métodos sobrevivientes en un archivo llamado `catalogo_metodos.txt` usando la etiqueta `Clase#metodo`.

#### 3. `EjecutorDinamico` (El Jefe / Orquestador)
Es el cerebro del programa (`main`). Controla todo el experimento en orden:
* Lee las órdenes y parámetros que le pones en la terminal.
* Manda a compilar el proyecto y abre el catálogo de métodos.
* Corta la lista gigante en el grupo (lote) que le pediste evaluar.
* Configura los cronómetros de JMH (cuánta memoria usar y cuántas vueltas darle al código).
* Guarda los resultados limpios en una carpeta ordenada por lotes.

#### 4. `MetodoBenchmark` (El Cronómetro Real)
Es donde se realiza el experimento científico sobre cada método.
* **Antes de correr (`@Setup`):** Toma la etiqueta `Clase#metodo`, busca el método real en la memoria y crea una copia del objeto por reflexión.
* **En la carrera (`@Benchmark`):** Corre el método miles de veces. Usa una herramienta llamada `Blackhole` (Hoyo Negro) que se "traga" los resultados del método para obligar a la computadora a procesarlo completo y evitar que el sistema haga trampa ignorando código.

#### 5. `ResultadoDinamico` (El Contenedor de Datos)
Es una plantilla simple que guarda la información de cada método que ya compitió: su nombre, su clase, su tiempo promedio, su margen de error y la RAM que gastó.


---

### Cómo se ejecuta el programa

Para poner a andar el analizador dinámico en tu terminal de Ubuntu, sigues estos pasos:

1. **Limpiar y empaquetar tu proyecto:**
   ```bash
   mvn clean package
   java -jar target/benchmarks.jar --proyecto:/ruta/de/tu/proyecto-externo --batchSize:50 --batchIndex:0 --I:5 --WI:1 --F:1
   -proyecto: La ubicación del proyecto que quieres medir (se compila solo). Nota: Puedes cambiarlo por --ruta si ya está compilado y quieres ir más rápido.

--batchSize:50: Mide los métodos en grupos de 50 en 50 para cuidar tu memoria RAM.

--batchIndex:0: Corre el primer grupo (el grupo cero). Para el siguiente grupo usarías el 1, luego el 2, etc.

--I:5 / --WI:1 / --F:1: Da 1 vuelta de calentamiento, 5 vueltas de medición real y usa 1 proceso aislado (fork) para que la medición sea perfecta.