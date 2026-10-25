# AOSP Fluid Animations

LSPosed module for Android 16 / Evolution X that replaces AOSP app-transition physics with three profiles:

- **HyperOS 4** — cubic Bézier `(0.20, 0.00, 0.00, 1.00)`, scale `0.85 → 1.0`.
- **ColorOS** — under-damped spring, stiffness `300`, damping `0.80`, optional SurfaceFlinger background blur.
- **OriginOS 7** — expressive spring with controlled bounce, scale and parallax skew.

The module hooks Android 16 `WindowContainer#loadAnimation`, feeds the result through `WindowAnimationSpec` / `SurfaceAnimationRunner`, and animates the real `SurfaceControl` leash. Cancellation applies a direct transaction (`scale 0.1`, `alpha 0`) before WMS resets the leash, preventing the full-screen jump. First-frame skipping is enabled only for supported app transitions.

No background services, polling loops, or persistent workers are used.

## Install

1. Install the release APK.
2. Enable **AOSP Fluid Animations** in LSPosed.
3. Scope it to **Android System** (`android`).
4. Reboot.
5. Open **AOSP Fluid Animations** and choose a profile.
