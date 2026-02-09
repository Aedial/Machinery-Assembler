# Machinery Assembler

A Minecraft 1.12 mod for modpack makers to define multiblocks for easy assembly (like a full Astral Sorcery Altar).

## Features
- Define multiblock structures in JSON or NBT file format.
- Hot reloading of multiblock definitions without restarting the game (via `/ma-reload`). This does not, however, add/remove definitions, you will still need to restart the game for that (this is a limitation of JEI and we cannot do anything about it).
- Preview multiblock structures both in-world and in a JEI GUI. In-world preview supports moving the preview and canceling with a keybind.
- Automatic binding of blocks in the structure to JEI recipes. This also means the list of the blocks used in the structure is integrated with JEI.
- Info/Warning/Error messages in the JEI tab, to help communicating important information about the structure to the user (for example, additional steps required for assembly, or warnings about potential issues).
- Baton to automatically assemble a multiblock structure. The baton will look in player's inventory and then AE2 network (if linked) for required blocks.
- Wand to encode blocks into a multiblock structure definition (see FAQ).

## Keybinds
- Arrow keys: Move the in-world preview left/right/forward/backward by one block.
- Page Up/Down: Move the in-world preview up/down by one block.
- Escape: Cancel the in-world preview.
- Shift + Scroll Wheel: Change the in-world preview rotation.

- Shift + Right-click on a block with the baton: Cancel any in-world preview and open the selection GUI with that block as the anchor.
- Shift + Right-click in the air with the baton: Cancel any in-world preview, clear the anchor, and open the selection GUI.
- Right-click in the air with the baton: Open the selection GUI with the previous anchor (if any), or autobuild if the preview is active and in autobuild mode.

## FAQ
### Where do I place the multiblock structure definition files?
Place them in the `config/machineryassembler/structures/` folder in the Minecraft instance folder. You may create subfolders to organize them better. The mod will load all JSON files in that folder and its subfolders. See the [Structure JSON schema](src\main\resources\assets\machineryassembler\structures\structure_schema.json) or the [example structure definition](src\main\resources\assets\machineryassembler\structures\example_structure.json) for reference.

### How do I define a multiblock structure?
Use the Wand:
- Right-click with the wand in the air while sneaking to open the GUI.
- Give the structure an id (for example, "my_multiblock" or "origin_mod:multiblock").
- Click "Select area" and right-click on 2 corners of the desired structure in the world. Right-clicking on the air is possible if it is further away than 3 blocks. The selected block positions will be highlighted until the corners are chosen, then the area will be outlined.
- A preview of the structure and a list of blocks in the area will be shown. You may click on blocks in the list to remove them (they will not be saved in the structure definition).
- Click "Save" to save the structure definition to a file in the `config/machinery_assembler/structures/` folder in the Minecraft instance folder. The structure will be immediately available for assembling with the baton, but will require a game restart to show up in JEI and be usable as a recipe component (same as hot-reloading).

### How do I select a multiblock structure to assemble?
Use the Assembler's Baton:
- Right-click with the baton in the air to open the GUI with the last used anchor (if any).
- Right-click with the baton in the air while sneaking to clear the anchor and open the GUI (with no anchor).
- Right-click with the baton on a block while sneaking to open the GUI with that block as the anchor (reduces the list of matching multiblocks). The anchor will basically filter the list to only multiblocks that contain that block.
- Select a multiblock structure from the list. A ghost preview of the structure will be shown in-world.
- Right-click to assemble the structure around that block (only if it has been selected in the GUI). An in-world preview of the structure will be shown before assembly. If the structure contains multiple instances of the selected block, the behavior is undefined, and it is recommended to use arrow keys to move the preview to the desired position before assembling. It is generally recommended to use blocks that occur only once in the structure, for anchoring.


## Building
Run:
```
./gradlew build
```

## License
This project is licensed under the GPL License - see the LICENSE file for details.
