# LoM Magic — Soul Recall (development branch)

Forge 1.20.1 / Java 17 / Iron's Spells 'n Spellbooks 3.16.3.

Custom **Epic Ender** spell `lommagic:soul_recall`.

- Captures the player's last death coordinates and dimension server-side.
- Preserves these data over respawn with PlayerEvent.Clone.
- Searches safe destinations 30–50 blocks away, then 51–96 blocks.
- If no safe point exists, attempts a 3×3 obsidian platform **only above lava**.
- Platform lasts 1,200 ticks (60 seconds), unless server restarts.
- Cross-dimensional teleport is supported.
- Mana: 250; cooldown: 900 seconds; cast time: 100 ticks.

## Current limitations / review checklist

**UNTESTED SOURCE — NOT A RELEASE.** The addon is not yet compiled against the exact 3.16.3 distribution, and is **not wired into the modpack distribution.json**.

1. Verify Gradle dependency coordinate and Iron's API signature with `gradle build` (Java 17). Add a Gradle wrapper before CI release.
2. Confirm spell registration, item/scroll loot, spell icon, and localization keys against Iron's 3.16.3.
3. Verify Netheritic lava ocean behavior and cross-dimensional arrival on a dedicated server.
4. The platform cleanup currently uses an in-memory timer: a server restart can leave blocks in the world. Replace with world SavedData before production.
5. Obsidian cleanup cannot distinguish a player's identical replacement obsidian; persistent per-platform ownership and protection/claims checks are needed before shipping.
6. The safe-search attempts potentially significant chunk generation; add a loaded-chunk-only or controlled preloading strategy and profiling before production.
7. Need no-consume handling if a spell fails and a PvP/combat restriction if desired.

Project is deliberately isolated under `addons/lom-magic`; no user-facing modpack changes until build and tests pass.
