<div align="center">

# Sign

Replace vanilla player nametags with fast & customizable text display entities.

**Paper 1.21+ · Java 21+**

[Modrinth](https://modrinth.com/plugin/sign-nametags) · [Documentation](https://docs.lode.gg/sign/server-owners/overview) · [API](https://github.com/Lodestones/Sign-API)

</div>

---

## Features

- **Packet-driven rendering** — Nametags are client-side text display entities. No server entities are spawned.
- **MiniMessage formatting** — Full [MiniMessage](https://webui.advntr.dev/) support with gradients, hover, and more.
- **PlaceholderAPI** — Use any registered PAPI placeholder in nametag lines.
- **Voice chat integration** — `{voice}` placeholder for Simple Voice Chat and Amplifier status icons.
- **Crouching support** — Nametags reduce opacity and disable see-through when sneaking.
- **Condensed or per-line holograms** — Choose between a single text display or individual displays per line.
- **Customizable display** — Billboard, alignment, scale, background color, text shadow, and see-through.
- **Developer API** — Per-viewer nametag overrides, visibility control, and reload events via [Sign-API](https://github.com/Lodestones/Sign-API).

## Sign vs DisplayTags

Sign is built on top of [DisplayTags](https://github.com/imskeptical/DisplayTags) by [SkyyIsCool](https://github.com/imskeptical). Full credit to the original project for pioneering the text display entity approach to nametags.

Sign extends DisplayTags with the following improvements:

| | Sign | DisplayTags |
|---|---|---|
| **Nametag spawning** | Packet-intercepted — listens for `SPAWN_ENTITY`/`DESTROY_ENTITIES` packets to know exactly when a player entity exists on each viewer's client | Tick-based polling with fixed delays |
| **GSit compatibility** | Handles `SET_PASSENGERS` packets to keep nametags mounted through sit, crawl, lay, and ride states | Not supported — players float when using GSit |
| **Crouching** | Reduces opacity and disables see-through when the player is sneaking | Not supported |
| **Condensed mode** | Option to render all lines in a single text display entity, reducing packet overhead | One entity per line only |
| **Voice chat** | `{voice}` placeholder with Simple Voice Chat + Amplifier integration | Not available |
| **Per-viewer caching** | Three-layer dirty cache (resolved strings → per-viewer components → per-viewer condensed text) — only re-renders when content actually changes | Basic text caching |
| **Packet bundling** | Spawn, metadata, and mount packets are sent as atomic bundles to prevent partial rendering | Individual packets |
| **Entity state tracking** | Tracks client-side entity state per viewer to prevent nametag flicker on spawn | No client state tracking |
| **Developer API** | Separate [Sign-API](https://github.com/Lodestones/Sign-API) module with per-viewer nametag overrides, visibility control, and events | No public API |
| **Respawn handling** | Clean respawn without duplication | Known duplication issue on respawn |
| **Update interval** | Configurable in ticks (granular control) | Configurable in seconds |

## Credits

- [DisplayTags](https://github.com/imskeptical/DisplayTags) by [SkyyIsCool](https://github.com/imskeptical) — the original text display nametag plugin that Sign is built upon.
