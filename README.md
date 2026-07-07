# Módulo de Extracción de Caminos Sintácticos y Orquestación del Análisis

## 1. Descripción del Proyecto
Este módulo forma parte del framework de extracción de métricas estáticas. Su función es recorrer el Árbol de Sintaxis Abstracta (AST) de cada método para representarlo de dos formas complementarias: como **caminos de control de flujo** (recorriendo el método en el orden en que se ejecutaría, marcando las bifurcaciones de los `if/else`) y como **trillizos Code2Seq** (parejas de nodos terminales unidas por la ruta que las conecta en el árbol). Todo este trabajo es coordinado por `ProcesadorMetricas`, que además valida que los trillizos generados sí puedan reconstruirse hasta el código original.

## 2. Tecnologías Utilizadas
* **Lenguaje:** Java 21
* **Gestor de dependencias:** Maven
* **Librerías extra:** JavaParser 3.25.10 (para analizar y recorrer el árbol de sintaxis del código, configurado en `BLEEDING_EDGE` para soportar la sintaxis más reciente de Java) y JUnit 5.10.2 (para la ejecución de las pruebas unitarias).

## 3. Estructura del Proceso

### 3.1 ProcesadorMetricas
Es la clase orquestadora: recibe la carpeta con los proyectos Java y la carpeta de resultados, y por cada archivo `.java` sigue este flujo:
1. **Parseo del archivo:** Usa un `JavaParser` propio configurado en `BLEEDING_EDGE` para poder leer sintaxis moderna de Java. Si el archivo no se puede leer, se salta y se reporta al final, sin detener el resto del lote.
2. **Poda de métodos anidados:** Antes de sacar métricas, revisa si hay un método declarado dentro de otro (por ejemplo, dentro de una clase local o anónima). Si lo encuentra, primero le saca sus propias métricas por separado, le resta esas líneas al método que lo contiene, y después lo quita del árbol para que no se cuente dos veces.
3. **Extracción de métricas y caminos:** Manda el árbol ya podado a `MetricsAnalyzer` (Halstead, Complejidad Ciclomática, CFG), a `ArbolCaminoExtractor` (caminos de control de flujo) y usa `Code2SeqExtractor` para sacar los trillizos de cada método.
4. **Validación cruzada:** Con los trillizos obtenidos, reconstruye fragmentos de código y verifica que esas líneas reconstruidas sí existan dentro del cuerpo original de los métodos, como una comprobación de que la extracción fue correcta.
5. **Reporte final:** Registra el peso en disco de cada archivo y el conteo de clases/métodos del proyecto (incluyendo los que estaban anidados), y al final delega a `MetricsExporter` la generación de los archivos JSON y CSV.

### 3.2 ArbolCaminoExtractor
Se encarga de representar cada método como una lista de **caminos de control de flujo**, siguiendo este proceso:
1. **Numeración de nodos:** Recorre el cuerpo del método de arriba hacia abajo (preorden) y le asigna un número consecutivo a cada pieza de código, para poder identificarlas después.
2. **Bifurcación en los `if`:** Cuando encuentra un `if`, abre dos caminos distintos: uno para cuando la condición es verdadera y otro para cuando hay un `else`, cada uno arrastrando el texto y los números acumulados hasta ese punto.
3. **Camino secuencial:** Para cualquier otro tipo de instrucción, guarda su texto (limpio y en una sola línea) y su número asignado, y sigue bajando por sus piezas más chicas.
4. **Cierre del camino:** Cuando ya no hay más piezas hacia abajo, junta todo el texto acumulado en un solo renglón (separado por `|`) y lo guarda junto con su serie de números, siempre que no sea un camino repetido.

### 3.3 Code2SeqExtractor
El núcleo de la conversión de código a secuencias de caminos, con este flujo de procesamiento:
1. **Recolección de Terminales:** El extractor analiza un método (`MethodDeclaration`) e identifica todos los nodos terminales, dividiéndolos en variables (nombres de variables, parámetros) y valores fijos (números, cadenas, booleanos).
2. **Selección de Parejas:** Mediante un doble ciclo iterativo, el extractor empareja de manera única cada nodo terminal con los elementos que se encuentran adelante de él en la estructura, evitando duplicaciones o caminos inversos.
3. **Ascenso en el Árbol AST:** Para cada pareja de terminales, el algoritmo escala de abajo hacia arriba a través de los nodos padres intermedios (como declaraciones, asignaciones u operaciones binarias), marcando el ancestro común con un `*` para poder identificarlo después.
4. **Filtrado y Registro:** El trillizo resultante (`nodo_terminal_1 | camino_en_ast | nodo_terminal_2`) se añade a la lista del método para ser exportado posteriormente en el reporte JSON final.
5. **Reconstrucción y Validación:** Además de generar los trillizos, esta clase sabe hacer el camino inverso: a partir de un trillizo cuya ruta es de un solo nodo (una pareja directamente conectada, como una declaración o una asignación), reconstruye el fragmento de código que representa (por ejemplo `int x=5`), lo cual permite comprobar que la extracción coincide con el código real.

## 4. Cómo Ejecutar el Proyecto
Para correr este proyecto necesitas:
1. Tener instalado Maven y Java 21 en tu equipo.
2. Descargar o abrir el repositorio en tu entorno de desarrollo (como VS Code).
3. Para ejecutar las pruebas unitarias y verificar las fórmulas, corre en la terminal: `mvn clean test`
4. Para ejecutar el comando principal y generar tus reportes de métricas, corre en la terminal: `mvn clean compile exec:java -Dexec.mainClass="estatica.App"`