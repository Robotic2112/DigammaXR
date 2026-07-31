const docs = {
home:{title:"DigammaXR",lede:"Complete installation, configuration and troubleshooting documentation.",html:`
<aside class="wiki-infobox"><h3>DigammaXR</h3><img src="../digammaxr-logo.svg" alt="DigammaXR logo"><dl><dt>Type</dt><dd>VR client mod</dd><dt>Game</dt><dd>Minecraft Java</dd><dt>Version</dt><dd>1.20.4</dd><dt>Loader</dt><dd>Fabric</dd><dt>Platform</dt><dd>Windows</dd><dt>Runtime</dt><dd>VDXR OpenXR</dd><dt>License</dt><dd>LGPL-3.0-or-later</dd></dl></aside>
<div class="callout"><strong>Start here.</strong> This guide assumes Minecraft Java 1.20.4, Fabric, Windows, Virtual Desktop with VDXR, and a Quest headset. Do not skip the startup order.</div>
<h2 id="choose">Choose what you need</h2>
<div class="cards">
<a class="card" href="install.html"><strong>Install DigammaXR</strong><span>Build a clean Fabric instance and add the correct JAR.</span></a>
<a class="card" href="vdxr.html"><strong>Configure VDXR</strong><span>Set Virtual Desktop as the active OpenXR path.</span></a>
<a class="card" href="controls.html"><strong>Controller setup</strong><span>Movement, turning, triggers, grips and menu controls.</span></a>
<a class="card" href="troubleshooting.html"><strong>Fix a problem</strong><span>Dark headset, missing controllers, crashes, HUD and performance.</span></a>
</div>
<h2 id="known-good">Known-good baseline</h2>
<table><tr><th>Component</th><th>Baseline</th></tr><tr><td>Game</td><td>Minecraft Java Edition 1.20.4</td></tr><tr><td>Loader</td><td>Fabric Loader with 64-bit Java 17</td></tr><tr><td>Runtime</td><td>Virtual Desktop Streamer, VDXR selected</td></tr><tr><td>Headset</td><td>Meta Quest 3S with Touch Plus controllers</td></tr><tr><td>GPU target</td><td>Intel Arc on a current Windows driver</td></tr></table>
<h2 id="order">Correct startup order</h2>
<ol class="steps"><li>Open Virtual Desktop Streamer on the PC and confirm VDXR is selected.</li><li>Put on the headset, open Virtual Desktop and connect to the PC.</li><li>Wake both controllers and confirm both are tracked.</li><li>Launch the Fabric 1.20.4 instance.</li><li>Wait for the VR view, then enter a world.</li></ol>
<h2 id="before-reporting">Before reporting a bug</h2>
<ul class="checklist"><li>Tested with only DigammaXR and required loader components</li><li>Confirmed there is only one DigammaXR or Vivecraft-derived JAR</li><li>Confirmed VDXR, not SteamVR, is active</li><li>Collected the full latest.log or crash report</li></ul>`},
install:{title:"Installation",lede:"Create a clean, reproducible Minecraft instance before adding performance or content mods.",html:`
<h2 id="files">What you need</h2><ul><li>The DigammaXR JAR matching Minecraft 1.20.4 and Fabric.</li><li>A 64-bit Java 17 runtime.</li><li>Fabric Loader for Minecraft 1.20.4.</li><li>Virtual Desktop on the PC and Quest headset.</li></ul>
<div class="callout warning"><strong>Do not install DigammaXR together with Vivecraft.</strong> Two VR renderers in the same instance can cause crashes, duplicate mixins or invalid rendering.</div>
<h2 id="prism">Prism Launcher</h2><ol class="steps"><li>Create a new instance and select Minecraft 1.20.4.</li><li>Open <em>Edit → Version</em>, choose <em>Install Fabric</em>, and use a current compatible Fabric Loader.</li><li>Open <em>Settings → Java</em> for the instance and select a 64-bit Java 17 executable.</li><li>Open the <em>Mods</em> page and add the DigammaXR JAR.</li><li>Launch once with no shaders, weather overhauls or camera mods.</li></ol>
<h3>Clean-instance checklist</h3><ul class="checklist"><li>One DigammaXR JAR</li><li>No Vivecraft JAR</li><li>No MCXR JAR</li><li>No OptiFine</li><li>Minecraft version is exactly 1.20.4</li><li>Java major version is 17</li></ul>
<h2 id="standard">Standard Minecraft launcher</h2><ol><li>Install Fabric Loader for 1.20.4.</li><li>Open the game directory used by that profile.</li><li>Create a <code>mods</code> directory if it does not exist.</li><li>Copy DigammaXR into <code>mods</code>.</li><li>Launch the Fabric profile only after Virtual Desktop is connected.</li></ol>
<h2 id="verify">Verify the installation</h2><p>Open the launcher log and confirm Fabric lists DigammaXR as loaded. If Minecraft starts as a normal flat window, continue with the <a href="vdxr.html">VDXR setup</a>. If Fabric rejects the JAR, verify the Minecraft version, loader and Java version before doing anything else.</p>`},
vdxr:{title:"Virtual Desktop and VDXR",lede:"DigammaXR depends on the active OpenXR runtime and the order in which the headset, controllers and game are started.",html:`
<h2 id="runtime">Select VDXR</h2><ol class="steps"><li>Close Minecraft and SteamVR.</li><li>Open Virtual Desktop Streamer on Windows.</li><li>Select VDXR as the preferred OpenXR runtime.</li><li>Open Virtual Desktop in the Quest headset and connect to the PC.</li><li>Verify the performance overlay reports VDXR when available.</li></ol>
<h2 id="controllers">Controller readiness</h2><p>Wake both controllers before starting Minecraft. If only one controller appears in the Quest interface, Minecraft cannot repair that tracking state. Replace or reseat batteries, wake the controller, and reconnect Virtual Desktop.</p>
<h2 id="refresh">Refresh rate</h2><p>Use a refresh rate exposed by the active Virtual Desktop session. Begin with the stable setting already working in Virtual Desktop. A refresh-rate mismatch should not be diagnosed by repeatedly replacing the mod JAR; first verify the streaming session itself.</p>
<h2 id="steamvr">SteamVR opens unexpectedly</h2><ol><li>Close Minecraft.</li><li>Exit SteamVR completely.</li><li>Re-select VDXR in Virtual Desktop Streamer.</li><li>Disconnect and reconnect Virtual Desktop.</li><li>Launch Minecraft again.</li></ol>
<div class="callout danger"><strong>Do not switch OpenXR runtimes while Minecraft is running.</strong> Close the game before changing the active runtime.</div>
<h2 id="restart">Clean restart procedure</h2><p>When a session becomes stuck: close Minecraft, exit Virtual Desktop in the headset, close Streamer on the PC, reopen Streamer, reconnect the headset, verify both controllers, and only then relaunch Minecraft.</p>`},
controls:{title:"Controllers and locomotion",lede:"Default bindings, seated play, room-scale movement and controller troubleshooting.",html:`
<h2 id="mapping">Default mapping</h2><table><tr><th>Input</th><th>Action</th></tr><tr><td>Left thumbstick</td><td>Move like Minecraft WASD</td></tr><tr><td>Right thumbstick</td><td>Snap turn</td></tr><tr><td>Right trigger</td><td>Attack or mine</td></tr><tr><td>Left trigger</td><td>Use, place or interact</td></tr><tr><td>A</td><td>Jump fallback</td></tr><tr><td>X</td><td>Game menu</td></tr><tr><td>Y</td><td>Inventory</td></tr><tr><td>Left grip</td><td>Sneak</td></tr><tr><td>Right grip</td><td>Next hotbar slot</td></tr></table>
<h2 id="movement">Free movement</h2><p>The left stick supplies normal player movement. Physical room movement is separate and remains available for leaning or room-scale interaction. If you must walk across the real room to move in-game, confirm free movement is selected and that the left controller is tracked.</p>
<h2 id="seated">Seated mode</h2><ol><li>Sit in the intended play position.</li><li>Recenter the Quest view.</li><li>Select seated play in VR settings.</li><li>Use the left stick to move and right stick to turn.</li><li>Keep auto-jump enabled if climbing single blocks is difficult.</li></ol>
<h2 id="standing">Standing mode</h2><p>Standing uses physical headset height and room-scale controller positions. Clear the play area, set the correct floor height and recenter before entering a world.</p>
<h2 id="missing">One controller is missing</h2><ol><li>Check and reseat the battery.</li><li>Confirm the controller appears in the Quest home interface.</li><li>Confirm it appears in Virtual Desktop before Minecraft starts.</li><li>Reconnect Virtual Desktop.</li><li>Restart Minecraft after both controllers are active.</li></ol>
<h2 id="menus">Menus and cursor</h2><p>Point with the controller and select with the right trigger. If the pointer disappears, test a vanilla menu without menu-overhaul mods. Recenter if the menu is behind or below the player.</p>`},
rendering:{title:"Rendering and image quality",lede:"How the two-eye view behaves and how to diagnose dark, monochrome or misplaced rendering.",html:`
<h2 id="pipeline">Rendering path</h2><p>DigammaXR renders separate views for both eyes and submits them through the active OpenXR session. A healthy desktop mirror does not automatically prove that valid eye images reached the headset.</p>
<h2 id="dark">Absolute darkness</h2><ol><li>Verify VDXR is active and the headset is connected before launch.</li><li>Remove shaders and framebuffer-changing mods.</li><li>Test a clean instance.</li><li>Confirm the world itself renders in the desktop window.</li><li>Collect the full log and note whether menus, HUD, clouds or terrain are visible.</li></ol>
<h2 id="ui-only">Menus visible but world dark</h2><p>This distinguishes GUI composition from world rendering. Disable shaders, volumetric weather, camera replacement and render-pipeline mods. If the clean instance works, reintroduce mods one at a time.</p>
<h2 id="one-eye">Image appears in one corner or one eye</h2><p>Reset HUD position and scale, recenter the play origin, and restore default eye/render settings. Do not try to correct an eye projection problem by moving the Minecraft GUI manually.</p>
<h2 id="color">Black-and-white or incorrect color</h2><p>Disable shader packs, color filters, post-processing and HDR-related overlays. Retest with vanilla resource packs and a clean render path.</p>
<h2 id="quality">Increasing quality safely</h2><ol><li>Establish a stable baseline without shaders.</li><li>Increase render distance in small steps.</li><li>Increase Virtual Desktop streaming quality one step at a time.</li><li>Test movement and turning for several minutes after each change.</li><li>Revert the most recent change if frame timing becomes unstable.</li></ol>`},
performance:{title:"Intel Arc performance",lede:"A methodical tuning guide for stable frame timing without destroying image quality.",html:`
<h2 id="baseline">Baseline first</h2><p>Use a clean instance, a moderate render distance, no shaders and no recording overlays. Confirm controller tracking and world rendering before tuning quality.</p>
<h2 id="driver">Graphics driver</h2><p>Use a current Intel Arc Windows driver. Reboot after a driver update. If a regression begins immediately after updating, record both the old and new driver versions in the issue report.</p>
<h2 id="cpu-gpu">Identify the bottleneck</h2><table><tr><th>Symptom</th><th>Likely area</th></tr><tr><td>Low FPS in dense worlds, stable stream</td><td>Minecraft CPU load, chunks, entities or mods</td></tr><tr><td>Clear desktop mirror, headset compression or latency</td><td>Network or Virtual Desktop streaming settings</td></tr><tr><td>Both desktop and headset stutter</td><td>Game render load, driver or background GPU load</td></tr><tr><td>Stutter only when chunks generate</td><td>CPU, storage or world-generation mods</td></tr></table>
<h2 id="settings">Recommended tuning order</h2><ol class="steps"><li>Reduce render distance.</li><li>Reduce simulation distance.</li><li>Disable shaders and volumetric effects.</li><li>Reduce expensive particles and entity-heavy settings.</li><li>Close browser video, capture software and GPU overlays.</li><li>Then tune Virtual Desktop bitrate and quality.</li></ol>
<h2 id="memory">Java memory</h2><p>More RAM is not automatically faster. Use enough memory for the mod set without assigning nearly all system memory. Excessively large heaps can cause longer garbage-collection pauses.</p>
<h2 id="sodium">Sodium</h2><p>Sodium is optional. Test it separately and keep versions matched to Minecraft 1.20.4. If a visual defect appears only with Sodium, report both the DigammaXR and Sodium versions.</p>`},
compatibility:{title:"Mod compatibility",lede:"How to add mods without losing the ability to identify rendering and input conflicts.",html:`
<h2 id="method">The one-at-a-time method</h2><ol><li>Confirm DigammaXR works alone.</li><li>Add Fabric API if required.</li><li>Add one performance mod and test.</li><li>Add content mods in small groups.</li><li>Add visual, camera and shader mods last.</li></ol>
<h2 id="risk">Compatibility risk by category</h2><table><tr><th>Category</th><th>Risk</th><th>Reason</th></tr><tr><td>Content, recipes, blocks</td><td>Lower</td><td>Usually does not replace the client camera or framebuffer</td></tr><tr><td>Performance</td><td>Medium</td><td>May replace chunk or render internals</td></tr><tr><td>HUD and menus</td><td>Medium</td><td>May assume a flat screen and mouse pointer</td></tr><tr><td>Weather and volumetrics</td><td>High</td><td>May render once or use incompatible depth buffers</td></tr><tr><td>Shaders and camera mods</td><td>Highest</td><td>Directly replace projection, framebuffer or post-processing</td></tr></table>
<h2 id="weather">Weather and volumetric effects</h2><p>A mod can load successfully while rendering incorrectly in one or both eyes. Check terrain, sky, particles, fog and menus independently. Report eye-specific artifacts rather than only saying that the mod “does not work.”</p>
<h2 id="bisect">Find a conflicting mod</h2><p>Copy the instance, disable half of the optional mods, and test. Continue halving the failing set until the conflict is isolated. Keep DigammaXR and required loader components enabled throughout the test.</p>`},
multiplayer:{title:"Multiplayer",lede:"Client-side use, server restrictions and movement features that may trigger anti-cheat.",html:`
<h2 id="client">Client-side installation</h2><p>DigammaXR is installed on the client. A vanilla-compatible server generally does not need the client JAR, but individual content mods may have their own server requirements.</p>
<h2 id="anticheat">Anti-cheat</h2><p>Teleporting, room-scale displacement, crawling, unusual reach and rapid movement can look invalid to server anti-cheat. Follow the server's rules and disable features the server does not permit.</p>
<h2 id="safe">Safe test procedure</h2><ol><li>Test movement in a private or trusted server first.</li><li>Use normal joystick locomotion.</li><li>Avoid teleporting until server rules are known.</li><li>Do not use movement multipliers on public servers.</li><li>Ask staff before using room-scale mechanics competitively.</li></ol>
<h2 id="desync">Position desynchronization</h2><p>If the server repeatedly snaps the player back, return to normal movement settings and test without auto-jump or teleporting. Server correction is not necessarily a rendering bug.</p>`},
troubleshooting:{title:"Troubleshooting index",lede:"Start from the visible symptom. Follow the checks in order and stop when the cause is identified.",html:`
<div class="callout warning"><strong>Preserve evidence.</strong> Before replacing files or deleting configuration, save latest.log and the crash report.</div>
<h2 id="dark">Headset is completely dark</h2><ol><li>Confirm the headset is connected through Virtual Desktop.</li><li>Confirm VDXR is selected.</li><li>Close SteamVR and other OpenXR programs.</li><li>Restart the complete session in the documented order.</li><li>Test a clean instance without shaders.</li></ol>
<h2 id="runtime">No active OpenXR headset/runtime</h2><p>The game launched before a valid VDXR session existed, or another runtime is active. Close Minecraft, reconnect Virtual Desktop, confirm VDXR and relaunch.</p>
<h2 id="vulkan">“Vulkan has already been created”</h2><p>Another initialization path or stale game session attempted to create the graphics backend twice. Close Minecraft completely. Confirm no Java process from the instance remains, then reconnect and relaunch once.</p>
<h2 id="controller">Left or right controller missing</h2><p>Check batteries and tracking outside Minecraft first. The mod cannot create an OpenXR interaction profile for a controller that VDXR does not expose.</p>
<h2 id="stick">Thumbstick does nothing</h2><p>Verify controller tracking, leave the key-binding screen, select controller free movement and test in a world. Confirm a menu is not capturing the stick.</p>
<h2 id="cursor">Cursor missing or follows the head</h2><p>Test a vanilla menu, point with the controller, and remove menu or camera mods. Recenter if the menu plane is misplaced.</p>
<h2 id="hud">Double cursor, low hotbar or misplaced HUD</h2><p>Restore default HUD scale and distance, then recenter the seated/standing origin. Disable GUI overhaul mods during diagnosis.</p>
<h2 id="world">HUD visible but the world is black</h2><p>Disable shaders, volumetric weather and framebuffer-changing mods. This symptom means GUI composition works while world rendering does not.</p>
<h2 id="freeze">Freezing or severe stutter</h2><p>Reduce render and simulation distance, close overlays, test a clean world, update the Intel driver and separate network stutter from game-render stutter.</p>
<h2 id="crash">Crash during startup</h2><ol><li>Check Java 17.</li><li>Check Minecraft 1.20.4 and Fabric.</li><li>Remove duplicate VR mods.</li><li>Read the first “Caused by” section in the crash report.</li><li>Test only required mods.</li></ol>
<h2 id="still">Still not fixed?</h2><p>Continue to <a href="logs.html">Logs and issue reports</a>. Include the exact symptom and the last step that worked.</p>`},
logs:{title:"Logs and issue reports",lede:"A complete report makes rendering, controller and startup failures reproducible.",html:`
<h2 id="latest">latest.log</h2><p>After the failed run, copy the complete <code>logs/latest.log</code> from the instance. Do this before launching again because the next run replaces it.</p>
<h2 id="crash">Crash reports</h2><p>Use the newest file in <code>crash-reports</code>. Include it as text or an attached file. A screenshot of the final stack-trace line is not enough.</p>
<h2 id="prism">Prism Launcher</h2><ol><li>Select the instance.</li><li>Open the Minecraft log after the failure.</li><li>Use the copy or upload function.</li><li>Include the complete result, not only red lines.</li></ol>
<h2 id="template">Issue template</h2><pre><code>DigammaXR version:
Minecraft version:
Fabric Loader version:
Java version:
Intel GPU and driver:
Headset and controllers:
Virtual Desktop version:
VDXR selected: yes/no
Other installed mods:

Expected result:
Actual result:
Exact reproduction steps:
First relevant error:
</code></pre>
<h2 id="privacy">Privacy check</h2><p>Logs may contain the Windows user name, file paths, server addresses or chat. Review the file before posting publicly, but do not remove technical stack traces needed to identify the cause.</p>`},
updates:{title:"Updating, rollback and removal",lede:"Replace builds cleanly and keep a known-working copy available.",html:`
<h2 id="update">Update</h2><ol class="steps"><li>Close Minecraft.</li><li>Back up important worlds and the working JAR.</li><li>Remove the previous DigammaXR JAR from the instance.</li><li>Add the new JAR.</li><li>Confirm only one version is present.</li><li>Test before updating unrelated mods.</li></ol>
<h2 id="rollback">Rollback</h2><p>If a new build fails, save its log, remove it, restore the last known-working JAR and keep all other versions unchanged. This confirms whether the regression belongs to DigammaXR or another simultaneous update.</p>
<h2 id="remove">Remove DigammaXR</h2><p>Delete the DigammaXR JAR from the client's mods folder. The mod does not contain world saves. Keep world backups whenever changing a large client mod set.</p>
<h2 id="config">Reset configuration</h2><p>Reset only DigammaXR/Vivecraft-related client configuration after preserving a copy. Do not delete the entire Minecraft directory to solve one setting problem.</p>`},
building:{title:"Building from source",lede:"Compile the Fabric 1.20.4 project with the included Gradle Wrapper.",html:`
<h2 id="tools">Required tools</h2><ul><li>64-bit JDK 17</li><li>Git</li><li>Internet access for the first dependency download</li><li>The DigammaXR source repository</li></ul>
<h2 id="clone">Clone</h2><pre><code>git clone https://github.com/damianmazanec/DigammaXR.git
cd DigammaXR</code></pre>
<h2 id="build">Build Fabric</h2><pre><code>.\gradlew.bat :fabric:build --no-daemon</code></pre><p>The distributable remapped JAR is produced under <code>fabric/build/libs</code>. Do not distribute development, sources or shadow JARs as the normal launcher mod.</p>
<h2 id="clean">Clean rebuild</h2><pre><code>.\gradlew.bat clean :fabric:build --no-daemon</code></pre>
<h2 id="license">Fork obligations</h2><p>Preserve applicable LGPL licensing and Vivecraft attribution. Source changes to covered code must remain available under the applicable license terms. Do not describe the project as an official Vivecraft, Mojang, Microsoft, Meta, Intel or Virtual Desktop release.</p>`}
};

const order=["home","install","vdxr","controls","rendering","performance","compatibility","multiplayer","troubleshooting","logs","updates","building"];
const groups=[
["Start here",[["home","Overview"],["install","Installation"],["vdxr","Virtual Desktop & VDXR"]]],
["Using DigammaXR",[["controls","Controllers & movement"],["rendering","Rendering & image quality"],["performance","Intel Arc performance"]]],
["Compatibility",[["compatibility","Mod compatibility"],["multiplayer","Multiplayer"]]],
["Help",[["troubleshooting","Troubleshooting index"],["logs","Logs & issue reports"],["updates","Updates & rollback"]]],
["Developers",[["building","Building from source"]]]
];
const filenames={home:"index.html",install:"install.html",vdxr:"vdxr.html",controls:"controls.html",rendering:"rendering.html",performance:"performance.html",compatibility:"compatibility.html",multiplayer:"multiplayer.html",troubleshooting:"troubleshooting.html",logs:"logs.html",updates:"updates.html",building:"building.html"};
const page=document.body.dataset.page||"home";
const data=docs[page]||docs.home;
document.title=`${data.title} — DigammaXR`;
const navHtml=groups.map(([name,items])=>`<div class="nav-group"><strong>${name}</strong>${items.map(([id,label])=>`<a class="${id===page?"active":""}" href="${filenames[id]}">${label}</a>`).join("")}</div>`).join("");
const temp=document.createElement("div");temp.innerHTML=data.html;
const headings=[...temp.querySelectorAll("h2")].map(h=>({id:h.id,text:h.textContent}));
const index=order.indexOf(page);
document.body.innerHTML=`<header class="top"><a class="top-brand" href="../"><img src="../digammaxr-logo.svg" alt="">DigammaXR Wiki</a><div class="top-links"><a href="../">Main site</a><a href="https://github.com/damianmazanec/DigammaXR">GitHub</a></div></header><div class="layout"><aside class="sidebar"><button class="sidebar-title">Contents</button><input class="search" type="search" placeholder="Search contents" aria-label="Search documentation contents"><div class="nav-groups">${navHtml}</div></aside><article class="article"><h1>${data.title}</h1><p class="lede">${data.lede}</p>${data.html}<nav class="page-nav">${index>0?`<a href="${filenames[order[index-1]]}">← ${docs[order[index-1]].title}</a>`:"<span></span>"}${index<order.length-1?`<a href="${filenames[order[index+1]]}">${docs[order[index+1]].title} →</a>`:""}</nav></article><aside class="on-page"><strong>On this page</strong>${headings.map(h=>`<a href="#${h.id}">${h.text}</a>`).join("")}</aside></div>`;
document.querySelector(".sidebar-title").addEventListener("click",()=>document.querySelector(".sidebar").classList.toggle("open"));
document.querySelector(".search").addEventListener("input",event=>{const q=event.target.value.toLowerCase();document.querySelectorAll(".nav-group a").forEach(a=>a.classList.toggle("hidden",!a.textContent.toLowerCase().includes(q)));});
