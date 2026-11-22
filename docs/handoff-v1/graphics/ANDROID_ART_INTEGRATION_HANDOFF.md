# EMBERFALL — Android Art Reconstruction & Integration Contract v0.4

## Source and package identity

The approved source is the exact user-selected artboard:
- 1536×1024 px.
- SHA-256: `2d12913ff593a528473d50c80f252a05598f40c959e9c8e9400cf537614ed476`.
- Stored as `reference/approved_base_visual_identity.png` inside companion graphics ZIP.
- Design reference only; never package as a runtime screen texture.

The companion full source bundle is `EMBERFALL_ANDROID_GRAPHICS_v0.4.zip`; a smaller resource-only bundle is `EMBERFALL_ANDROID_RESOURCES_v0.4.zip`. The archive binaries are supplied with the project handoff and are not committed as image blobs in this repo branch.

## Class identity lock

- Ashen Monk — Ember/Moss; first, mandatory; existing painterly portrait preserved.
- Quarry Witch — Tide/Violet; board repaint + Spectrum.
- The Unlit — Gold/Ash; Ash-to-fuel; v0.4 painterly portrait candidate replaces the vector placeholder.
- Shade is an encounter based on the previous dead deck, never a playable class.
- Act I boss is The Hollow Warden.

## Included reconstructed surfaces

Ten 390×844 dp screen SVGs + 3× preview PNGs:
Title/Main Menu, Class Select, Run Map, Combat, Card Detail, Relic Gallery, Event, Bonfire, Shade Encounter, Boss/Hollow Warden.

Graphic library:
- 7 reusable SVG components.
- 12 independent SVG layers.
- 8 glass block/neutral tiles with glyphs.
- Card art and hue frames.
- Relic icons, enemy/boss art, act environments and VFX.
- Palette/dimensions XML, button background XML and 78 WebP Android drawables.
- Resource CSV, asset manifest, provenance register and preview browser.
- Compose samples for class selection, combat, ornate controls and drawable mapping.

## Import sequence

1. Download/extract the companion graphics ZIP into the game repository under a source-controlled location such as `design/emberfall-graphics/`.
2. Merge `android/app/src/main/res/` from the bundle into the actual app's `app/src/main/res/`; resolve any duplicate resources before build.
3. Add the four sample files from `android/sample/` to the app's UI package and adapt `com.emberfall.R` to the real package.
4. Keep SVG masters, art source files, screen previews, asset manifest and provenance register in source control.
5. Use `ui/screens/*.svg` as the 390×844 layout reference; build native Compose/View screens from components, not from screenshot PNGs.
6. Keep `reference/approved_base_visual_identity.png` out of Android `res/`.
7. Re-run the package's export, preview and static validation tools after changes.

## Runtime boundaries

- Android/UI owns navigation, drawing, touch routing, accessibility, animation and live text.
- Game core owns legal group detection, Tap counts, Ink, Spectrum Run, card costs/effects, Wall, enemy intents and all damage calculations.
- Combat screen has 49 typed cells in row-major order; upcoming Wall row is a separate preview model.
- Enemy preview must display the two intents returned by the game engine; UI must not simulate them.
- Every tile renders glyph + hue. Ash/Stone/Prism are unique neutral IDs.
- Cards are composed from art/frame plus live cost/name/type/rules/upgrade state.
- Class select uses canonical IDs in canonical order; its lower relic panel updates from selected class data.

## Acceptance gates

Static package gate: 10 screens, 7 components, 12 layers, 84 SVGs, 78 Android drawables, all SVGs parse and all local references resolve.

Actual app integration remains pending:
- Gradle build, lint and test in the real Android app repository.
- Device screenshots at 360×800, 390×844 and 412×915.
- 48dp touch areas, large text, grayscale glyph visibility, screen-reader semantics, reduced motion and haptics controls.
- 60fps target measurement on a mid-range Android device.
- Independent Maestro Mastery review on the exact app candidate SHA.

No native app build/device pass is claimed by the graphics handoff.
