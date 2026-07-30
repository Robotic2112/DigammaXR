# ForkXR
<img width="2065" height="2065" alt="forkxr_logo" src="https://github.com/user-attachments/assets/4919b7e6-bec6-439f-85db-f67163cfbb05" />
<svg width="2065" height="2065" viewBox="0 0 2065 2065" fill="none" xmlns="http://www.w3.org/2000/svg">
<rect width="2065" height="2065" fill="#404040"/>
<rect x="207" y="574" width="1652" height="918" rx="455" fill="#D9D9D9"/>
<ellipse cx="529.5" cy="1231.5" rx="71.5" ry="66.5" fill="black"/>
<ellipse cx="1494.5" cy="1231.5" rx="71.5" ry="66.5" fill="black"/>
<ellipse cx="672.5" cy="1140.5" rx="71.5" ry="66.5" fill="black"/>
<ellipse cx="1351.5" cy="1140.5" rx="71.5" ry="66.5" fill="black"/>
<ellipse cx="672.5" cy="1298.5" rx="71.5" ry="66.5" fill="black"/>
<ellipse cx="1351.5" cy="1298.5" rx="71.5" ry="66.5" fill="black"/>
<path d="M686.48 789H666.32V621.48H766.16V639.24H686.48V695.4H759.2V713.16H686.48V789ZM839.169 791.88C828.129 791.88 818.369 789.24 809.889 783.96C801.569 778.52 795.009 771 790.209 761.4C785.569 751.64 783.249 740.2 783.249 727.08C783.249 713.96 785.569 702.6 790.209 693C795.009 683.24 801.569 675.72 809.889 670.44C818.369 665 828.129 662.28 839.169 662.28C850.209 662.28 859.889 665 868.209 670.44C876.689 675.72 883.249 683.24 887.889 693C892.689 702.6 895.089 713.96 895.089 727.08C895.089 740.2 892.689 751.64 887.889 761.4C883.249 771 876.689 778.52 868.209 783.96C859.889 789.24 850.209 791.88 839.169 791.88ZM839.169 774.84C849.569 774.84 858.049 771.64 864.609 765.24C871.169 758.84 874.449 749.08 874.449 735.96V718.2C874.449 705.08 871.169 695.32 864.609 688.92C858.049 682.52 849.569 679.32 839.169 679.32C828.929 679.32 820.449 682.52 813.729 688.92C807.169 695.32 803.889 705.08 803.889 718.2V735.96C803.889 749.08 807.169 758.84 813.729 765.24C820.449 771.64 828.929 774.84 839.169 774.84ZM945.866 789H926.666V665.16H945.866V687.96H947.066C948.506 683.96 950.746 680.28 953.786 676.92C956.826 673.4 960.746 670.6 965.546 668.52C970.346 666.28 976.106 665.16 982.826 665.16H990.266V684.36H978.986C972.106 684.36 966.186 685.32 961.226 687.24C956.266 689 952.426 691.4 949.706 694.44C947.146 697.48 945.866 701 945.866 705V789ZM1014.79 789V611.4H1033.99V701.88V723.96H1034.95L1053.91 702.84L1088.95 665.16H1112.23L1066.39 714.12L1116.79 789H1093.51L1052.47 725.88L1033.99 744.84V789H1014.79ZM1126.71 789L1181.43 703.8L1128.15 621.48H1152.39L1194.39 688.2H1194.87L1237.83 621.48H1260.87L1207.11 703.08L1262.31 789H1238.07L1194.15 718.68H1193.67L1149.75 789H1126.71ZM1310.62 717V789H1290.46V621.48H1357.18C1366.94 621.48 1375.26 623.32 1382.14 627C1389.18 630.68 1394.54 636.04 1398.22 643.08C1402.06 650.12 1403.98 658.76 1403.98 669C1403.98 681.16 1401.1 691.24 1395.34 699.24C1389.58 707.24 1381.26 712.52 1370.38 715.08L1408.78 789H1385.98L1349.74 717H1310.62ZM1310.62 699.72H1357.18C1362.46 699.72 1366.94 698.84 1370.62 697.08C1374.46 695.16 1377.34 692.44 1379.26 688.92C1381.34 685.24 1382.38 680.84 1382.38 675.72V663.24C1382.38 658.12 1381.34 653.8 1379.26 650.28C1377.34 646.6 1374.46 643.88 1370.62 642.12C1366.94 640.2 1362.46 639.24 1357.18 639.24H1310.62V699.72Z" fill="black"/>
</svg>


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
