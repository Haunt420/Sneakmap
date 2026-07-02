# NetInsight - Design and Aesthetic Specification

This document details the visual style, interactive mechanics, UX architecture, and structural layout choices engineered into the **NetInsight** application.

---

## 1. Visual Theme & Aesthetic Identity

NetInsight adheres to a specialized **"Cosmic Cybernetic Dark Theme"** tailored specifically for professional network analysis, discovery, and systems architecture auditing.

```
┌────────────────────────────────────────────────────────┐
│                   COSMIC CYBERNETIC DARK               │
├─────────────────┬──────────────────────────────────────┤
│ Deep Canvas Base│ Slate Charcoal (Color(0xFF0F172A))  │
│ Primary Accent  │ Cyber Cyan     (Color(0xFF22D3EE))  │
│ Alert Warning   │ Coral Amber    (Color(0xFFF59E0B))  │
│ Diagnostic Red  │ Neon Rose      (Color(0xFFEF4444))  │
└─────────────────┴──────────────────────────────────────┘
```

### A. Color Palette & Hierarchy
*   **Deep Canvas Base:** Instead of default pitch-black or flat gray, the app implements a rich Slate-Charcoal colorway that reduces eye strain during prolonged audits and provides depth.
*   **Cyber Cyan Accent (`0xFF22D3EE`):** Serves as the primary focus color. It highlights active nodes, signals ongoing background tasks, outlines selected cards, and styles primary action indicators.
*   **Contrast Layering:** Layered surfaces (cards, logs, detail overlays) use a subtle semi-transparent dark tint (`Color(0x1A000000)`) combined with Material 3 dynamic tonal elevation to stand out cleanly from the canvas base.

### B. Typography Pairings
*   **Display & Interface Headers:** Styled with clean sans-serif typefaces utilizing high-contrast letter-spacing and uppercase tracking.
*   **System and Data Elements:** IP addresses, port listings, terminal logs, and live telemetry outputs utilize monospaced fonts (`FontFamily.Monospace`) to emulate network diagnostic terminals and guarantee clean alignment.

---

## 2. Interactive Force-Directed Topology Visualizer

At the heart of NetInsight's user interface is a custom, real-time physics simulation rendering network nodes on a Jetpack Compose `Canvas`.

```
                    [ REPULSION (Coulomb's Law) ]
                            O  <───>  O
                                 │
                                 │  [ SPRING TENSION ]
                                 ▼
                            O ───────── O
```

### A. Physics-Based Layout Engine
Instead of rigid coordinate tables, the visualizer simulates a living physical environment:
1.  **Repulsive Coulomb Forces:** Every node exerts an electrostatic repulsion force on all other nodes to prevent clutter and encourage self-spreading.
2.  **Spring Attraction:** Connected nodes (e.g., ports/services linked to their parent hosts) pull toward each other based on spring coefficient equations.
3.  **Kinetic Damping:** A friction coefficient is applied frame-by-frame to prevent permanent oscillation, causing the system to settle into a clean, balanced state.
4.  **Anti-Coercion Constraint Guards:** Integrates dynamic safety margins preventing any coercion crash during canvas resize transitions by calculating bounds dynamically based on actual measured width/height:
    $$\text{Bound}_x = \max(60\text{dp}, W - 60\text{dp})$$

### B. Gestures & Fluid Interactions
*   **Multi-Touch Zooming & Panning:** Users can pinch to scale and drag to pan across vast subnet topologies effortlessly.
*   **Dynamic Signal Pulses:** Active hosts emit cyclic glowing ring expansions, visualizing ongoing communication and diagnostics.
*   **Single-Tap Focal Details:** Tapping any node displays a slide-up details sheet showcasing deep service, OS fingerprint, and port analysis.

---

## 3. Structural Layout & UX Choreography

NetInsight coordinates multiple diagnostic panels through fluid transitions and responsive layout patterns.

### A. Navigation & Edge-to-Edge Support
*   **Edge-to-Edge Fluidity:** Utilizes `enableEdgeToEdge()` combined with strict window-inset handling. Status bars and system navigation bars blend seamlessly into the background canvas, while content is padded safely using `WindowInsets.safeDrawing`.
*   **Tab-Based Auditing:** Uses a high-contrast Material 3 bottom navigation bar to transition between the active Topology Simulator, Scans History, Security compliance alerts, and the Live Broadcast terminal.

### B. Motion & Micro-interactions
*   **Details Panel Slider:** The selection of a node activates an elegant slide-up panel:
    *   **Enter Transition:** `slideInVertically(initialOffsetY = { it })`
    *   **Exit Transition:** `slideOutVertically(targetOffsetY = { it })`
*   **Staggered Telemetry Logs:** The websocket telemetry channel uses a reactive flow that slides incoming telemetry lines from the top, drawing attention to active data packets without disrupting structural context.
*   **Automation Warnings:** High-visibility yellow alerts and Toast compliance notifications immediately fire when a newly scanned node breaks security baselines (e.g., exposing open ports 22 or 80).
