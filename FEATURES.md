# NetInsight - Feature and Functionality Specification

This document provides a comprehensive breakdown of all features and capabilities coded into the **NetInsight** application. Following recent upgrades, multiple previously simulated/placeholder systems have been refactored into fully real, functional, and integrated systems.

---

## 1. Completed Features

These features are fully implemented, structurally sound, error-free, compile successfully, and are integrated into the core database and OS architecture of the application.

### A. Real-Time Comparative Temporal Diff Engine
*   **Database-Driven Diffing:**
    *   Fully integrated with the local SQLite Room database.
    *   Dynamically extracts host and port listings for any two user-selected historical scan records.
    *   Compares lists and generates an authentic, line-by-line delta mapping additions (`➕`), removals (`➖`), and modified characteristics (`▲`).
    *   Tracks newly discovered hosts, newly open ports, closed ports, and slight modifications to OS fingerprints between any two historic point-in-time scans.

### B. Live Hardware & Network Telemetry Broadcaster
*   **Active OS Interface Inspection:**
    *   Queries real-world Android system and hardware parameters to build a fully authentic live stream of server logs.
    *   Scans native network interfaces via `NetworkInterface.getNetworkInterfaces()` to detect actual active physical/virtual interface names, MTU specifications, and bound IP addresses.
    *   Logs JVM resource allocation diagnostics in real-time, checking actual CPU cores (`availableProcessors()`) and available memory.
    *   Integrates with the Room database, outputting reactive statistics (such as total scans recorded and total hosts discovered) as formatted JSON WebSocket broadcast frames.

### C. Active Security Automation Compliance Triggers
*   **Live Compliance Notifications:**
    *   Monitors real-time database insertions during live scanner execution.
    *   Triggers actual system Toast alerts when user-configured threshold options are violated.
    *   Detects open SSH port (22) and open Web ports (80/443) on discovered network nodes, issuing immediate warnings to help administrators secure target hosts.

### D. Modular Data Analysis & Executable Pipeline (`com.example.analysis`)
A robust, decoupled background processor designed to bundle, execute, stream, and parse binary data-processing tools locally.
*   **BinaryAssetManager:**
    *   Verifies, extracts, and caches local ARM64 binary utilities from Android Assets to the private `filesDir`.
    *   Implements checksum/size matching with the original asset package.
    *   Automatically repairs and enforces file executable permissions (`setExecutable(true, false)`).
    *   Executes fully on non-blocking `Dispatchers.IO` threads.
*   **CommandLineRunner:**
    *   Uses `ProcessBuilder` to safely launch local system processes.
    *   Spawns dedicated coroutines on `Dispatchers.IO` to stream standard output (`stdout`) and standard error (`stderr`) streams line-by-line using a callback-based `Flow`.
    *   Ensures clean resource closing, process destruction, and handles coroutine cancellation gracefully.
*   **XmlAnalysisParser:**
    *   An efficient, low-memory, event-driven pull parser utilizing Android's `XmlPullParser`.
    *   Strictly extracts IP addresses, port IDs, protocols, active states, and service identification names from XML formatted output files.
*   **AnalysisPipeline:**
    *   Unifies `BinaryAssetManager`, `CommandLineRunner`, and `XmlAnalysisParser` into a reactive data streaming pipeline.
    *   Accumulates output streams and translates process exit codes into state events (`Started`, `Result`, `Error`).

### E. Background Execution Engine & Notifications
*   **AnalysisService & NmapForegroundService:**
    *   Fully declared in the `AndroidManifest.xml` as standard Android background services of type `dataSync`.
    *   Showcases sticky ongoing foreground notifications alerting users of background data analysis progress.
    *   Exposes reactive `StateFlow` state streams to communicate real-time state changes directly to the UI layer.

### F. Local Database & Persistence Layer (`com.gmap.data`)
*   **Room Database (`NetDatabase`):**
    *   A fully implemented local SQLite database managed via Room.
    *   Features tables and relationships for **Scans**, **Hosts**, and **Ports**.
*   **Data Access Object (`NetDao`):**
    *   Enforces reactive data-flow by exposing database queries as Kotlin asynchronous `Flow` streams.
    *   Provides automatic cascade deletes and bulk insert mechanics to store real-world discoveries.

### G. Advanced Topology Visualizer & Physics Engine
*   **Force-Directed Graph Simulator:**
    *   Renders network relationships on a custom Jetpack Compose `Canvas`.
    *   Computes electrostatic repulsion forces, spring/attraction forces along network edges, and kinetic damping frame-by-frame on a high-performance rendering loop.
    *   Supports dynamic node creation: nodes drift apart, self-stabilize, and dynamically adjust positions when new elements populate the local Room database.
*   **Interactive Controls:**
    *   Includes gesture listeners for panning, multi-touch zooming, pinch scaling, and single-tap node selection.
    *   Provides high-fidelity visuals showing OS logos, connection vectors, active diagnostic signals, and distinct colored states for gateways, switches, and client workstations.

---

## 2. Partially Completed Features

These features have fully-coded frameworks and supporting interfaces, but depend on runtime configurations, user environments, or binary assets to achieve 100% real-world output.

### A. Live Binary Execution
*   **Local Process Invocation:**
    *   The `AnalysisPipeline` and `CommandLineRunner` are fully functional and capable of launching any ARM64 binary. However, the exact behavior in the emulator is dependent on executing under compatible system permissions and having target utilities aligned with standard Linux system libraries.
    *   A simulated fallback interface is provided via mock insertion engines to ensure visual UI responsiveness regardless of background system limitations.

### B. Network Target Scope Modification
*   **Input Scope Parsing:**
    *   The interface accepts standard IP, CIDR notations (e.g., `192.168.1.0/24`), and specific target hosts.
    *   The values are dynamically converted into arguments to build active terminal strings, but deep validation check modules (e.g. validating subnet limits) are partially handled by the underlying execution binaries.
