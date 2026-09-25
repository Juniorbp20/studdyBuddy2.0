# Compañero de Estudios

Aplicación Android de gestión de tareas y hábitos de estudio, construida con **Kotlin + Views (XML)** y **Material 3**.

## Características

- **Gestión de tareas** con título, descripción, fecha/hora, categoría y prioridad (Alta, Media, Baja).
- **Categorías personalizables**: 4 por defecto (General, Estudio, Trabajo, Personal) con icono y color, más categorías propias creadas desde la app (8 iconos, paleta de colores) y borrado con reasignación automática de tareas a General.
- **Tareas recurrentes** (diarias/semanales) que se reprograman automáticamente al completarse.
- **Subtareas** con cascada al eliminar la tarea padre.
- **Etiquetas** (tags) con filtro por chips.
- **Búsqueda** y filtros combinados por categoría y prioridad.
- **Recordatorios** con notificaciones y canales dedicados (requiere permiso de notificaciones y alarmas exactas).
- **Resumen diario de tareas vencidas** vía WorkManager.
- **Calendario mensual** con indicador visual de días con tareas pendientes.
- **Modo enfoque (Pomodoro)** con duración configurable (Enfoque y Descanso en minutos, persistida), vibración y registro de sesiones.
- **Estadísticas**: progreso de completado, resumen (total, completadas, pendientes, vencidas, hoy, última semana), gráfico de los últimos 7 días y sesiones de enfoque.
- **Widget de escritorio** "Tareas de hoy" con lista actualizable.
- **Exportar/importar** tareas en JSON y CSV.
- **Tema oscuro y Material You** (colores dinámicos en Android 12+), tema claro/oscuro/sistema desde el menú.
- **Deshacer** al eliminar tareas (snackbar).
- **Actualizaciones desde GitHub Releases**: menú → "Buscar actualizaciones" compara la versión instalada con la última release del CD y descarga/instala el APK (requiere permiso "instalar desde orígenes desconocidos").
- **Anuncio de inicio (App Open Ad de AdMob)**: se muestra al volver a la app desde segundo plano; se omite en el primer arranque (política de AdMob). En builds de debug se usa el ID de prueba de Google; en release, la unidad real.
- **Multilingüe**: español (es) e inglés (en).

## Tecnologías

- Kotlin 2.0, corrutinas y Flow
- ViewBinding
- Room 2.6 (migraciones v1→v2→v3→v4 versionadas)
- Material Components 3, temas con Material You
- AlarmManager + WorkManager + notificaciones
- App Widgets (RemoteViewsService)
- Google Mobile Ads (App Open Ad, `play-services-ads` 24.2.0)
- Splash Screen API (androidx.core)

## Estructura

```
app/src/main/java/com/example/studybuddy/
├── MainActivity.kt          # Lista principal: búsqueda, filtros, chips, swipe, undo
├── App.kt                   # Application: inicialización AdMob + anuncio de inicio
├── AddTaskActivity.kt       # Crear/editar tarea
├── TaskDetailActivity.kt    # Detalle + subtareas
├── CalendarActivity.kt      # Calendario mensual
├── FocusActivity.kt         # Pomodoro
├── StatisticsActivity.kt    # Estadísticas y gráfico
├── adapter/                 # TaskAdapter, SubTaskAdapter
├── data/                    # ServiceLocator, TaskRepository (Flow)
├── model/                   # Task, SubTask, CategoryEntity, DAOs, AppDatabase + migraciones
├── notification/            # AlarmReceiver, AlarmManagerHelper, NotificationHelper, DailyOverdueWorker
├── ui/                      # TaskViewModel (Flow + LiveData)
├── util/                    # DateUtils, ExportImportHelper, BackupHelper, FocusSessionStore
├── view/                    # BarChartView, MonthCalendarView
└── widget/                  # TodayTasksWidgetProvider + Service
```

## Requisitos

- Android Studio (JDK 21)
- minSdk 23 · targetSdk 36

## Compilar y probar

```bash
./gradlew assembleDebug        # APK de debug
./gradlew lintDebug            # análisis estático
./gradlew testDebugUnitTest    # tests unitarios
./gradlew connectedDebugAndroidTest  # tests de instrumentación (migraciones Room)
```

Los tests de migración validan las rutas v1→v4, v2→v4 y v3→v4 usando los esquemas exportados en `app/schemas/`.

## CI / CD

- `.github/workflows/android.yml` — CI en cada push/PR a `main`/`master`: tests unitarios, lint y build del APK (subido como artefacto).
- `.github/workflows/cd.yml` — CD al publicar un tag `v*` (o manual): tests, lint, build del APK de release y publicación de una GitHub Release con el APK adjunto.

### Publicar una versión

1. Sube el `versionCode` y `versionName` en `app/build.gradle.kts`.
2. Crea y sube un tag con el mismo número: `git tag v5 && git push origin v5`.
3. El CD genera la release; la app la detecta en "Buscar actualizaciones".

El APK se firma con el keystore versionado en `keystore/studybuddy.jks` (alias/contraseña: `studybuddy`), el mismo que usan los builds de debug, por lo que la actualización se instala sobre cualquier instalación previa.

## Licencia

Uso personal/educativo.