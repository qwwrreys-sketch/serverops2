# Server Ops (Fabric 1.20.1)

Compact staff dashboard for Minecraft **Fabric 1.20.1 / Java 17**, built from scratch on **Mojang (official) mappings**.

## Layout
```
common/   plain Java (no Minecraft): permissions, roles, hierarchy rules, saved data
fabric/   the mod: server logic, networking, GUI, mixins
.github/workflows/build.yml   GitHub Actions build (uploads the jar as an artifact)
```
Build locally: `./gradlew build` -> `fabric/build/libs/serverops-fabric-2.0.0.jar`
(`gradlew` downloads the small `gradle-wrapper.jar` itself on first run if it is missing.)

## Using it
Press **O** (rebindable under *Server Ops*) to open the panel. It is a centered window with 3 tabs:
- **Server** - weather, time of day, season (tints grass/leaves).
- **Players** - active players; click one for the profile: BRING / GO TO / RESPAWN / HEAL / KICK, role selector,
  *Panel access* switch, Godmode / Freeze / Invisible / Fly, ESP / health bars, ban (duration slider,
  public + private reason), unban, server ban list.
- **Roles** - hierarchy list (drag the grip or use the arrows; top = most senior), role editor (name, colour, permissions).

Players with a role show **[Role]** in the role colour directly above their nametag for everybody.

## Security model
- **Master key**: vanilla OP level 4 (`/op`) or the singleplayer host. Full access to everything.
- Everyone else needs either a role that has *Panel access*, or a manual *Panel access* switch set by a master.
  Players with no role and no switch get nothing (the keybind just says no).
- A non-master can never act on (freeze/ban/kick/heal/bring/change role of...) a player whose role is more senior, or on a master.
  They can only assign/edit roles strictly below their own, and only hand out permissions they hold themselves.
- Everything is checked again on the server; the client UI greys things out only as a convenience.

Data is stored per world in `<world>/serverops/data.json`.
