# NetInsight: Android Network Discovery Platform
## Architectural Design Specification

This document provides the exhaustive technical design for the NetInsight application, an educational and professional network discovery tool built for authorized environments.

### I. Core Architecture
*   **Execution Engine (Android NDK & JNI):** The Nmap binary is pre-compiled for `arm64-v8a` using the Android NDK. The Android application interfaces with this binary via a Java Native Interface (JNI) bridge. To ensure scans continue uninterrupted, the execution engine is wrapped in an Android **Foreground Service** with a persistent notification, preventing the OS from killing the process when the user switches apps.
*   **Data Layer (Room Database):** While a pure graph database like Realm is an option, Android's native **Room Persistence Library** (backed by SQLite) is used to simulate a graph structure for maximum compatibility.
    *   **Schema:** `ScanEntity`, `HostEntity`, `PortEntity`, and `ScriptResultEntity`.
    *   Foreign keys explicitly link `HostEntity` to a `ScanEntity`, and `PortEntity` to a `HostEntity`, establishing the relational nodes.
*   **UI Layer (Jetpack Compose):** The entire user interface is built declaratively using Jetpack Compose, enabling 60fps animations for the topology map and immediate state-driven updates for the command builder.

### II. Hyper-Instructive User Interface
*   **Visual Command Constructor Canvas:** The UI utilizes Compose `ViewModel` and `StateFlow`. As the user toggles `Switch` or `Slider` composables (e.g., Aggressiveness `-T1` to `-T5`), the `ViewModel` updates a core `ScanConfig` data class.
*   **Dynamic "Long-Press to Learn" Tooltips:** Implemented using Compose's pointer input modifier (`Modifier.pointerInput`). A long press triggers a `ModalBottomSheet` or a custom `Popup` that reads from a bundled JSON dictionary of Nmap flags, displaying educational details about the flag's network signature (e.g., explaining the TCP handshake differences).
*   **Live Translation Bar:** A persistent `BottomAppBar` uses `derivedStateOf` to continuously observe the `ScanConfig` data class and format it into a raw Nmap command string (e.g., `nmap -sS -T4 -p- 192.168.1.0/24`).

### III. Real-Time Visualization & Output
*   **Force-Directed Topology Mapping:** Built using the Jetpack Compose `Canvas` API. Discovered nodes are represented as data objects with `(x, y)` velocity coordinates. A Kotlin Coroutine runs a basic physics simulation loop (e.g., Fruchterman-Reingold algorithm) using `withFrameNanos` to calculate repulsive forces between nodes and attractive forces along edges (network links), updating their `Offset` state in real-time.
*   **Dynamic Color Coding:** Compose `animateColorAsState` transitions node colors smoothly based on data events (e.g., Green for `Host Up`, Yellow for `Open Ports Detected`).
*   **Contextual Zooming:** `Modifier.transformable` allows pinch-to-zoom and panning across the `Canvas`. Tapping a node intersects the touch coordinates, triggering a state change that overlays an asset-specific dashboard panel.

### IV. Local Databasing and Temporal State Management
*   **Temporal Diffing Engine:** When two historical scans are selected, the app performs a local diffing algorithm in a background `Dispatchers.Default` coroutine. It compares the `List<PortEntity>` of two identical IPs across different `ScanEntity` IDs.
    *   *Result:* An enumerated list of `ChangeType.ADDED`, `ChangeType.REMOVED`, or `ChangeType.MODIFIED` is emitted and visualized in a split-pane Compose layout.
*   **Universal Search:** Room's built-in Full-Text Search (FTS4) extension is utilized. A global `OutlinedTextField` triggers SQLite queries via DAO methods like `@Query("SELECT * FROM hosts JOIN ports ON hosts.id = ports.host_id WHERE ports.service LIKE :query")`.

### V. Data Export and Real-Time Integration
*   **Secure Local Streaming (Ktor):** The app embeds a lightweight Ktor Server (`io.ktor:ktor-server-netty`).
    *   When local streaming is enabled, a WebSocket endpoint (`/ws/scan-stream`) is opened.
    *   As the Nmap binary outputs XML results, the app parses them locally and broadcasts structured JSON over the local WebSocket to any authorized machine on the same subnet listening to the port. No external cloud endpoints are utilized.

### VI. Advanced Scan Technique Presets
*   **Technique Showcase Profiles:** The app features a standard `LazyColumn` listing profiles. Selecting the "Fragment & Decoy" profile updates the `ScanConfig` state to include `-f` and `-D RND:10`. An educational card dynamically appears to explain how packet fragmentation tests the reassembly capabilities of local firewalls for auditing purposes.
*   **Visual NSE Manager:** The app ships with a curated index of the `.nse` library. When a user selects a script (e.g., `http-title`), the app dynamically generates input fields (`OutlinedTextField`) based on the script's documented `@args` metadata, ensuring syntax-perfect execution for authorized vulnerability scanning workflows.

### VII. Post-Scan Automation Triggers
*   **Rule Engine (WorkManager):** Utilizing Android's `WorkManager` API for guaranteed execution.
    *   Upon scan completion, a `ScanResultWorker` analyzes the database.
    *   If a specific port (e.g., 80) is flagged open, the Worker uses Retrofit to make a local HTTP GET request to capture headers.
    *   `NotificationCompat.Builder` is used to fire a high-priority local push notification to the operator if predefined high-interest services are discovered.
