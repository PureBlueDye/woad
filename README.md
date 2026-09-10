# Woad

A client-side Fabric mod for Hypixel SkyBlock, built for **Minecraft 26.1.2**.

## Features

| Feature | What it does |
| --- | --- |
| **Custom Items** | Rename, recolour and restyle SkyBlock items on your client only — keyed on the item's own uuid, so only *your* Hyperion changes. Swap the vanilla model, force or remove the enchant glint, dye leather armour (fixed colour or RGB). Open with `/woad customitem` while holding the item. |
| **Tick Time** | Server tick health, measured from the ping. |
| **Jerry Timer** | Countdown to the next Jerry, drawn on the HUD. |
| **Custom Lava** | Recolours lava to any palette you like. |
| **Inventory Buttons** | Shortcut buttons around the player inventory. |
| **Loadout Keybinds** | Press a key to pick a loadout in the Loadouts menu. |
| **Explosive Shot** | Quality-of-life helper for explosive bows. |
| **AI Chat** | A local Ollama model answers in party chat, with conversation memory. |
| **Translator** | Hold the bound key and click a chat line to translate it, client-side only. |
| **Blackjack** | A blackjack table played through party chat (`/bj`). |
| **Network picker** | With several internet connections plugged in, choose which adapter Minecraft leaves through — button at the top right of the server list. |

## Commands

| Command | |
| --- | --- |
| `/woad` | Open the config menu (also bound to Right Shift by default) |
| `/woad customitem` | Customise the item in your hand |
| `/woad hud` | Move the HUD elements |
| `/bj` | Open the blackjack table |

## Building

Requires JDK 25.

```
./gradlew build
```

The jar lands in `build/libs/`.

## License

MIT — see [LICENSE](LICENSE).
