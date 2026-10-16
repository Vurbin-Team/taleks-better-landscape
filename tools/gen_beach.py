#!/usr/bin/env python3
"""Generates the beach worldgen modifiers. Edit the constants, run, rebuild.
Units: 'b' = ramp height in blocks above sea level at a given continentalness."""
import json, pathlib

NS = "taleks-better-landscape"
OUT = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/data" / NS / "lithostitched/worldgen_modifier"

# --- tunables -------------------------------------------------------------
C0 = -0.17          # continentalness of the shoreline (vanilla coast jump is -0.18..-0.16)
WC = 0.024          # continentalness span of the 12-block slope; ~0.002 c/block => ~12 blocks
RISE = 4.0          # slope variant: height gained, blocks (1 block per 3 blocks of length)
CLIFF_MIN = 12.0    # inland height (blocks above sea) from which the cliff+arch variant is used
FLAT_LO, FLAT_HI = 3.0, 4.0   # cliff variant: flat beach rises 3 -> 4 blocks toward the cliff
FLAT_END = 9.0      # b where the flat beach ends and the cliff face starts (~18 blocks of beach)
FACE = 0.4          # b-width of the cliff face
ARCH_Y0 = 67.0      # arch gap floor (sea 63 + 4)
ARCH_H = (3.0, 2.0) # gap height = 3 + 2 * noise (blocks)
ARCH_B = (8.6, 2.0) # corridor starts at b=8.6, extends 2 b-units (~6 blocks) * noise past the face
ARCH_NOISE_MIN = 0.1
OCEAN_FLOOR = -14.84 # vanilla shallow-ocean offset (blocks vs sea) for continents -0.44..-0.18
SEA_OFF = -0.5078125 # vanilla offset at which the surface sits at sea level (y=63)
# --------------------------------------------------------------------------

C = "minecraft:overworld/continents"
MARK = {"type": "lithostitched:wrapped_marker"}
INLAND = f"{NS}:beach/inland_height"
Y = "minecraft:y"

def add(a, b): return {"type": "minecraft:add", "argument1": a, "argument2": b}
def mul(a, b): return {"type": "minecraft:mul", "argument1": a, "argument2": b}
def mn(a, b): return {"type": "minecraft:min", "argument1": a, "argument2": b}
def mx(a, b): return {"type": "minecraft:max", "argument1": a, "argument2": b}
def clamp(x, lo, hi): return {"type": "minecraft:clamp", "input": x, "min": lo, "max": hi}
def rc(x, lo, hi, yes, no):
    return {"type": "minecraft:range_choice", "input": x, "min_inclusive": lo, "max_exclusive": hi,
            "when_in_range": yes, "when_out_of_range": no}
def flat(x): return {"type": "minecraft:flat_cache", "argument": x}
def neg(x): return mul(x, -1.0)
def sub(a, b): return add(a, neg(b))

b = mul(add(C, -C0), RISE / WC)                 # ramp height, blocks (1 per 3 blocks of length)
ob = mul(add(MARK, -SEA_OFF), 128.0)            # vanilla height here, blocks above sea
BIG = 100000.0

def lerp(a, c, t): return add(a, mul(sub(c, a), t))

# slope variant: rise RISE blocks, then merge into the biome height over the next RISE b-units
core = add(mn(b, RISE), mul(mx(sub(INLAND, RISE), 0.0), clamp(mul(sub(b, RISE), 1.0 / RISE), 0.0, 1.0)))
slope = lerp(mn(INLAND, core), ob, clamp(mul(sub(b, 2 * RISE), 1.0 / RISE), 0.0, 1.0))

# cliff variant: flat beach 3 -> 4 blocks, then a short steep face into the vanilla cliff
cliff = add(add(mn(b, FLAT_LO),
                mul(clamp(mul(sub(b, FLAT_LO), 1.0 / (FLAT_END - FLAT_LO)), 0.0, 1.0), FLAT_HI - FLAT_LO)),
            mul(sub(ob, FLAT_HI), clamp(mul(sub(b, FLAT_END), 1.0 / FACE), 0.0, 1.0)))

offshore = mx(b, mn(ob, OCEAN_FLOOR))
land = rc(INLAND, CLIFF_MIN, BIG, cliff, slope)
beach_blocks = rc(b, -BIG, 0.0, offshore, land)
offset = rc(b, -15.0, 14.0, flat(add(SEA_OFF, mul(beach_blocks, 1.0 / 128.0))), MARK)

# flatten zone: m = 1 over the beach, fades out on both sides
m = mul(clamp(mul(add(b, 3.0), 0.5), 0.0, 1.0), clamp(mul(sub(12.0, b), 1.0 / 3.0), 0.0, 1.0))
keep_noise = add(1.0, mul(m, -0.85))   # base_3d_noise keeps 15% inside the zone
keep_jag = add(1.0, neg(m))            # jaggedness removed inside the zone

# arches: 2D mask (flat cached), then a y window
N = {"type": "lithostitched:fast_noise", "config": f"{NS}:arches", "xz_scale": 1.0, "y_scale": 0.0}
nz = clamp(N, 0.0, 1.0)
corridor_end = add(ARCH_B[0] + 0.8, mul(nz, ARCH_B[1]))
mask = flat(rc(INLAND, CLIFF_MIN, BIG,
               rc(N, ARCH_NOISE_MIN, 2.0,
                  rc(b, ARCH_B[0], BIG, rc(sub(corridor_end, b), 0.0, BIG, 1.0, 0.0), 0.0),
                  0.0),
               0.0))
ceiling = add(ARCH_Y0 + ARCH_H[0], mul(nz, ARCH_H[1]))
gap = rc(Y, ARCH_Y0, 400.0, rc(sub(ceiling, Y), 1e-4, BIG, -1.0, 1000.0), 1000.0)
limit = rc(mask, 0.5, 2.0, gap, 1000.0)

def write(name, obj):
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / f"{name}.json").write_text(json.dumps(obj, indent=1) + "\n")

write("beach_offset", {"type": "lithostitched:wrap_density_function", "target_function": "minecraft:overworld/offset",
                       "wrapper_function": offset})
write("beach_flatten_noise", {"type": "lithostitched:wrap_density_function", "target_function": "minecraft:overworld/base_3d_noise",
                              "wrapper_function": mul(MARK, flat(keep_noise))})
write("beach_flatten_jaggedness", {"type": "lithostitched:wrap_density_function", "target_function": "minecraft:overworld/jaggedness",
                                   "wrapper_function": mul(MARK, flat(keep_jag))})
write("beach_arches", {"type": "lithostitched:wrap_noise_router", "dimension": "minecraft:overworld",
                       "target": "final_density", "wrapper_function": mn(MARK, limit)})
print("ok", [p.name for p in OUT.iterdir()])
