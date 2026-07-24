# Módulo de Análisis Dinámico (Módulo de Carreras y Rendimiento)

### ¿Qué hace este proyecto?
Este programa es un "analizador de rendimiento" para código de Java. A diferencia del análisis estático (que solo lee el texto del código sin correrlo), este módulo **sí ejecuta el código de verdad**. 

Su trabajo es tomar un proyecto externo (como Keycloak), compilarlo, buscar todos sus métodos y medir en vivo mediante dos enfoques de cronómetro:
1. **Rendimiento General (JMH):** Mide en nanosegundos el tiempo promedio total que tarda cada método y cuánta memoria RAM gasta al funcionar miles de veces.
2. **Rendimiento Línea por Línea / Caminos (Instrumentación):** Inyecta "marcas de tiempo" directamente en el código fuente `.java` (dentro de cada instrucción, `if`, `else`, etc.) para registrar paso a paso cómo se ejecuta el método instrucción por instrucción.

Para que tu computadora no explote ni se congele si el proyecto tiene miles de métodos, el programa es inteligente: los separa en **lotes** (grupos pequeños, por ejemplo, de 50 en 50) y guarda una lista en un archivo de texto para no tener que buscar los métodos desde cero cada vez.

---

### Herramientas que utiliza (Tecnologías)
* **Java 17 y Maven:** La base del proyecto.
* **JMH:** El cronómetro científico ultra preciso para medir el tiempo y la RAM.
* **JavaParser:** Herramienta de "cirugía de código" para leer la estructura del código `.java` e inyectar marcas de tiempo automáticamente sin romper el archivo original.
* **Reflexión de Java:** Una técnica que le permite a nuestro programa abrir, leer y ejecutar archivos de otro proyecto externo sin necesidad de instalarlos dentro de nuestro código.
* **ThreadLocal & CSV:** Para registrar y sincronizar los tiempos de las instrucciones en tiempo real por cada hilo sin contaminar los datos entre pruebas.

---

### Modos de Ejecución (`ModeMapper`)
El programa permite elegir qué tipo de análisis quieres realizar mediante el parámetro `--modo`:
* **`completo` (por defecto):** Ejecuta tanto el Benchmark de JMH como el análisis de caminos línea por línea.
* **`fase1` / `benchmark`:** Corre únicamente las mediciones generales de tiempo y RAM con JMH.
* **`fase2` / `caminos`:** Corre únicamente el análisis detallado paso a paso inyectando cronómetros línea por línea.

---

###  ¿Cómo funciona?

El programa se divide en piezas especializadas que trabajan en cadena:

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
* Lee las órdenes y parámetros que le pones en la terminal (`--modo`, `--proyecto`, `--batchSize`, etc.).
* Manda a compilar el proyecto y abre el catálogo de métodos.
* Corta la lista gigante en el grupo (lote) que le pediste evaluar.
* Configura los cronómetros de JMH o de caminos según el modo elegido.
* Guarda los resultados limpios en una carpeta ordenada por lotes.

#### 4. `MetodoBenchmark` (El Cronómetro Real - JMH)
Es donde se realiza el experimento de rendimiento global sobre cada método.
* **Antes de correr (`@Setup`):** Toma la etiqueta `Clase#metodo`, busca el método real en la memoria y crea una copia del objeto por reflexión.
* **En la carrera (`@Benchmark`):** Corre el método miles de veces. Usa una herramienta llamada `Blackhole` (Hoyo Negro) que se "traga" los resultados del método para obligar a la computadora a procesarlo completo y evitar que el sistema haga trampa ignorando código.

#### 5. `InstrumentadorCaminos` (La Cirugía de Código)
Si se activa la fase de caminos:
* Toma el código fuente del archivo `.java` y usa **JavaParser** para leer su estructura.
* Inyecta llamadas a `RegistradorTiempos.marcar("INSTR-X")` antes de cada línea de código, incluyendo las instrucciones dentro de los bloques `if`, `else` y bucles.
* Genera una versión modificada en una carpeta de salida sin alterar nunca el archivo original.

#### 6. `RegistradorTiempos` y `TimeLogger` (El Cronómetro Línea por Línea)
Son los encargados de atrapar las marcas inyectadas durante la ejecución real:
* **`RegistradorTiempos`:** Maneja el estado global del medidor para el hilo actual (`ThreadLocal`) asegurando que el cronómetro se encienda, marque y apague limpiamente.
* **`TimeLogger`:** Es la libreta de anotaciones. Cada vez que el código pasa por una línea marcada, guarda un "fotograma": ID de registro, iteración actual, clase, método, marca del nanosegundo y la fecha/hora exacta.
* Al finalizar la prueba, exporta todo a un archivo `.csv` detallado.

#### 7. `ResultadoDinamico` (El Contenedor de Datos)
Es una plantilla simple que guarda la información de cada método que ya compitió: su nombre, su clase, su tiempo promedio, su margen de error y la RAM que gastó. Mantiene la llave de unión `Clase#metodo` para conectarse perfectamente con los reportes estáticos.


---

### Cómo se ejecuta el programa

Para poner a andar el analizador dinámico en tu terminal de Ubuntu, sigues estos pasos:

1. **Limpiar y empaquetar tu proyecto:**
   ```bash
   mvn clean package
   java -cp "target/classes:target/dependency/*" dinamica.EjecutorCompleto --proyecto:/home/tania/Documentos/ejemplojava/ai-git-bot-main
   ```

-