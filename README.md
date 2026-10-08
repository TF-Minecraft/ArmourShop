# ArmourShop

> Armour and item appearances for TF-Minecraft characters.

ArmourShop lets players give their equipment a different look through an in-game skin catalogue. It brings armour sets, weapons, shields, tools, and other supported items into one browsing experience, with themed collections and access to personal or restricted designs.

## Features

- **Armour collections** — browse themed sets and apply appearances to compatible helmets, chestplates, leggings, and boots.
- **Weapon and item skins** — change the appearance of supported swords, bows, guns, shields, tools, and more.
- **Organised browsing** — category menus and paginated skin selections make large collections easier to explore.
- **Compatible equipment matching** — skins apply to an appropriate item in the player's inventory through the shared equipment appearance system.
- **Unlocks and skin scrolls** — collections can require access permissions, while individual designs can consume a matching skin scroll.
- **Custom skin submissions** — connects approved designs from TF-Minecraft's web workflow to the in-game catalogue, including rank-based submission entitlements.

ArmourShop gives players room to express a character's style across both armour and held items. Its catalogue includes broad equipment themes alongside individual custom collections, so personal designs and shared server cosmetics use the same familiar interface.

## Documentation

[Project documentation](https://github.com/TF-Minecraft/Docs/blob/main/projects/ArmourShop/README.md)

Technical documentation is maintained in [TF-Minecraft/Docs](https://github.com/TF-Minecraft/Docs).

## Tests and coverage

With Java 21 and the [build dependencies](https://github.com/TF-Minecraft/Docs/blob/main/projects/ArmourShop/README.md#build-and-dependencies)
prepared, run `mvn clean verify`. JUnit 5, Mockito and MockBukkit cover menus,
permissions, pack processing and web-service boundaries. JaCoCo enforces 100%
production **line coverage**, without production-class exclusions. Instruction
and branch coverage are reported separately.

The HTML report is `target/site/jacoco/index.html`; the machine-readable report is
`target/site/jacoco/jacoco.xml`. Surefire results are in `target/surefire-reports/`;
the Build workflow uploads both report directories. These tests do not replace
live Paper, ItemsAdder resource-pack or deployed website integration checks.

## License

Copyright (c) 2026 TF-Minecraft contributors.

TF-Minecraft-authored material in this repository is licensed under the
[Artistic License 2.0](LICENSE). Third-party dependencies and bundled material
retain their own licenses.
