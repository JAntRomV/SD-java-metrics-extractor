# SD-java-metrics-extractor
This framework is developed as a tool that allows the extraction of static and dynamic metrics for training machine learning and deep learning models oriented towards software performance analysis.

# Módulo de Extracción de Métricas Estáticas y Caminos Sintácticos

## 1. Descripción del Proyecto
Este proyecto es un sistema desarrollado en Java diseñado para el análisis estático de código fuente. Su función principal es inspeccionar archivos .java para extraer métricas de calidad (como Halstead y Complejidad Ciclomática) y, de forma centralizada, mapear la estructura sintáctica del código mediante la extracción de caminos en el Árbol de Sintaxis Abstracta (AST) basados en el enfoque Code2Seq, permitiendo representar los métodos como trillizos (nodo_terminal_1 | camino_en_ast | nodo_terminal_2).

## 2. Tecnologías Utilizadas
* **Lenguaje:** Java 17
* **Gestor de dependencias:** Maven
* **Librerías extra:** JavaParser (para analizar y recorrer el árbol de sintaxis del código) y JUnit 5 (para la ejecución de las pruebas unitarias).

## 3. Estructura del Proceso
El núcleo de la conversión de código a secuencias de caminos se ejecuta en la clase Code2SeqExtractor, la cual sigue este flujo de procesamiento:

1. Recolección de Terminales: El extractor analiza un método (MethodDeclaration) e identifica todos los nodos terminales, dividiéndolos en variables (nombres de variables, parámetros) y valores fijos (números, cadenas, booleanos).

2. Selección de Parejas: Mediante un doble ciclo iterativo, el extractor empareja de manera única cada nodo terminal con los elementos que se encuentran adelante de él en la estructura, evitando duplicaciones o caminos inversos.

3. Ascenso en el Árbol AST: Para cada pareja de terminales, el algoritmo escala de abajo hacia arriba a través de los nodos padres intermedios (como declaraciones, operaciones condicionales o bucles), traduciendo los nombres técnicos complejos de JavaParser a abstracciones simplificadas (BucleDoWhile, Condicion(if), Defines una variable).

4. Filtrado y Registro: Si el camino entre la pareja es válido y se conecta de forma lógica, el trillizo resultante se añade a la lista del método para ser exportado posteriormente en el reporte JSON final.

## 4. Cómo Ejecutar el Proyecto
Para correr este proyecto necesitas:
1. Tener instalado Maven y Java 17 en tu equipo.
2. Descargar o abrir el repositorio en tu entorno de desarrollo (como VS Code).
3. Para ejecutar las pruebas unitarias y verificar las fórmulas, corre en la terminal: `mvn clean test`
4. Para ejecutar el comando principal y generar tus reportes de métricas, corre en la terminal: `mvn clean compile exec:java -Dexec.mainClass="estatica.App"`
