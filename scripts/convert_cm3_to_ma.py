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
import warnings
import sys

from pathlib import Path
from typing import Any
from argparse import ArgumentParser, RawDescriptionHelpFormatter


STRIPPED_NBT_KEYS = {"ForgeCaps", "OwnerUUIDLeast", "OwnerUUIDMost", "Items", "Lock"}
META_ITEM_BLOCK_ID = "minecraft:tripwire"
METAITEM_MESSAGE_KEY = "machineryassembler.message.cm3_metaitem_attachment"
VARIANT_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!#$%&()*+,-./;<=>?[]^{}|~"


class SnbtParser:
    def __init__(self, text: str):
        self.text = text
        self.cursor = 0

    def parse(self) -> Any:
        self.skip_whitespace()
        value = self.parse_value()
        self.skip_whitespace()

        if self.can_read():
            raise ValueError(f"Unexpected trailing data at position {self.cursor}")

        return value

    def parse_value(self) -> Any:
        self.skip_whitespace()

        if not self.can_read():
            raise ValueError("Expected value")

        current = self.peek()
        if current == "{":
            return self.parse_compound()

        if current == "[":
            return self.parse_list()

        if current == '"':
            return self.parse_quoted_string()

        return self.parse_bare_token()

    def parse_compound(self) -> dict[str, Any]:
        compound: dict[str, Any] = {}
        self.expect("{")
        self.skip_whitespace()

        while self.can_read() and self.peek() != "}":
            key = self.parse_key()
            self.skip_whitespace()
            self.expect(":")
            compound[key] = self.parse_value()

            self.skip_whitespace()
            if not self.consume_separator(","):
                break

        self.skip_whitespace()
        self.expect("}")

        return compound

    def parse_list(self) -> list[Any]:
        values: list[Any] = []
        self.expect("[")
        self.skip_whitespace()

        while self.can_read() and self.peek() != "]":
            values.append(self.parse_value())
            self.skip_whitespace()

            if not self.consume_separator(","):
                break

        self.skip_whitespace()
        self.expect("]")

        return values

    def parse_key(self) -> str:
        self.skip_whitespace()

        if not self.can_read():
            raise ValueError("Expected key")

        if self.peek() == '"':
            return self.parse_quoted_string()

        return self.parse_bare_token()

    def parse_quoted_string(self) -> str:
        self.expect('"')
        characters: list[str] = []
        escaped = False

        while self.can_read():
            current = self.pop()

            if escaped:
                characters.append(current)
                escaped = False
                continue

            if current == "\\":
                escaped = True
                continue

            if current == '"':
                return "".join(characters)

            characters.append(current)

        raise ValueError("Missing closing quote in SNBT string")

    def parse_bare_token(self) -> str:
        start = self.cursor

        while self.can_read() and self.peek() not in {",", ":", "{", "}", "[", "]"} and not self.peek().isspace():
            self.cursor += 1

        if start == self.cursor:
            raise ValueError(f"Expected token at position {self.cursor}")

        return self.text[start:self.cursor]

    def consume_separator(self, separator: str) -> bool:
        self.skip_whitespace()

        if not self.can_read() or self.peek() != separator:
            return False

        self.cursor += 1
        self.skip_whitespace()
        return True

    def skip_whitespace(self) -> None:
        while self.can_read() and self.peek().isspace():
            self.cursor += 1

    def expect(self, expected: str) -> None:
        self.skip_whitespace()

        if not self.can_read() or self.peek() != expected:
            raise ValueError(f"Expected '{expected}' at position {self.cursor}")

        self.cursor += 1

    def can_read(self) -> bool:
        return self.cursor < len(self.text)

    def peek(self) -> str:
        return self.text[self.cursor]

    def pop(self) -> str:
        character = self.text[self.cursor]
        self.cursor += 1
        return character


def strip_nbt_keys(value: Any) -> Any:
    if isinstance(value, dict):
        cleaned: dict[str, Any] = {}

        for key, nested_value in value.items():
            if key in STRIPPED_NBT_KEYS:
                continue

            cleaned[key] = strip_nbt_keys(nested_value)

        return cleaned

    if isinstance(value, list):
        return [strip_nbt_keys(entry) for entry in value]

    return value


def parse_nbt(nbt_value: str) -> dict[str, Any] | None:
    try:
        parsed = SnbtParser(nbt_value).parse()
    except ValueError as exc:
        warnings.warn(f"Could not parse NBT '{nbt_value}': {exc}")
        return None

    if not isinstance(parsed, dict):
        warnings.warn(f"Expected compound NBT, got {type(parsed).__name__}")
        return None

    cleaned = strip_nbt_keys(parsed)
    return cleaned or None


def format_item_descriptor(item_info: dict[str, Any]) -> str | None:
    item_id = item_info.get("id")
    if not item_id:
        return None

    descriptor = item_id
    meta = item_info.get("meta", 0)
    count = item_info.get("count", 1)

    if meta:
        descriptor += f"@{meta}"

    if count != 1:
        descriptor += f"*{count}"

    if "nbt" in item_info:
        warnings.warn(f"Ignoring unsupported message item NBT for '{descriptor}'")

    return descriptor


def build_input_entry(block_info: dict[str, Any], nbt: dict[str, Any] | None = None) -> str | dict[str, Any]:
    block_id = block_info.get("id", "")
    meta = block_info.get("meta", 0)

    if nbt is None:
        if meta:
            return f"{block_id}@{meta}"

        return block_id

    entry: dict[str, Any] = {"id": block_id}
    if meta:
        entry["meta"] = meta

    entry["nbt"] = nbt
    return entry


def split_variant_key(token: str) -> tuple[str, str | None]:
    if ":" not in token:
        return token, None

    base, variant = token.split(":", 1)
    return base, variant or None


def allocate_variant_char(preferred: str | None, used_chars: set[str]) -> str:
    if preferred and len(preferred) == 1 and preferred not in {"_", " "} and preferred not in used_chars:
        used_chars.add(preferred)
        return preferred

    for candidate in VARIANT_CHARACTERS:
        if candidate in {"_", " "} or candidate in used_chars:
            continue

        used_chars.add(candidate)
        return candidate

    raise ValueError("Ran out of single-character aliases for CM3 input variants")


def build_conversion_context(
    input_types: dict[str, dict[str, Any]], input_nbt: dict[str, dict[str, str]]
) -> tuple[dict[str, str | None], dict[str, str | dict[str, Any]], set[str]]:
    token_map: dict[str, str | None] = {}
    inputs: dict[str, str | dict[str, Any]] = {}
    used_chars: set[str] = set()
    metaitem_chars: set[str] = set()

    for char, block_info in input_types.items():
        if len(char) != 1:
            warnings.warn(f"Input character '{char}' is not a single character. Skipping.")
            continue

        if block_info.get("id") == META_ITEM_BLOCK_ID:
            token_map[char] = None
            metaitem_chars.add(char)
            continue

        token_map[char] = char
        inputs[char] = build_input_entry(block_info)
        used_chars.add(char)

    for token, nbt_info in input_nbt.items():
        base_char, variant = split_variant_key(token)
        block_info = input_types.get(base_char)

        if block_info is None:
            warnings.warn(f"NBT variant '{token}' does not match any input type. Skipping.")
            continue

        if block_info.get("id") == META_ITEM_BLOCK_ID:
            token_map[token] = None
            metaitem_chars.add(base_char)
            continue

        nbt_value = nbt_info.get("nbt")
        parsed_nbt = parse_nbt(nbt_value) if nbt_value else None
        if parsed_nbt is None:
            token_map[token] = base_char
            continue

        if variant is None:
            inputs[base_char] = build_input_entry(block_info, parsed_nbt)
            token_map[token] = base_char
            continue

        alias_char = allocate_variant_char(variant, used_chars)
        inputs[alias_char] = build_input_entry(block_info, parsed_nbt)
        token_map[token] = alias_char

    return token_map, inputs, metaitem_chars


def build_meta_item_messages(
    input_types: dict[str, dict[str, Any]], used_metaitems: list[str]
) -> list[dict[str, str]]:
    seen_items: set[str] = set()
    messages: list[dict[str, str]] = []

    for metaitem_char in used_metaitems:
        block_info = input_types.get(metaitem_char, {})
        item_info = block_info.get("item")
        if not isinstance(item_info, dict):
            warnings.warn(f"Meta item placeholder '{metaitem_char}' is missing an attached item")
            continue

        descriptor = format_item_descriptor(item_info)
        if descriptor is None or descriptor in seen_items:
            continue

        seen_items.add(descriptor)
        messages.append(
            {
                "key": METAITEM_MESSAGE_KEY,
                "level": "info",
                "item": descriptor,
            }
        )

    return messages


def collect_used_shape_chars(shape: list[list[str]]) -> set[str]:
    used_chars: set[str] = set()

    for y_layer in shape:
        for row in y_layer:
            for char in row:
                if char in {"_", " "}:
                    continue

                used_chars.add(char)

    return used_chars


def convert_shape(shape: list[list[list[str]]], token_map: dict[str, str | None]) -> tuple[list[list[str]], list[str]]:
    """
    Convert shape from CM3 format to MA format.
    CM3: shape[y][z][x] = single character string (or "a:A" for NBT variants)
    MA:  shape[y][z] = string of characters for that row

    Variant suffixes are remapped to dedicated MA input characters when they carry NBT.
    Meta item placeholders are removed from the structure and surfaced as JEI messages.
    """
    new_shape = []
    used_metaitems: list[str] = []

    for y_layer in shape:
        new_layer = []

        for z_row in y_layer:
            row_chars = []

            for cell in z_row:
                if not cell:
                    row_chars.append("_")
                    continue

                if cell in {"_", " "}:
                    row_chars.append("_")
                    continue

                mapped_char = token_map.get(cell)
                if mapped_char is None and cell not in token_map and ":" in cell:
                    base = cell.split(":", 1)[0]
                    mapped_char = token_map.get(base)

                if mapped_char is None and (cell in token_map or cell.split(":", 1)[0] in token_map):
                    base = cell.split(":", 1)[0]
                    if base not in used_metaitems:
                        used_metaitems.append(base)

                    row_chars.append("_")
                    continue

                if mapped_char is not None:
                    row_chars.append(mapped_char)
                    continue

                warnings.warn(f"Cell '{cell}' has unexpected format. Using placeholder.")
                row_chars.append(cell[0] if cell else "_")

            row_str = "".join(row_chars)
            new_layer.append(row_str)

        new_shape.append(new_layer)

    return new_shape, used_metaitems


def extract_id_from_name(name: str) -> str:
    """
    Remove namespace prefix from name if present.
    E.g., "compactmachines:small" -> "small"
    """

    if ":" in name:
        return name.split(":")[-1]

    return name


def convert_cm3_to_ma(cm3_data: dict[str, Any]) -> dict[str, Any]:
    """
    Convert a CompactMachines3 structure definition to Machinery Assembler format.
    """
    ma_data: dict[str, Any] = {}

    # Extract id from name
    name = cm3_data.get("name", "unknown")
    ma_data["id"] = extract_id_from_name(name)

    # Optional: disabled structures could set register-as-item to false
    if cm3_data.get("disabled", False):
        ma_data["register-as-item"] = False

    input_types = cm3_data.get("input-types", {})
    input_nbt = cm3_data.get("input-nbt", {})
    token_map, inputs, metaitem_chars = build_conversion_context(input_types, input_nbt)

    if inputs:
        ma_data["inputs"] = inputs

    if "shape" in cm3_data:
        converted_shape, used_metaitems = convert_shape(cm3_data["shape"], token_map)
        ma_data["shape"] = converted_shape

        used_input_chars = collect_used_shape_chars(converted_shape)
        if "inputs" in ma_data:
            ma_data["inputs"] = {
                char: value for char, value in ma_data["inputs"].items() if char in used_input_chars
            }

        relevant_metaitems = [metaitem for metaitem in used_metaitems if metaitem in metaitem_chars]
        messages = build_meta_item_messages(input_types, relevant_metaitems)
        if messages:
            ma_data["messages"] = messages

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
