# ForkXR

**The only simple way to play Minecraft Java in VR on Intel Arc graphics cards.**

ForkXR is a Vivecraft fork for Minecraft Java Edition 1.20.4. It brings
Vivecraft-style VR gameplay to Intel Arc systems through Virtual Desktop,
including tracked hands, motion controls, joystick movement, snap turning,
room-scale interaction, VR menus, and inventory controls.

ForkXR is a Fabric client mod. It does not use SteamVR or OpenVR.

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

Sodium is optional. Do not install ForkXR and Vivecraft together.

## Installation

1. Install Fabric Loader for Minecraft 1.20.4.
2. Put `ForkXR.jar` in the instance's `mods` folder.
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

ForkXR can connect to vanilla Minecraft servers without a server-side
installation. Some Vivecraft features, including unrestricted teleporting,
may require compatible server support. Server anti-cheat systems may also
restrict room-scale or non-standard VR movement.

## Building

ForkXR includes the Gradle Wrapper, so a separate Gradle installation is not
required.

### Requirements

- Windows 10 or Windows 11
- A 64-bit JDK 17 installation
- An internet connection for the first build
- Git, if the source is being cloned instead of downloaded

Confirm that Java 17 is available:

```powershell
java -version
```

The output must report Java 17. A Java Runtime Environment alone is not
enough; Gradle needs a complete JDK.

### Compile from source

1. Clone the repository or download and extract its source archive.
2. Open PowerShell in the repository directory. This is the directory that
   contains `gradlew.bat`, `build.gradle`, `common`, and `fabric`.
3. Run the complete Fabric build:

```powershell
.\gradlew.bat clean :fabric:build --no-daemon
```

The first build takes longer because Gradle downloads Minecraft, Fabric, LWJGL,
and the other compile-time dependencies automatically. A separate Minecraft
JAR does not need to be copied into the project.

After a successful build, the distributable files are available in
`fabric/build/libs`:

- `ForkXR-10.0.0-fabric.jar` — the mod file for a Fabric `mods` folder;
- `ForkXR-10.0.0-sources.jar` — the matching source archive.

Do not distribute the `dev-shadow` JAR. It is an intermediate development
artifact and is not remapped for a normal Minecraft installation.

The command finishes with `BUILD SUCCESSFUL` when compilation, resource
processing, access-widener validation, remapping, and the available Gradle
checks have completed successfully.

### Build without cleaning

For faster builds after making a small code change, omit `clean`:

```powershell
.\gradlew.bat :fabric:build --no-daemon
```

### Project layout

- `common/src/main/java` — shared Vivecraft and ForkXR implementation;
- `common/src/main/resources` — shared assets, mixins, and controller data;
- `fabric/src/main/java` — Fabric-specific entrypoints and platform code;
- `fabric/src/main/resources` — Fabric metadata;
- `stubs` — compile-time compatibility declarations;
- `gradle` — Gradle Wrapper files.

## Reporting problems

When reporting a problem, include:

- the Minecraft log or crash report;
- the Intel Arc GPU model;
- the headset and controller model;
- the Virtual Desktop version;
- confirmation that VDXR is the selected runtime;
- clear steps that reproduce the problem.

## Credits and license

ForkXR is a fork of [Vivecraft](https://github.com/Vivecraft/VivecraftMod).
The inherited VR rendering, gameplay, GUI, locomotion, networking, and
compatibility systems originate from the Vivecraft project. See
[`FORKXR_NOTICE.md`](FORKXR_NOTICE.md) for detailed attribution.

ForkXR is licensed under the
[GNU Lesser General Public License v3.0 or later](LICENSE.md).

ForkXR is not an official release from Vivecraft, Mojang, Microsoft, Meta,
Intel, or Virtual Desktop.
