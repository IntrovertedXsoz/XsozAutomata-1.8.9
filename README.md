# Xsoz Automata 1.8.9 PvP Edition

A PvP client for Minecraft 1.8.9 (Forge) that fights like a person. Strictly PvP: the aim, the clicking and the craft of 1.8 fighting.

Right Shift opens the menu.

## What it does

- **Human aim.** A model of a hand on a mouse: a reaction time, eyes about a tenth of a second behind, flicks timed by Fitts' law that land a little short or long and get corrected, small correction bursts while tracking, tremor and drift. The view turns through the game's own mouse code in whole mouse steps at your sensitivity. It aims at a spot round the neck and upper chest that wanders, never one exact pixel.
- **Human clicking.** Normal (about 7-10 a second), jitter (about 10-13) or butterfly (about 12-16, in close pairs). The gaps are never even, the rate drifts over a few seconds, a beat gets missed now and then, and it never goes over 18.5 a second. It only clicks when they're about in reach and the crosshair is near them.
- **1.8 fighting.**
  - **Sprint resets:** after every hit that lands, a w-tap, s-tap or block-hit, so the next hit knocks them back again.
  - **Movement:** it strafes, switching direction at no set rhythm, and jump-resets when hit.
  - **Closing in:** it sprint-jumps across a gap to reach them.
- **Kits:** works out the kit from what's in the hotbar, or you pick one.
  - **Classic:** rod and bow.
  - **Sumo:** never steps toward an edge.
  - **Boxing.**
  - **NoDebuff:** throws splash healing potions at its own feet when low.
  - **Soup.**
  - **UHC:** golden apples when there's room to eat.
  - **Combo.**
  - **Rod:** used to break their combo and start its own.
  - **Bow:** at range, leading a moving target and aiming over the arrow's drop.
- **Who it fights.**
  - **Only players.** Never friends, and never teammates (same team or same name colour).
  - **Never fake players:** a server's NPCs and anti-cheat bots aren't on the tab list, so it ignores them.
  - **Only what it can see:** someone it saw a moment ago counts too.

## Pages

- **PvP:** the switch (or bind "Fight on/off" in Controls); Skill (Casual, Decent, Good, Great, Pro); Clicking style; Kit; Sprint reset; Leave teammates; Thoughts (what it's doing, top left).
- **Friends:** add by name or the player you're looking at. `/automata friend add|remove <name>`, `/automata friend list`.
- **Messages:** in chat, above the hotbar, or off. It never talks in public chat.

## Building

Needs a JDK 17-21 to run Gradle; Gradle fetches a Java 8 JDK (Temurin) for Minecraft by itself.

```
./gradlew build
```

The mod is in `build/libs`. To play from the source: `./gradlew runGame`. To run the in-game self-test (opens the menu, makes a flat world, fights a practice dummy, writes `run/xsoz-selftest.txt` and quits): `XSOZ_SELFTEST=1 ./gradlew runGame`.

## Licence

MIT.
