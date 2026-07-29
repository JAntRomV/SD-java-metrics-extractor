# Módulo de Integración (El Director de Orquesta)

### ¿Qué hace este proyecto?
Este módulo es el **punto de entrada único** de todo el sistema. Hasta ahora tenías dos programas separados: uno que lee el código sin correrlo (análisis estático) y otro que lo compila y lo ejecuta de verdad para cronometrarlo (análisis dinámico). Correr los dos a mano, uno tras otro, significa acordarte de las rutas correctas, no mezclar las carpetas de resultados, y esperar a que termine uno para lanzar el otro.

`AnalizadorUnificado` resuelve eso: le das la ruta de UN proyecto externo (como Keycloak) una sola vez, y él se encarga de mandarlo primero al análisis estático y después al dinámico, en el orden correcto, guardando cada resultado en su propia carpeta para que nunca se mezclen los archivos de uno con los del otro.

Es literalmente un director de orquesta: no toca ningún instrumento él mismo (no analiza código, no compila nada, no corre JMH), solo le da la entrada a cada músico (`ProcesadorMetricas` y `EjecutorCompleto`) en el momento justo.

---

### Herramientas que utiliza (Tecnologías)
* **Java 17 y Maven:** La base del proyecto, igual que los otros dos módulos.
* **`estatica.ProcesadorMetricas`:** El módulo de análisis estático completo, ya armado. Este módulo solo lo invoca, no repite su lógica.
* **`dinamica.EjecutorCompleto`:** El orquestador del módulo dinámico (que a su vez ya corre las 2 fases de JMH internamente). Este módulo tampoco repite esa lógica, solo lo llama.

---

### ¿Cómo funciona?

#### 1. `AnalizadorUnificado` (El Director)
Es la única clase de este módulo, y hace 3 cosas en orden:

* **Lee los argumentos de la terminal.** Necesita al menos `--proyecto` (la ruta al proyecto externo). Si no se lo das, te avisa el modo de uso correcto y se detiene ahí, sin tronar.
* **Corre el análisis estático primero.** Llama a `ProcesadorMetricas.analizarUnProyecto(...)`, apuntando su salida a una subcarpeta propia: `resultados/resultados_estaticos`. Va primero porque es rápido (solo lee texto, no compila nada) y así detecta de una vez archivos con sintaxis rara antes de gastar tiempo compilando el proyecto completo.
* **Corre el análisis dinámico después.** Llama a `EjecutorCompleto.main(...)`, pero antes le cambia (o le agrega) el parámetro `--salida` para que apunte a su propia subcarpeta: `resultados/resultados_dinamicos`. Cualquier otro parámetro que hayas puesto en la terminal (`--batchSize`, `--I`, `--WI`, `--classpath`, etc.) se le pasa intacto, tal cual lo escribiste — este módulo no necesita entender esos parámetros, solo reenviarlos.

#### 2. `conSalidaOverride` (El truco de la subcarpeta)
Es un método pequeño pero importante: toma el arreglo original de argumentos que escribiste en la terminal, y si encuentra un `--salida:algo`, lo reemplaza por la subcarpeta correcta antes de pasárselo a `EjecutorCompleto`. Si no pusiste `--salida` en absoluto, simplemente se lo agrega al final. Así nunca terminas con los CSV del análisis dinámico mezclados en la misma carpeta que los JSON del estático.

---

### Cómo se ejecuta el programa

Para poner a andar el análisis unificado en tu terminal de Ubuntu, sigues estos pasos:

1. **Empaquetar el proyecto completo:**
```bash
   mvn clean package
```

2. **Correr el análisis unificado sobre un proyecto externo:**
```bash
   java -cp target/estatica-framework-2.0.0-jar-with-dependencies.jar \
       integracion.AnalizadorUnificado \
       --proyecto:/home/tania/Documentos/ejemplojava/keycloak-config-cli-main \
       --salida:resultados
```

3. **(Opcional) Ajustar los parámetros de JMH de la fase dinámica**, exactamente igual que si llamaras a `EjecutorCompleto` directamente — se pasan tal cual:
```bash
   java -cp target/estatica-framework-2.0.0-jar-with-dependencies.jar \
       integracion.AnalizadorUnificado \
       --proyecto:/home/tania/Documentos/ejemplojava/keycloak-config-cli-main \
       --salida:resultados \
       --batchSize:30 --I:5 --WI:1
```

Al terminar, vas a encontrar todo ordenado así:

```
resultados/
├── resultados_estaticos/
│   └── keycloak-config-cli-main/
│       ├── <Clase>Metricas.json
│       ├── <Clase>Metricas.csv
│       ├── <Clase>_code2seq.json
│       └── <Clase>_caminos.json
└── resultados_dinamicos/
    ├── Benchmarks.csv
    ├── cronometro_caminos.csv
    ├── _escaneo_resumen.txt
    ├── _caminos_resumen.txt
    └── _caminos_no_seguibles.txt
```