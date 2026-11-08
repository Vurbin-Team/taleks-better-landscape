"""Генератор lithostitched/worldgen_modifier/windswept_surface.json.
Меняйте пороги в словаре P и запустите: python3 docs/tools/gen_windswept_surface.py
Пороги это значения шума: выше число = реже. STEEP = с какого перепада высот (slope) склон считается скалой.
"""
import json
import os
ROOT=os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
R=ROOT+'/src/main/resources/data/taleks-better-landscape/'
W=R+'lithostitched/worldgen_modifier/'
def save(p,o): open(p,'w').write(json.dumps(o,indent=2,ensure_ascii=False)+'\n')
NS='taleks-better-landscape:'
def blk(n): return {"type":"minecraft:block","result_state":{"Name":"minecraft:"+n}}
def cond(c,then): return {"type":"minecraft:condition","if_true":c,"then_run":then}
def seq(*s): return {"type":"minecraft:sequence","sequence":list(s)}
def depth(off): return {"type":"minecraft:stone_depth","offset":off,"surface_type":"floor","add_surface_depth":False,"secondary_depth_range":0}
def noise(n,lo,hi=10.0): return {"type":"minecraft:noise_threshold","noise":n,"min_threshold":lo,"max_threshold":hi}
def slope(lo,hi=256): return {"type":"lithostitched:slope","height_difference":[lo,hi]}
HEATH=NS+'windswept_heather'; ROCK=NS+'windswept_rock_patch'; TONE=NS+'peak_rock_tone'; DITH=NS+'forest_floor_dither'
def layers(top): return seq(cond(depth(0),blk(top)), blk('dirt'))
def rock_column(): return seq(cond(depth(0), seq(cond(noise(TONE,0.3),blk('andesite')),blk('stone'))), blk('stone'))
def gravel_column(): return seq(cond(depth(0),blk('gravel')), cond(depth(1),blk('gravel')), blk('dirt'))
def speckle(lo,hi,then): return cond(noise(ROCK,lo,hi), cond(noise(DITH,0.0), then))

# ---- knobs (thresholds of noise values; higher = rarer) ----
P = {
 'hills':   dict(STONE=0.62, GRAVEL=0.50, GRAVEL_EDGE=0.44, COARSE=None,         STEEP=3, HEATH=0.40, HEATH_EDGE=0.32),
 'gravelly':dict(STONE=0.52, GRAVEL=0.30, GRAVEL_EDGE=0.22, COARSE=(0.16,0.22),  STEEP=2, HEATH=0.45, HEATH_EDGE=0.37),
}
def build(p):
    s=[cond(slope(p['STEEP']), rock_column()),
       cond(slope(1,p['STEEP']-1), seq(
           cond(noise(ROCK,p['STONE']), rock_column()),
           cond(noise(ROCK,p['GRAVEL']), gravel_column()),
           speckle(p['GRAVEL_EDGE'],p['GRAVEL'],gravel_column())))]
    flat=[]
    if p['COARSE']:
        flat+=[cond(noise(ROCK,p['STONE']), rock_column()),
               cond(noise(ROCK,p['GRAVEL']), gravel_column()),
               speckle(p['GRAVEL_EDGE'],p['GRAVEL'],gravel_column()),
               cond(noise(ROCK,*p['COARSE']), layers('coarse_dirt'))]
    flat+=[cond(noise(HEATH,p['HEATH']), layers('podzol')),
           cond(noise(HEATH,p['HEATH_EDGE'],p['HEATH']), cond(noise(DITH,0.0), layers('podzol'))),
           cond(noise(HEATH,p['HEATH_EDGE']-0.07,p['HEATH_EDGE']), layers('coarse_dirt'))]
    s.append(cond(slope(0,1), seq(*flat)))
    s.append(layers('grass_block'))
    return seq(*s)
def biome(ids): return {"type":"minecraft:biome","biome_is":["minecraft:"+i for i in ids]}
def wrap(ids,inner):
    return cond(biome(ids), cond({"type":"minecraft:above_preliminary_surface"},
      cond({"type":"minecraft:water","offset":-1,"surface_depth_multiplier":0,"add_stone_depth":False}, cond(depth(3),inner))))
rule=seq(wrap(['windswept_gravelly_hills'],build(P['gravelly'])),
         wrap(['windswept_hills','windswept_forest','windswept_savanna'],build(P['hills'])))
save(W+'windswept_surface.json',{"type":"lithostitched:add_surface_rule","priority":880,"levels":["minecraft:overworld"],"surface_rule":rule})
