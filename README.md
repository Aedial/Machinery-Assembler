# Machinery Assembler

A Minecraft 1.12 mod for modpack makers to define multiblocks for easy assembly (like a full Astral Sorcery Altar).

## Features
- Define multiblock structures in JSON or NBT file format.
- Hot reloading of multiblock definitions without restarting the game (via `/ma-reload`). This does not, however, add/remove definitions, you will still need to restart the game for that (this is a limitation of JEI and we cannot do anything about it).
- Preview multiblock structures both in-world and in a JEI GUI. In-world preview supports moving the preview and canceling with a keybind.
- Automatic binding of blocks in the structure to JEI recipes. This also means the list of the blocks used in the structure is integrated with JEI.
- Info/Warning/Error messages in the JEI tab, to help communicating important information about the structure to the user (for example, additional steps required for assembly, or warnings about potential issues).
- Wrench to automatically assemble a multiblock structure. The wrench will look in player's inventory and then AE2 network (if linked) for required blocks.
- Multiblock Recording Tool to capture an area, preview the exported result, and save a multiblock JSON definition (see FAQ).

## Keybinds
- Arrow keys: Move the in-world preview left/right/forward/backward by one block.
- Page Up/Down: Move the in-world preview up/down by one block.
- Escape: Cancel the in-world preview.
- Shift + Scroll Wheel: Change the in-world preview rotation.

- Shift + Right-click on a block with the wrench: Cancel any in-world preview and open the selection GUI with that block as the anchor.
- Shift + Right-click in the air with the wrench: Cancel any in-world preview, clear the anchor, and open the selection GUI.
- Right-click in the air with the wrench: Open the selection GUI with the previous anchor (if any), or autobuild if the preview is active and in autobuild mode.

## FAQ
### Where do I place the multiblock structure definition files?
Place them in the `config/machineryassembler/structuresonachi/structures/` folder in the Minecraft instance folder. You may create subfolders to organize them better. The mod will load all JSON files in that folder and its subfolders. See the [Structure JSON schema](src\main\resources\assets\machineryassembler\structures\structure_schema.json) or the [example structure definition](src\main\resources\assets\machineryassembler\structures\example_structure.json) for reference.

### How do I define a multiblock structure?
Use the Multiblock Recording Tool:
- Right-click once to set the first corner at your feet.
- Right-click again to set the second corner.
- Right-click a third time to scan the area and open the recorder GUI.
- Review the generated preview, disable any unwanted block types, and enable only the tile tags that should participate in matching.
- Click "Save" to write a structure JSON into the `multiblocks/` folder in the Minecraft instance folder. The new structure is reloaded for the wrench immediately, but JEI will still require a full restart before it appears there. Always review the generated JSON, and provide a proper file name and structure id before moving it to the `structures/` folder.

### How do I select a multiblock structure to assemble?
Use the Assembler's Wrench:
- Right-click with the wrench in the air to open the GUI with the last used anchor (if any).
- Right-click with the wrench in the air while sneaking to clear the anchor and open the GUI (with no anchor).
- Right-click with the wrench on a block while sneaking to open the GUI with that block as the anchor (reduces the list of matching multiblocks). The anchor will basically filter the list to only multiblocks that contain that block.
- Select a multiblock structure from the list. A ghost preview of the structure will be shown in-world.
- Right-click to assemble the structure around that block (only if it has been selected in the GUI). An in-world preview of the structure will be shown before assembly. If the structure contains multiple instances of the selected block, the behavior is undefined, and it is recommended to use arrow keys to move the preview to the desired position before assembling. It is generally recommended to use blocks that occur only once in the structure, for anchoring.


## Building
Run:
```
./gradlew build
```

## License
This project is licensed under the GPL License - see the LICENSE file for details.
