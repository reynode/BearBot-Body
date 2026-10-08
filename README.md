# BearBot Body

Paper 1.21.8 physical-body foundation for BearBot. It uses Mojang-mapped NMS and does not depend on Citizens.

`BearBotBody` owns the lifecycle. `BearBotBodyEntity` is a custom `ServerPlayer` added through `ServerLevel.addNewPlayer`, which enters the entity manager, level player collection, entity tick list, and normal chunk tracking. A no-socket packet sink backs the required `ServerGamePacketListenerImpl`; it discards packets addressed to the bot itself. Actual viewers receive entity state through normal server tracking.

The body is deliberately not added to the global `PlayerList`. The player profile is sent to viewers separately for client skin/profile lookup. The entity is excluded from chunk entity persistence and is removed during plugin shutdown.

## Commands

- `/bot spawn`
- `/bot despawn`
- `/bot look <player>`

`/bearbotbody` remains a command alias.

## Current limits

The NMS lifecycle and physical tick path have not been runtime-verified on a Paper server in this workspace. Automated navigation, movement commands, Skyblock void safeguards, death/respawn lifecycle, and server-player integration with plugins that depend on global `PlayerList` membership are not implemented.
