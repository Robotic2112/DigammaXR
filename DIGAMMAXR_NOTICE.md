# DigammaXR 10.0.0

DigammaXR is an LGPL-3.0-or-later derivative of Vivecraft for Minecraft 1.20.4.

The room-scale renderer, gameplay systems, GUI, controller interaction,
locomotion, hand rendering, networking, compatibility mixins, and other
Vivecraft-derived portions originate from:

- Project: Vivecraft
- Source: https://github.com/Vivecraft/VivecraftMod
- OpenXR base revision: `d7651229abe191dd40227b509e59adccdb3c76fc`
- Copyright: the Vivecraft contributors recorded in the upstream repository
  history and project metadata at the base revision
- License: GNU Lesser General Public License v3.0 or later (`LICENSE.md`)

DigammaXR-specific changes replace the OpenVR/SteamVR provider with a Windows
native OpenXR provider for Virtual Desktop VDXR, add a Vulkan presentation
bridge for Intel Arc GPUs, select the Meta Quest Touch controller interaction
profile, and preserve Vivecraft's OpenGL room-scale rendering pipeline.

No OpenVR, SteamVR, or legacy Oculus PC SDK native library is bundled in the
DigammaXR binary.
