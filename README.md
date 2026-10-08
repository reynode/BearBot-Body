# BearBot Body

Paper 1.21.8 physical-body foundation for BearBot. It uses Mojang-mapped NMS and does not depend on Citizens.

`BearBotBody` owns the lifecycle. `BearBotBodyEntity` is a custom `ServerPlayer` added through `ServerLevel.addNewPlayer`, which enters the entity manager, level player collection, entity tick list, and normal chunk tracking. A no-socket packet sink backs the required `ServerGamePacketListenerImpl`; it discards packets addressed to the bot itself. Actual viewers receive entity state through normal server tracking.

The body is deliberately not added to the global `PlayerList`. The player profile is sent to viewers separately for client skin/profile lookup. The entity is excluded from chunk entity persistence and is removed during plugin shutdown.

## Commands

- `/bot spawn`
- `/bot despawn`
- `/bot look <player>`
- `/bot look <x> <y> <z>`
- `/bot move <forward|backward|left|right>` and `/bot stop`
- `/bot jump`, `/bot sprint [on|off]`, `/bot sneak [on|off]`
- `/bot attack <player>`, `/bot interact <player>`, `/bot use [main|off]`
- `/bot debug`

`/bearbotbody` remains a command alias.

## Physical controls

`BodyController` exposes the server-side `ServerPlayer` inventory, item-use, block-use, entity interaction, attack, look, movement, health, environment, and passenger state. Movement continues through vanilla `LivingEntity.travel()` and `Entity.move()`; the entity records the latest travel request, collision result, and applied position delta for `/bot debug`.

The body is not registered in the global `PlayerList`. Plugins that require a normal online player session may not treat it as a human player. The NMS lifecycle, physics, interactions, damage, and plugin coexistence still require runtime verification on the target Paper 1.21.8 server. Automated navigation and Brain decisions remain out of scope.
