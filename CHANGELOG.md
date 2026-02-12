# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and this project adheres to Semantic Versioning.

- Keep a Changelog: https://keepachangelog.com/en/1.1.0/
- Semantic Versioning: https://semver.org/spec/v2.0.0.html


## [0.2.2] - 2026-02-12
### Fixed
- Fix message item tooltip rendering behind JEI item slots.
- Fix JEI exclusion area being applied to other JEI tabs.
- Fix output slot not rendering correctly in the JEI preview.
- Fix some conversion issues in the CM3 to MA script. AE2 cables and some rare cases of block variants are still not handled correctly, but the script should work for the majority of structutres now.


## [0.2.1] - 2026-02-10
### Added
- Add support for info/warning/error messages defined in structure JSON, displayed at the left of the preview area. Useful for giving players important information about the structure, such as special requirements or potential dangers.
- Add output item display in JEI preview, shown as an output slot at the top-right of the preview area. This allows players to see what item will be produced by the structure, if any.


## [0.2.0] - 2026-02-09
### Added
- Add script to convert Compact Machine format to our custom JSON format, for easier migration of existing structures.
- Add autobuild with the Assembler's Baton.
- Add configurable autobuild throttle (blocks per tick, fractional values supported).
- Add in-game config GUI.

### Changed
- Move from MMCE format to custom JSON format for better compression and readability.
- Remove MMCE dependency in favor of using its code directly, to greatly reduce the complexity and unecessary bloat.


## [0.1.0] - 2026-01-12
### Added
- Add JEI structure preview integration.
- Add MMCE format JSON structure support.
- Add `/ma-reload` command to reload structure definitions without restarting the game.
- Add structure's blocks as JEI ingredients.
- Add support for keybinds to move/cancel in-world preview.