# SD-java-metrics-extractor
This framework is developed as a tool that allows the extraction of static and dynamic metrics for training machine learning and deep learning models oriented towards software performance analysis.

# Módulo de Extracción de Métricas Estáticas

## 1. Descripción del Proyecto
Este proyecto es un sistema desarrollado en Java que sirve para analizar código fuente de forma estática, extrayendo métricas de calidad como Halstead y Complejidad Ciclomática a nivel de método sin necesidad de ejecutar la aplicación.

## 2. Tecnologías Utilizadas
* **Lenguaje:** Java 17
* **Gestor de dependencias:** Maven
* **Librerías extra:** JavaParser (para analizar y recorrer el árbol de sintaxis del código) y JUnit 5 (para la ejecución de las pruebas unitarias).

## 3. Estructura del Proceso
El camino que sigue el código para realizar el análisis es el siguiente:
1. La clase `App` detecta automáticamente el nombre de la carpeta del proyecto y busca todos los archivos con extensión `.java`.
2. El código procesa la información en la clase `MetricsAnalyzer`, la cual extrae los operadores, operandos y puntos de decisión apoyándose en las calculadoras expertas (`HalsteadCalculator` y `CfgCalculator`).
3. Se genera un reporte organizado por clases y métodos dentro de la carpeta `ProyectoPrueba`, exportando de manera inteligente en formato `.json` (si la clase tiene menos de 20 métodos) o en formato `.csv` (si tiene 20 o más métodos).

## 4. Cómo Ejecutar el Proyecto
Para correr este proyecto necesitas:
1. Tener instalado Maven y Java 17 en tu equipo.
2. Descargar o abrir el repositorio en tu entorno de desarrollo (como VS Code).
3. Para ejecutar las pruebas unitarias y verificar las fórmulas, corre en la terminal: `mvn clean test`
4. Para ejecutar el comando principal y generar tus reportes de métricas, corre en la terminal: `mvn clean compile exec:java -Dexec.mainClass="estatica.App"`
