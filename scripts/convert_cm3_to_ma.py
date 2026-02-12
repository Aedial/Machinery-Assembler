#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors
"""
Converter script from CompactMachines3 structure format to Machinery Assembler format.

Old CM3 format:
- name: String identifier
- input-types: Mapping of characters to {id, meta} objects
- shape: Array[y][z][x] where each position is a single character string

New MA format:
- id: String for lang key, JEI, item registration
- inputs: Mapping of characters to "id@meta" strings (or {id, meta?, nbt?} objects)
- shape: Array[y][z] where each z is a string representing the x row
"""

import json
import sys
import os
import warnings

from pathlib import Path
from typing import Any
from argparse import ArgumentParser, RawDescriptionHelpFormatter


def convert_input_types(input_types: dict[str, dict]) -> dict[str, str]:
    """
    Convert input-types from CM3 format to MA format.
    CM3: {"a": {"id": "mod:block", "meta": 0}}
    MA:  {"a": "mod:block@0"}

    Note: CM3 input-nbt variants (e.g., "a:A") are ignored as they just map to the base
    character.
    """
    inputs = {}

    for char, block_info in input_types.items():
        if len(char) != 1:
            warnings.warn(f"Input character '{char}' is not a single character. Skipping.")
            continue

        block_id = block_info.get("id", "")
        meta = block_info.get("meta", 0)

        if meta != 0:
            inputs[char] = f"{block_id}@{meta}"
        else:
            inputs[char] = block_id


    # FIXME: process NBT if present
    return inputs


def convert_shape(shape: list[list[list[str]]]) -> list[list[str]]:
    """
    Convert shape from CM3 format to MA format.
    CM3: shape[y][z][x] = single character string (or "a:A" for NBT variants)
    MA:  shape[y][z] = string of characters for that row

    Variant suffixes like "a:A" are stripped to just the base character "a".
    """
    new_shape = []
    for y_layer in shape:
        new_layer = []

        for z_row in y_layer:
            row_chars = []
            for cell in z_row:
                if len(cell) == 1:
                    # Simple single character
                    row_chars.append(cell)
                elif ":" in cell:
                    # Variant reference like "a:A" - strip to base character
                    base = cell.split(":", 1)[0]
                    if len(base) == 1:
                        row_chars.append(base)
                    else:
                        warnings.warn(f"Cannot parse cell '{cell}'. Using placeholder.")
                        row_chars.append("?")
                else:
                    # Unknown format - use first character if possible
                    warnings.warn(f"Cell '{cell}' has unexpected format. Using first char.")
                    row_chars.append(cell[0] if cell else "_")

            row_str = "".join(row_chars)
            new_layer.append(row_str)

        new_shape.append(new_layer)

    return new_shape


def extract_id_from_name(name: str) -> str:
    """
    Remove namespace prefix from name if present.
    E.g., "compactmachines:small" -> "small"
    """

    if ":" in name:
        return name.split(":")[-1]

    return name


def convert_cm3_to_ma(cm3_data: dict) -> dict:
    """
    Convert a CompactMachines3 structure definition to Machinery Assembler format.
    """
    ma_data = {}

    # Extract id from name
    name = cm3_data.get("name", "unknown")
    ma_data["id"] = extract_id_from_name(name)

    # Optional: disabled structures could set register-as-item to false
    if cm3_data.get("disabled", False):
        ma_data["register-as-item"] = False

    # Convert input-types to inputs
    if "input-types" in cm3_data:
        ma_data["inputs"] = convert_input_types(cm3_data["input-types"])

    # Convert shape (variant suffixes like "a:A" are stripped to "a")
    if "shape" in cm3_data:
        ma_data["shape"] = convert_shape(cm3_data["shape"])

    return ma_data


def convert_file(input_path: Path, output_path: Path | None = None, dry: bool = False) -> None:
    """
    Convert a single CM3 JSON file to MA format.
    """

    if output_path is None:
        output_path = input_path.with_stem(input_path.stem + "_converted")

    cm3_data = json.loads(input_path.read_text(encoding="utf-8"))
    ma_data = convert_cm3_to_ma(cm3_data)

    data = json.dumps(ma_data, indent=2, ensure_ascii=False)
    if not dry:
        output_path.write_text(data, encoding="utf-8")

    print(f"Converted{' (dry)' if dry else ''}: {input_path} -> {output_path}\n")


def convert_directory(input_dir: Path, output_dir: Path | None = None, dry: bool = False) -> None:
    """
    Convert all CM3 JSON files in a directory.
    """

    if output_dir is None:
        output_dir = input_dir / "converted"
    else:
        output_dir = Path(output_dir)

    if not dry:
        output_dir.mkdir(parents=True, exist_ok=True)

    for json_file in input_dir.glob("*.json"):
        try:
            output_path = output_dir / json_file.name
            convert_file(json_file, output_path, dry=dry)
        except Exception as e:
            print(f"Error converting {json_file}: {e}\n")


description = "Convert CompactMachines3 structure JSON files to Machinery Assembler format."
epilog = """
Examples:
  convert_cm3_to_ma.py recipe.json                    # Convert single file
  convert_cm3_to_ma.py recipe.json output.json        # Convert with custom output
  convert_cm3_to_ma.py ./recipes/                     # Convert all in directory
  convert_cm3_to_ma.py ./recipes/ ./converted/        # Convert to custom directory
"""

parser = ArgumentParser(epilog=epilog, description=description, formatter_class=RawDescriptionHelpFormatter)
parser.add_argument("input", type=Path, help="Input file or directory")
parser.add_argument("output", nargs="?", type=Path, help="Output file or directory")
parser.add_argument("--dry", action="store_true", help="Convert without writing output files")


def main():
    args = parser.parse_args()

    if args.input.is_dir():
        convert_directory(args.input, args.output, dry=args.dry)
    elif args.input.is_file():
        convert_file(args.input, args.output, dry=args.dry)
    else:
        print(f"Error: {args.input} does not exist")
        sys.exit(1)


if __name__ == "__main__":
    main()
