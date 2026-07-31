# Changelog

All notable DigammaXR changes are documented here.

## 10.0.0 - 2026-07-31

Initial DigammaXR release for Minecraft Java Edition 1.20.4 and Fabric.

### Added

- Native OpenXR support through Virtual Desktop's VDXR runtime.
- Vulkan-backed OpenXR presentation path designed for Intel Arc graphics.
- Tracked VR hands and Touch Plus controller input.
- Left-stick movement and right-stick snap turning.
- Controller actions for attacking, interacting, jumping, opening the game menu and inventory, sneaking, and changing the selected hotbar slot.
- VR menus, room-scale interaction, teleporting, and comfort settings inherited from the Vivecraft gameplay base.
- New DigammaXR name, metadata, logo, documentation, and source release.

### Compatibility

- Minecraft Java Edition 1.20.4.
- Fabric Loader on Windows with Java 17.
- Tested with Intel Arc graphics, Meta Quest 3S, Touch Plus controllers, and Virtual Desktop VDXR.
- Client-side installation; Sodium is optional.
- SteamVR and OpenVR are not used by this build.

### Notes

- Do not install DigammaXR and Vivecraft in the same Minecraft instance.
- Hardware, controller profiles, and OpenXR runtimes outside the tested setup may require additional compatibility work.
