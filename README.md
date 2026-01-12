# Machinery Assembler

A Minecraft 1.12 mod for modpack makers to define multiblocks for easy assembly (like a full Astral Sorcery Altar).

## Features
- Define multiblock structures in JSON or NBT file format.
- Hot reloading of multiblock definitions without restarting the game (via `/ma-reload`). This does not, however, add/remove definitions, you will still need to restart the game for that.
- Preview multiblock structures both in-world and in a JEI GUI. In-world preview supports moving the preview with keybinds (arrow keys and page up/down by default) and canceling with a keybind (escape by default).
- Automatic binding of blocks in the structure to JEI recipes. This also means the list of the blocks used in the structure is integrated with JEI.
- Stick to automatically assemble a multiblock structure. The stick will look in player's inventory and then AE2 network (if available) for required blocks.
- Wand to encode blocks into a multiblock structure definition (see FAQ).


## FAQ
### Where do I place the multiblock structure definition files?
Place them in the `config/machineryassembler/structures/` folder in the Minecraft instance folder. You may create subfolders to organize them better. The mod will load all JSON and NBT files in that folder and its subfolders. Due to `:` being an invalid character for file names on Windows, use `__` (double underscore) instead of `:` in file names to define namespaced ids (for example, `origin_mod__my_multiblock.nbt` will define a multiblock with id `origin_mod:my_multiblock`). This is not an issue for JSON files, as the id is defined inside the file.

### How do I define a multiblock structure?
Use the Wand:
- Right-click with the wand in the air while sneaking to open the GUI.
- Give the structure an id (for example, "my_multiblock" or "origin_mod:multiblock").
- Click "Select area" and right-click on 2 corners of the desired structure in the world. Right-clicking on the air is possible if it is further away than 3 blocks. The selected block positions will be highlighted until the corners are chosen, then the area will be outlined.
- A preview of the structure and a list of blocks in the area will be shown. You may click on blocks in the list to remove them (they will not be saved in the structure definition).
- Click "Save" to save the structure definition to a file in the `config/machinery_assembler/structures/` folder in the Minecraft instance folder. You may choose between JSON and NBT format. The structure is also loaded immediately for use.

### How do I select a multiblock structure to assemble?
Use the Stick:
- Right-click with the stick in the air while sneaking to open the GUI with no anchor.
- Right-click with the stick on a block to open the GUI with that block as the anchor (reduces the list of matching multiblocks). The anchor will basically filter the list to only multiblocks that contain that block.
- Select a multiblock structure from the list. A preview of the structure will be shown in-world.
- Right-click on a block to assemble the structure around that block (only if it has been selected in the GUI). An in-world preview of the structure will be shown before assembly. If the structure contains multiple instances of the selected block, the behavior is undefined, and it is recommended to use arrow keys to move the preview to the desired position before assembling. It is generally recommended to use blocks that occur only once in the structure, for anchoring.


## Building
Run:
```
./gradlew setupCiWorkspace build
```
The first run can take dozens of minutes. Resulting jar will be under `build/libs/`.

## License
This project is licensed under the MIT License - see the LICENSE file for details.
