# GeneXR

**The only way to play Minecraft VR easily on Intel Arc graphics.**

GeneXR is a Vivecraft fork for Minecraft Java Edition 1.20.4. It brings
Vivecraft-style VR gameplay to Intel Arc systems through Virtual Desktop,
including tracked hands, motion controls, joystick movement, snap turning,
room-scale interaction, VR menus, and inventory controls.

GeneXR is a Fabric client mod. It does not use SteamVR or OpenVR.

## Requirements

- Windows
- Minecraft Java Edition 1.20.4
- Fabric Loader
- Java 17
- Virtual Desktop with VDXR selected as the OpenXR runtime
- Meta Quest 3S with Touch Plus controllers

The current release is designed and tested for this setup. Other headsets,
controller profiles, operating systems, and OpenXR runtimes are not currently
supported.

Sodium is optional. Do not install GeneXR and Vivecraft together.

## Installation

1. Install Fabric Loader for Minecraft 1.20.4.
2. Put `GeneXR.jar` in the instance's `mods` folder.
3. Open Virtual Desktop Streamer and select VDXR as its OpenXR runtime.
4. Connect the headset through Virtual Desktop.
5. Start Minecraft with the Fabric profile.

## Default controls

| Controller input | Action |
| --- | --- |
| Left thumbstick | Move |
| Right thumbstick | Snap turn |
| Right trigger | Attack |
| Left trigger | Use or interact |
| A | Jump |
| X | Game menu |
| Y | Inventory |
| Left grip | Sneak |
| Right grip | Select the next hotbar slot |

In menus, the right trigger selects, the right thumbstick scrolls, the left
grip acts as Shift, and the right grip acts as a middle click.

## Multiplayer

GeneXR can connect to vanilla Minecraft servers without a server-side
installation. Some Vivecraft features, including unrestricted teleporting,
may require compatible server support. Server anti-cheat systems may also
restrict room-scale or non-standard VR movement.

## Building

On Windows, run:

```powershell
.\gradlew.bat :fabric:build
```

The Fabric JAR and source JAR are created in `fabric/build/libs`.

## Reporting problems

When reporting a problem, include:

- the Minecraft log or crash report;
- the Intel Arc GPU model;
- the headset and controller model;
- the Virtual Desktop version;
- confirmation that VDXR is the selected runtime;
- clear steps that reproduce the problem.

## Credits and license

GeneXR is a fork of [Vivecraft](https://github.com/Vivecraft/VivecraftMod).
The inherited VR rendering, gameplay, GUI, locomotion, networking, and
compatibility systems originate from the Vivecraft project. See
[`GENEXR_NOTICE.md`](GENEXR_NOTICE.md) for detailed attribution.

GeneXR is licensed under the
[GNU Lesser General Public License v3.0 or later](LICENSE.md).

GeneXR is not an official release from Vivecraft, Mojang, Microsoft, Meta,
Intel, or Virtual Desktop.
