#!/usr/bin/env python3
"""Port taleks-better-landscape datapack JSON from MC 26.1 to 26.3 (data pack format 121).

Rules are taken from the 26.3 release changelog + vanilla 26.3 data + Lithostitched 2.0.6 source.
"""
import json, os, sys, shutil, copy

SRC = "src/main/resources/data/taleks-better-landscape"   # .../src/main/resources/data/taleks-better-landscape
DST = "src/main/resources/data/taleks-better-landscape"

BIN = {"minecraft:add", "minecraft:mul", "minecraft:min", "minecraft:max",
       "minecraft:sub", "minecraft:div"}
UNARY = {"minecraft:abs", "minecraft:square", "minecraft:cube", "minecraft:half_negative",
         "minecraft:quarter_negative", "minecraft:squeeze", "minecraft:negate",
         "minecraft:floor", "minecraft:ceil", "minecraft:round", "minecraft:sqrt",
         "minecraft:sign", "minecraft:log", "minecraft:reciprocal", "minecraft:truncate",
         "lithostitched:sin", "lithostitched:cos"}

BIG = 30000000


def state(o):
    """{"Name":..,"Properties":{..}} -> "id" | {"id":..,"properties":{..}}"""
    if isinstance(o, dict) and "Name" in o and set(o) <= {"Name", "Properties"}:
        if o.get("Properties"):
            return {"id": o["Name"], "properties": {k: str(v) for k, v in o["Properties"].items()}}
        return o["Name"]
    return o


def deep_state(o):
    if isinstance(o, dict):
        s = state(o)
        if s is not o:
            return s
        return {k: deep_state(v) for k, v in o.items()}
    if isinstance(o, list):
        return [deep_state(v) for v in o]
    return o


def is_zero(o):
    return isinstance(o, (int, float)) and not isinstance(o, bool) and o == 0


def df(o):
    """Convert a density function (recursively)."""
    if isinstance(o, list):
        return [df(v) for v in o]
    if not isinstance(o, dict):
        return o
    t = o.get("type")
    o = {k: (df(v) if k != "type" else v) for k, v in o.items()}
    if t in BIN:
        out = {"type": t, "left": o["argument1"], "right": o["argument2"]}
        return out
    if t in UNARY and "argument" in o:
        return {"type": t, "input": o["argument"]}
    if t == "minecraft:flat_cache":
        return {"type": "minecraft:cache",
                "input": {"type": "minecraft:slice", "axis": "y", "coordinate": 0,
                          "input": o["argument"]}}
    if t == "minecraft:shifted_noise":
        out = {"type": "minecraft:noise", "noise": o["noise"]}
        for k in ("shift_x", "shift_y", "shift_z"):
            if k in o and not is_zero(o[k]):
                out[k] = o[k]
        out["xz_scale"] = o["xz_scale"]
        out["y_scale"] = o["y_scale"]
        return out
    if t == "lithostitched:mix":
        return {"type": "minecraft:lerp",
                "alpha": {"type": "minecraft:clamp", "input": o["input"], "min": 0, "max": 1},
                "first": o["argument1"], "second": o["argument2"]}
    if t == "lithostitched:floor":
        return {"type": "minecraft:floor", "input": o["argument"]}
    if t == "lithostitched:axis":
        return {"type": "minecraft:gradient", "axis": o["axis"],
                "from_coordinate": -BIG, "from_value": float(-BIG),
                "to_coordinate": BIG, "to_value": float(BIG)}
    if t == "lithostitched:select":
        res = o["fallback"]
        for sel in reversed(o["selections"]):
            r = sel["range"]
            lo, hi = (r, r) if isinstance(r, (int, float)) else (r[0], r[1])
            res = {"type": "minecraft:range_choice", "input": o["input"],
                   "min_inclusive": lo, "max_exclusive": hi,
                   "when_in_range": sel["function"], "when_out_of_range": res}
        return res
    return o


def noise(o):
    amps = o["amplitudes"]
    return {"base_octave": o["firstOctave"], "octave_count": len(amps),
            "amplitude_modifiers": amps, "normalize": "legacy"}


def provider(p):
    """block state provider (old format) -> 26.3"""
    if isinstance(p, dict):
        t = p.get("type")
        if t == "minecraft:simple_state_provider":
            return state(p["state"])
        if t == "minecraft:weighted_state_provider":
            return {"type": "minecraft:weighted",
                    "entries": [{"data": state(e["data"]), "weight": e["weight"]} for e in p["entries"]]}
    return p


def processors(o):
    if isinstance(o, dict):
        o = {k: processors(v) for k, v in o.items()}
        if "state_provider" in o:
            o["state_provider"] = provider(o["state_provider"])
        return o
    if isinstance(o, list):
        return [processors(v) for v in o]
    return o


def feature(o):
    """configured feature (old: type+config) -> 26.3 (inline)"""
    if isinstance(o, str):
        return o
    t = o["type"]
    cfg = o.get("config", {})
    if t == "lithostitched:weighted_selector":
        return {"type": "minecraft:weighted_random_selector",
                "features": [{"weight": f["weight"],
                              "data": {"feature": feature(f["data"]["feature"]),
                                       "placement": f["data"].get("placement", [])}}
                             for f in cfg["features"]]}
    out = {"type": t}
    for k, v in cfg.items():
        out[k] = v
    for k in ("state",):
        if k in out:
            out[k] = state(out[k])
    return out


def placement_mod(m):
    if m.get("type") == "lithostitched:offset":
        return {"type": "minecraft:offset", "x": 0, "y": m["y_offset"], "z": 0}
    return m


def modifier(o):
    t = o["type"]
    if t == "lithostitched:wrap_density_function":
        o = dict(o)
        o["wrapper_function"] = df(o["wrapper_function"])
        return o
    if t == "lithostitched:add_surface_rule":
        out = {"type": "lithostitched:set_material_rule"}
        if "priority" in o:
            out["priority"] = o["priority"]
        out["target_rules"] = "minecraft:overworld"
        out["material_rule"] = deep_state(o["surface_rule"])
        return out
    return o


def dump(path, obj, indent=2):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=indent, ensure_ascii=False)
        f.write("\n")


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


stats = {}
def count(k):
    stats[k] = stats.get(k, 0) + 1


for root, _, files in os.walk(SRC):
    for fn in files:
        sp = os.path.join(root, fn)
        rel = os.path.relpath(sp, SRC).replace(os.sep, "/")
        if not fn.endswith(".json"):
            dp = os.path.join(DST, rel)
            os.makedirs(os.path.dirname(dp), exist_ok=True)
            shutil.copyfile(sp, dp)
            count("copied")
            continue
        o = load(sp)
        if rel.startswith("worldgen/density_function/"):
            dump(os.path.join(DST, rel), df(o)); count("density_function")
        elif rel.startswith("worldgen/noise/"):
            dump(os.path.join(DST, rel), noise(o)); count("noise")
        elif rel.startswith("worldgen/configured_feature/"):
            dump(os.path.join(DST, "worldgen/feature", fn), feature(o)); count("feature (moved)")
        elif rel.startswith("worldgen/placed_feature/"):
            o["placement"] = [placement_mod(m) for m in o["placement"]]
            dump(os.path.join(DST, rel), o); count("placed_feature")
        elif rel.startswith("worldgen/processor_list/"):
            dump(os.path.join(DST, rel), processors(o)); count("processor_list")
        elif rel.startswith("lithostitched/worldgen_modifier/"):
            dump(os.path.join(DST, rel), modifier(o)); count("worldgen_modifier")
        else:
            dump(os.path.join(DST, rel), o); count("other json (unchanged): " + rel.split("/")[1])

print(json.dumps(stats, indent=1))