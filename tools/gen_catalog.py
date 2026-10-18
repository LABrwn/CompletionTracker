#!/usr/bin/env python3
"""Generates the tracker catalog and the generated language strings.

Input: a checkout of misode/mcmeta for Minecraft 26.3 (tags 26.3-registries and 26.3-summary)
at MCMETA (default /tmp/mc). Output:
  src/main/resources/assets/completiontracker/catalog.json
  src/main/resources/assets/completiontracker/lang/{en_us,de_de}.json  (base strings + generated)

Run:  python3 tools/gen_catalog.py
"""
import json
import os
import re
import sys

MCMETA = os.environ.get('MCMETA', '/tmp/mc')
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/completiontracker')

items_reg = json.load(open(f'{MCMETA}/registries/item/data.json'))
blocks_reg = set(json.load(open(f'{MCMETA}/registries/block/data.json')))
adv_def = json.load(open(f'{MCMETA}/summary/data/advancement/data.min.json'))
comps = json.load(open(f'{MCMETA}/summary/item_components/data.min.json'))
recipes = json.load(open(f'{MCMETA}/summary/data/recipe/data.min.json'))

# ---------------------------------------------------------------- items
EXCL = re.compile(r'(_spawn_egg$|command_block|structure_|^jigsaw$|^barrier$|^light$|debug_stick|knowledge_book|^bedrock$|'
                  r'end_portal_frame|reinforced_deepslate|^spawner$|trial_spawner|^vault$|budding_amethyst|^infested_|'
                  r'petrified_oak_slab|^player_head$|^dirt_path$|^farmland$|^frogspawn$|^chorus_plant$|^test_|^air$|^mannequin)')
TOOLS = re.compile(r'(sword|pickaxe|_axe$|shovel|_hoe$|^bow$|crossbow|trident|^mace$|spear|shield|helmet|chestplate|leggings|boots|elytra|'
                   r'fishing_rod|shears|flint_and_steel|brush|spyglass|_horse_armor|wolf_armor|nautilus_armor|turtle_helmet|^arrow$|'
                   r'tipped_arrow|spectral_arrow|compass|clock$|bucket|lead$|carrot_on_a_stick|warped_fungus_on_a_stick)')
COLORS = ['white', 'light_gray', 'gray', 'black', 'brown', 'pink', 'red', 'orange', 'yellow', 'lime', 'green', 'cyan', 'light_blue', 'blue', 'purple', 'magenta']
COLOR_RE = re.compile(r'^(%s)_' % '|'.join(sorted(COLORS, key=len, reverse=True)))
VERY = {'dragon_head', 'dragon_egg', 'enchanted_golden_apple', 'sniffer_egg', 'heavy_core', 'music_disc_pigstep', 'music_disc_otherside',
        'music_disc_5', 'music_disc_relic', 'music_disc_creator', 'music_disc_precipice', 'music_disc_creator_music_box', 'piglin_head',
        'creeper_head', 'zombie_head', 'skeleton_skull', 'silence_armor_trim_smithing_template', 'ward_armor_trim_smithing_template',
        'eye_armor_trim_smithing_template', 'vex_armor_trim_smithing_template', 'wayfinder_armor_trim_smithing_template',
        'raiser_armor_trim_smithing_template', 'shaper_armor_trim_smithing_template', 'host_armor_trim_smithing_template',
        'rib_armor_trim_smithing_template', 'spire_armor_trim_smithing_template', 'flow_armor_trim_smithing_template',
        'bolt_armor_trim_smithing_template', 'mojang_banner_pattern', 'netherite_block', 'beacon', 'brown_mushroom_block',
        'mushroom_stem', 'red_mushroom_block', 'mycelium'}
HARD_KW = re.compile(r'(netherite|elytra|trident|nether_star|shulker|totem|heart_of_the_sea|conduit|echo_shard|recovery_compass|'
                     r'disc_fragment|music_disc|ominous|wither|sponge|dragon_breath|sculk_|_smithing_template|pottery_sherd|end_crystal|'
                     r'blue_ice|turtle_scute|armadillo_scute|nautilus_shell|trial_key|breeze_rod|mace|banner_pattern|goat_horn|'
                     r'enchanted_book|experience_bottle|_head$|skull|chorus|purpur|end_rod)')
MID_KW = re.compile(r'(diamond|emerald|nether|blaze|ghast|glowstone|quartz|magma|crimson|warped|soul|basalt|blackstone|ender|end_stone|'
                    r'prismarine|sea_lantern|slime|honey|amethyst|copper|dye|wool|carpet|concrete|terracotta|glazed|stained|candle|bed$|'
                    r'banner$|cherry|mangrove|bamboo|pale_oak|moss|azalea|dripleaf|axolotl|tropical|pufferfish|cocoa|cactus|pumpkin|melon|'
                    r'sweet_berries|glow_berries|kelp|coral|sniffer|torchflower|pitcher|frog|froglight|tuff|calcite|resin|sulfur|poplar|'
                    r'creaking|eyeblossom|lapis|redstone|gold|potion|map$|name_tag|saddle|lead)')

def comp(i):
    return comps.get(i) or {}

def diff_item(i):
    rar = comp(i).get('minecraft:rarity')
    if i in VERY:
        return 4
    if HARD_KW.search(i) or rar in ('rare', 'epic'):
        return 3
    if MID_KW.search(i) or rar == 'uncommon':
        return 2
    return 1

def cat_item(i):
    c = comp(i)
    if 'minecraft:food' in c:
        return 'food'
    if TOOLS.search(i) or ('minecraft:equippable' in c and 'minecraft:max_damage' in c):
        return 'tools'
    if COLOR_RE.match(i) and i not in ('red_mushroom', 'brown_mushroom', 'red_sand', 'red_sandstone', 'red_nether_bricks'):
        return 'colored'
    if i in blocks_reg:
        return 'blocks'
    return 'other'

STATION = {'crafting_shaped': 'crafting', 'crafting_shapeless': 'crafting', 'crafting_transmute': 'crafting',
           'crafting_special_bannerduplicate': 'crafting', 'smelting': 'smelting', 'blasting': 'blasting', 'smoking': 'smoking',
           'campfire_cooking': 'campfire', 'stonecutting': 'stonecutter', 'smithing_transform': 'smithing'}
crafted_by = {}
for r in recipes.values():
    res = r.get('result')
    rid = res.get('id') if isinstance(res, dict) else res
    if rid:
        rid = rid.split(':')[-1]
        st = STATION.get(r['type'].split(':')[1])
        if st and rid not in crafted_by:
            crafted_by[rid] = st

ITEM_HINTS = {  # id: (en, de)
    'enchanted_golden_apple': ('Cannot be crafted. Only in chests of bastions, ruined portals, ancient cities and desert pyramids.',
                               'Nicht herstellbar. Nur in Truhen von Bastionen, Ruinenportalen, Antiken Städten und Wüstentempeln.'),
    'sniffer_egg': ('Brush suspicious sand in warm ocean ruins.', 'Verdächtigen Sand in Ruinen warmer Ozeane mit dem Pinsel abtragen.'),
    'dragon_head': ('On the bow of end ships near end cities.', 'Am Bug von Endschiffen bei Endstädten.'),
    'dragon_egg': ('Appears on the exit portal after the first dragon fight. Break it with a piston or the torch trick.',
                   'Nach dem ersten Drachensieg auf dem Ausgangsportal. Mit Kolben oder Fackel-Trick abbauen.'),
    'heavy_core': ('Rare reward from an ominous vault in trial chambers.', 'Seltene Beute aus dem unheilvollen Tresor in Prüfungskammern.'),
    'music_disc_pigstep': ('Only in bastion remnant chests.', 'Nur in Truhen von Bastionsruinen.'),
    'music_disc_otherside': ('Chests in strongholds and ancient cities.', 'Truhen in Festungen und Antiken Städten.'),
    'music_disc_relic': ('Brush suspicious gravel in trail ruins.', 'Verdächtigen Kies in Pfadruinen mit dem Pinsel abtragen.'),
    'music_disc_5': ('Craft from nine disc fragments found in ancient cities.', 'Aus neun Plattenbruchstücken aus Antiken Städten herstellen.'),
    'nether_star': ('Dropped by the Wither.', 'Droppt vom Wither.'),
    'totem_of_undying': ('Dropped by evokers (woodland mansions, raids).', 'Droppt vom Magier (Waldanwesen, Überfälle).'),
    'heart_of_the_sea': ('Buried treasure; maps are in shipwrecks and ocean ruins.', 'Vergrabene Schätze, Karten in Schiffswracks und Ozeanruinen.'),
    'trident': ('Rare drop from drowned holding a trident.', 'Seltener Drop von Ertrunkenen, die einen Dreizack halten.'),
    'shulker_shell': ('Dropped by shulkers in end cities.', 'Droppt von Shulkern in Endstädten.'),
    'elytra': ('In end ships near some end cities.', 'In Endschiffen bei manchen Endstädten.'),
    'echo_shard': ('Chests in ancient cities.', 'Truhen in Antiken Städten.'),
    'sponge': ('Dropped by elder guardians or found in monument rooms.', 'Droppt vom Großen Wächter oder liegt in Monument-Kammern.'),
    'wither_skeleton_skull': ('Rare drop from wither skeletons; Looting helps.', 'Seltener Drop von Wither-Skeletten, Plünderung hilft.'),
    'piglin_head': ('A charged creeper must blow up a piglin.', 'Ein geladener Creeper muss einen Piglin sprengen.'),
    'creeper_head': ('A charged creeper must blow up a creeper.', 'Ein geladener Creeper muss einen Creeper sprengen.'),
    'zombie_head': ('A charged creeper must blow up a zombie.', 'Ein geladener Creeper muss einen Zombie sprengen.'),
    'skeleton_skull': ('A charged creeper must blow up a skeleton.', 'Ein geladener Creeper muss ein Skelett sprengen.'),
    'blue_ice': ('Icebergs in frozen oceans, needs Silk Touch.', 'Eisberge in gefrorenen Ozeanen, mit Behutsamkeit.'),
    'mycelium': ('Mushroom fields, needs Silk Touch.', 'Pilzfelder, nur mit Behutsamkeit.'),
    'brown_mushroom_block': ('Huge mushrooms, needs Silk Touch.', 'Riesenpilze, nur mit Behutsamkeit.'),
    'red_mushroom_block': ('Huge mushrooms, needs Silk Touch.', 'Riesenpilze, nur mit Behutsamkeit.'),
    'mushroom_stem': ('Huge mushrooms, needs Silk Touch.', 'Riesenpilze, nur mit Behutsamkeit.'),
    'mojang_banner_pattern': ('Craft from an enchanted golden apple and paper.', 'Aus einem verzauberten goldenen Apfel und Papier herstellen.'),
    'beacon': ('Needs a nether star from the Wither.', 'Braucht einen Netherstern vom Wither.'),
    'conduit': ('Needs a heart of the sea and nautilus shells.', 'Braucht ein Herz des Meeres und Nautilusschalen.'),
    'nautilus_shell': ('Fishing treasure, wandering trader, or drowned holding one.', 'Angelschatz, Fahrender Händler oder Ertrunkene, die eine halten.'),
    'goat_horn': ('Goats ramming into stone, logs or ore drop horns.', 'Ziegen, die gegen Stein, Stämme oder Erz rammen, verlieren Hörner.'),
    'experience_bottle': ('Cleric trades and chests.', 'Handel mit Geistlichen und Truhen.'),
    'enchanted_book': ('Enchanting table, librarian trades, fishing and chests.', 'Zaubertisch, Bibliothekar-Handel, Angeln und Truhen.'),
    'saddle': ('Chests, fishing, or leatherworker trades.', 'Truhen, Angeln oder Handel mit Gerbern.'),
    'name_tag': ('Chests, fishing, or librarian trades.', 'Truhen, Angeln oder Handel mit Bibliothekaren.'),
    'end_crystal': ('Craft with a ghast tear, eye of ender and glass.', 'Aus Ghastträne, Enderauge und Glas herstellen.'),
    'recovery_compass': ('Craft with echo shards from ancient cities.', 'Mit Echosplittern aus Antiken Städten herstellen.'),
    'disc_fragment_5': ('Chests in ancient cities.', 'Truhen in Antiken Städten.'),
    'trial_key': ('Dropped by trial spawners after a wave is defeated.', 'Fällt aus Prüfungs-Spawnern, wenn eine Welle besiegt ist.'),
    'ominous_trial_key': ('Dropped by ominous trial spawners.', 'Fällt aus unheilvollen Prüfungs-Spawnern.'),
    'breeze_rod': ('Dropped by breezes in trial chambers.', 'Droppt von Böen in Prüfungskammern.'),
    'armadillo_scute': ('Brush an armadillo.', 'Ein Gürteltier mit dem Pinsel bürsten.'),
    'turtle_scute': ('Baby turtles drop them when they grow up.', 'Babyschildkröten lassen sie beim Erwachsenwerden fallen.'),
    'dragon_breath': ('Collect the dragon\'s breath attack with a glass bottle.', 'Den Drachenatem mit einer Glasflasche auffangen.'),
}
GENERIC = {
    'crafting': ('Craft it at a crafting table.', 'Lässt sich an der Werkbank herstellen.'),
    'smelting': ('Smelt it in a furnace.', 'Lässt sich im Ofen schmelzen.'),
    'blasting': ('Smelt it in a blast furnace.', 'Lässt sich im Schmelzofen schmelzen.'),
    'smoking': ('Cook it in a smoker.', 'Lässt sich im Räucherofen garen.'),
    'campfire': ('Cook it on a campfire.', 'Lässt sich auf dem Lagerfeuer garen.'),
    'stonecutter': ('Make it at a stonecutter.', 'Lässt sich an der Steinsäge herstellen.'),
    'smithing': ('Upgrade it at a smithing table.', 'Lässt sich am Schmiedetisch aufwerten.'),
    'natural': ('Found naturally in the world. Mine it with the right tool; some blocks need Silk Touch.',
                'Kommt natürlich in der Welt vor. Mit passendem Werkzeug abbauen, manche Blöcke nur mit Behutsamkeit.'),
    'drop': ('Dropped by mobs, found in chests, or traded.', 'Drop von Mobs, aus Truhen oder durch Handel.'),
    'trim': ('Found in structure chests (each template in a different structure). Duplicate with diamonds.',
             'In Strukturtruhen (je Schablone eine andere Struktur). Mit Diamanten vervielfältigen.'),
    'sherd': ('Brush suspicious sand or gravel in ruins and trail ruins.', 'Verdächtigen Sand oder Kies in Ruinen und Pfadruinen mit dem Pinsel abtragen.'),
    'disc': ('Have a skeleton shoot a creeper, or look in structure chests.', 'Einen Creeper von einem Skelett erschießen lassen oder Strukturtruhen durchsuchen.'),
}

def hint_item(i):
    if i in ITEM_HINTS:
        return 'item.' + i
    if i.endswith('_armor_trim_smithing_template'):
        return 'trim'
    if i.endswith('_pottery_sherd'):
        return 'sherd'
    if i.startswith('music_disc_'):
        return 'disc'
    if i in crafted_by:
        return crafted_by[i]
    if i in blocks_reg:
        return 'natural'
    return 'drop'

items = []
for i in items_reg:
    if EXCL.search(i):
        continue
    items.append({'id': 'minecraft:' + i, 'cat': cat_item(i), 'diff': diff_item(i), 'hint': hint_item(i)})

# ---------------------------------------------------------------- mobs
eggs = [i[:-10] for i in items_reg if i.endswith('_spawn_egg')]
hostile = [k.split(':')[-1] for k in adv_def['adventure/kill_all_mobs']['criteria']]
NEUTRAL = {'enderman', 'piglin', 'zombified_piglin', 'wolf', 'bee', 'llama', 'trader_llama', 'iron_golem', 'polar_bear', 'panda',
           'dolphin', 'goat', 'spider', 'cave_spider', 'happy_ghast', 'copper_golem'}
BOSS = {'ender_dragon', 'wither', 'warden', 'elder_guardian'}

def mob_cat(m):
    if m in BOSS:
        return 'boss'
    if m in NEUTRAL:
        return 'neutral'
    if m in hostile:
        return 'hostile'
    return 'passive'

MOB_DIFF = {'ender_dragon': 3, 'wither': 4, 'warden': 4, 'elder_guardian': 3, 'shulker': 3, 'ravager': 3, 'evoker': 3, 'piglin_brute': 3,
            'mooshroom': 3, 'axolotl': 4, 'panda': 3, 'zombie_villager': 4, 'sniffer': 3, 'happy_ghast': 2, 'nautilus': 2,
            'zombie_nautilus': 2, 'breeze': 2, 'bogged': 2, 'creaking': 2, 'parched': 2, 'camel_husk': 2, 'vex': 2, 'zoglin': 3,
            'skeleton_horse': 3, 'zombie_horse': 3, 'wandering_trader': 2, 'trader_llama': 2, 'polar_bear': 2, 'goat': 2, 'allay': 3,
            'guardian': 2, 'glow_squid': 2, 'dolphin': 2, 'turtle': 2, 'ocelot': 2, 'endermite': 2, 'witch': 2, 'blaze': 2, 'ghast': 2,
            'wither_skeleton': 2, 'phantom': 2, 'piglin': 2, 'hoglin': 2, 'strider': 2, 'magma_cube': 2, 'stray': 2, 'husk': 2,
            'sulfur_cube': 2, 'copper_golem': 2, 'cat': 2, 'wolf': 2, 'sheep': 2, 'horse': 2, 'rabbit': 2, 'frog': 2, 'villager': 2}
MOB_HINTS = {
    'ender_dragon': ('In the End. Destroy the end crystals on the obsidian pillars first.', 'Im End. Erst die Endkristalle auf den Obsidiansäulen zerstören.'),
    'wither': ('Summon it yourself: 4 soul sand and 3 wither skeleton skulls.', 'Selbst beschwören: 4 Seelensand und 3 Wither-Skelettschädel.'),
    'warden': ('Deep dark; appears after a sculk shrieker is triggered three times.', 'Tiefe Dunkelheit, erscheint nach dreimaligem Auslösen eines Sculk-Kreischers.'),
    'elder_guardian': ('Three in every ocean monument.', 'Drei pro Ozeanmonument.'),
    'shulker': ('End cities in the outer End.', 'Endstädte im äußeren End.'),
    'ravager': ('Raids, from wave 3 on.', 'Bei Überfällen ab Welle 3.'),
    'evoker': ('Woodland mansions or raids.', 'Waldanwesen oder Überfälle.'),
    'piglin_brute': ('Bastion remnants; they do not respawn.', 'Bastionsruinen, spawnen nicht nach.'),
    'vex': ('Summoned by evokers.', 'Wird vom Magier beschworen.'),
    'breeze': ('Trial chambers.', 'Prüfungskammern.'),
    'bogged': ('Trial chambers and swamps.', 'Prüfungskammern und Sümpfe.'),
    'creaking': ('Pale garden at night. Destroy its heart.', 'Blasser Garten, nachts. Sein Herz zerstören.'),
    'parched': ('Skeleton variant in deserts.', 'Skelett-Variante in Wüsten.'),
    'camel_husk': ('Desert villages at night.', 'Wüstendörfer bei Nacht.'),
    'sulfur_cube': ('Sulfur caves.', 'Schwefelhöhlen.'),
    'zombie_nautilus': ('Oceans at night.', 'Ozeane bei Nacht.'),
    'nautilus': ('Warm oceans.', 'Warme Ozeane.'),
    'happy_ghast': ('Put a dried ghast in water and raise it.', 'Ausgetrockneten Ghast ins Wasser setzen und großziehen.'),
    'copper_golem': ('Build it from a copper block and a carved pumpkin.', 'Aus einem Kupferblock und einem geschnitzten Kürbis bauen.'),
    'iron_golem': ('In villages.', 'In Dörfern.'),
    'snow_golem': ('Two snow blocks and a carved pumpkin.', 'Zwei Schneeblöcke und ein geschnitzter Kürbis.'),
    'allay': ('Pillager outposts (cages) and woodland mansions.', 'Plünderer-Außenposten (Käfige) und Waldanwesen.'),
    'zoglin': ('Bring a hoglin into the Overworld.', 'Einen Hoglin in die Oberwelt bringen.'),
    'skeleton_horse': ('Skeleton trap during thunderstorms.', 'Skelettfalle bei Gewitter.'),
    'zombie_horse': ('Spawns at night with a rider.', 'Spawnt nachts mit Reiter.'),
    'mooshroom': ('Only in mushroom fields.', 'Nur auf Pilzfeldern.'),
    'axolotl': ('Lush caves, under water.', 'Üppige Höhlen, unter Wasser.'),
    'sniffer': ('Hatch a sniffer egg.', 'Ein Schnüffler-Ei ausbrüten.'),
    'tadpole': ('Breed frogs; the spawn hatches in water.', 'Frösche züchten, der Laich schlüpft im Wasser.'),
    'mule': ('Breed a horse with a donkey.', 'Pferd und Esel züchten.'),
    'endermite': ('Sometimes appears when you throw ender pearls.', 'Selten beim Werfen von Enderperlen.'),
    'witch': ('Swamp huts.', 'Hexenhütten im Sumpf.'),
    'wandering_trader': ('Appears randomly near the player.', 'Taucht zufällig in der Nähe des Spielers auf.'),
    'trader_llama': ('Comes with the wandering trader.', 'Begleitet den Fahrenden Händler.'),
}
GENERIC_MOB = {'hostile': ('Spawns in the right biomes or at night.', 'Spawnt in passenden Biomen oder bei Nacht.'),
               'passive': ('Spawns in the right biomes.', 'Spawnt in passenden Biomen.'),
               'neutral': ('Spawns in the right biomes; only attacks when provoked.', 'Spawnt in passenden Biomen, greift nur an, wenn provoziert.'),
               'boss': ('', '')}

T = lambda en, de, h_en='', h_de='': (en, de, h_en, h_de)
TCW = [('temperate', T('Temperate', 'Gemäßigt', 'Plains, forests', 'Ebenen, Wälder')),
       ('cold', T('Cold', 'Kalt', 'Taiga, snowy biomes', 'Taiga, verschneite Biome')),
       ('warm', T('Warm', 'Warm', 'Savanna, jungle, badlands, desert', 'Savanne, Dschungel, Badlands, Wüste'))]
COLOR_EN = {c: c.replace('_', ' ').title() for c in COLORS}
COLOR_DE = {'white': 'Weiß', 'light_gray': 'Hellgrau', 'gray': 'Grau', 'black': 'Schwarz', 'brown': 'Braun', 'pink': 'Rosa', 'red': 'Rot',
            'orange': 'Orange', 'yellow': 'Gelb', 'lime': 'Hellgrün', 'green': 'Grün', 'cyan': 'Türkis', 'light_blue': 'Hellblau',
            'blue': 'Blau', 'purple': 'Violett', 'magenta': 'Magenta'}
SHEEP_H = T('', '', 'Naturally white, grays, black, brown, rarely pink; otherwise dye a sheep.',
            'Natürlich Weiß, Grautöne, Schwarz, Braun, selten Rosa. Sonst ein Schaf färben.')
VT = [('plains', 'Plains', 'Ebenen'), ('desert', 'Desert', 'Wüste'), ('savanna', 'Savanna', 'Savanne'), ('taiga', 'Taiga', 'Taiga'),
      ('snow', 'Snow', 'Schnee'), ('jungle', 'Jungle', 'Dschungel'), ('swamp', 'Swamp', 'Sumpf')]
VARIANTS = {
    'cow': TCW, 'pig': TCW, 'chicken': TCW,
    'sheep': [(c, T(COLOR_EN[c], COLOR_DE[c], SHEEP_H[2], SHEEP_H[3])) for c in COLORS],
    'horse': [(k, T(en, de, 'Search herds in plains and savannas, or breed', 'Herden in Ebenen und Savannen absuchen oder züchten'))
              for k, en, de in [('white', 'White', 'Weiß'), ('creamy', 'Creamy', 'Creme'), ('chestnut', 'Chestnut', 'Kastanie'),
                                ('brown', 'Brown', 'Braun'), ('black', 'Black', 'Schwarz'), ('gray', 'Gray', 'Grau'),
                                ('dark_brown', 'Dark Brown', 'Dunkelbraun')]],
    'rabbit': [('brown', T('Brown', 'Braun', 'Forests, plains', 'Wälder, Ebenen')), ('white', T('White', 'Weiß', 'Snowy biomes', 'Verschneite Biome')),
               ('black', T('Black', 'Schwarz', 'Forests, plains', 'Wälder, Ebenen')), ('gold', T('Gold', 'Gold', 'Desert', 'Wüste')),
               ('salt', T('Salt & Pepper', 'Salz und Pfeffer', 'Forests, plains', 'Wälder, Ebenen')),
               ('white_splotched', T('White Splotched', 'Weiß gefleckt', 'Snowy biomes', 'Verschneite Biome')),
               ('toast', T('Toast', 'Toast', 'Name a rabbit "Toast" with a name tag', 'Ein Kaninchen per Namensschild „Toast“ nennen'))],
    'cat': [(k, T(en, de, 'Stray cats in villages' if k != 'all_black' else 'Swamp huts',
                  'Streuner in Dörfern' if k != 'all_black' else 'Hexenhütte im Sumpf'))
            for k, en, de in [('tabby', 'Tabby', 'Getigert'), ('black', 'Tuxedo', 'Smoking'), ('red', 'Red', 'Rot'), ('siamese', 'Siamese', 'Siam'),
                              ('british_shorthair', 'British Shorthair', 'Britisch Kurzhaar'), ('calico', 'Calico', 'Calico'),
                              ('persian', 'Persian', 'Perser'), ('ragdoll', 'Ragdoll', 'Ragdoll'), ('white', 'White', 'Weiß'),
                              ('jellie', 'Jellie', 'Jellie'), ('all_black', 'Black', 'Schwarz')]],
    'parrot': [(k, T(en, de, 'Jungles, random colour', 'Dschungel, zufällige Farbe'))
               for k, en, de in [('red_blue', 'Red', 'Rot'), ('blue', 'Blue', 'Blau'), ('green', 'Green', 'Grün'),
                                 ('yellow_blue', 'Cyan', 'Cyan'), ('gray', 'Gray', 'Grau')]],
    'frog': [('temperate', T('Temperate', 'Gemäßigt', 'Swamps', 'Sümpfe')),
             ('cold', T('Cold', 'Kalt', 'Let a tadpole grow up in a cold biome', 'Kaulquappe in kaltem Biom aufwachsen lassen')),
             ('warm', T('Warm', 'Warm', 'Let a tadpole grow up in a warm biome', 'Kaulquappe in warmem Biom aufwachsen lassen'))],
    'fox': [('red', T('Red', 'Normal', 'Taiga', 'Taiga')), ('snow', T('Snow', 'Schnee', 'Snowy taiga, groves, snowy slopes', 'Verschneite Taiga, Haine, verschneite Hänge'))],
    'llama': [(k, T(en, de, 'Savannas and mountains', 'Savannen und Berge'))
              for k, en, de in [('brown', 'Brown', 'Braun'), ('creamy', 'Creamy', 'Creme'), ('gray', 'Gray', 'Grau'), ('white', 'White', 'Weiß')]],
    'mooshroom': [('red', T('Red', 'Rot', 'Mushroom fields', 'Pilzfelder')),
                  ('brown', T('Brown', 'Braun', 'Lightning must strike a red mooshroom', 'Ein Blitz muss in eine rote Pilzkuh einschlagen'))],
    'axolotl': [(k, T(en, de, h_en, h_de)) for k, en, de, h_en, h_de in [
        ('lucy', 'Pink', 'Rosa', 'Lush caves', 'Üppige Höhlen'), ('wild', 'Brown', 'Braun', 'Lush caves', 'Üppige Höhlen'),
        ('gold', 'Gold', 'Gold', 'Lush caves', 'Üppige Höhlen'), ('cyan', 'Cyan', 'Cyan', 'Lush caves', 'Üppige Höhlen'),
        ('blue', 'Blue', 'Blau', 'Only by breeding, 1 in 1200', 'Nur durch Züchten, Chance 1 zu 1200')]],
    'villager': [(k, T(en, de, 'Find a village there, or breed villagers in that biome', 'Dorf dort finden oder Dorfbewohner in diesem Biom züchten')) for k, en, de in VT],
    'zombie_villager': [(k, T(en, de, 'At night in that biome', 'Nachts in diesem Biom')) for k, en, de in VT],
    'panda': [(k, T(en, de, 'Bamboo jungles' if k != 'brown' else 'Breed two pandas carrying the brown gene',
                    'Bambusdschungel' if k != 'brown' else 'Zwei Pandas mit braunem Gen züchten'))
              for k, en, de in [('normal', 'Normal', 'Normal'), ('lazy', 'Lazy', 'Faul'), ('worried', 'Worried', 'Besorgt'),
                                ('playful', 'Playful', 'Verspielt'), ('aggressive', 'Aggressive', 'Aggressiv'), ('weak', 'Weak', 'Schwach'),
                                ('brown', 'Brown', 'Braun')]],
    'wolf': [(k, T(en, de, b_en, b_de)) for k, en, de, b_en, b_de in [
        ('pale', 'Pale', 'Standard', 'Taiga', 'Taiga'), ('ashen', 'Ashen', 'Aschgrau', 'Snowy taiga', 'Verschneite Taiga'),
        ('black', 'Black', 'Schwarz', 'Old growth pine taiga', 'Alte Kiefern-Taiga'),
        ('chestnut', 'Chestnut', 'Kastanie', 'Old growth spruce taiga', 'Alte Fichten-Taiga'),
        ('rusty', 'Rusty', 'Rostrot', 'Sparse jungle', 'Lichter Dschungel'), ('snowy', 'Snowy', 'Schnee', 'Grove', 'Hain'),
        ('spotted', 'Spotted', 'Gefleckt', 'Savanna plateau', 'Savannen-Hochebene'),
        ('striped', 'Striped', 'Gestreift', 'Wooded badlands', 'Bewaldete Badlands'), ('woods', 'Woods', 'Wald', 'Forest', 'Wald')]],
}

lang = {'en': {}, 'de': {}}
def put(key, en, de):
    lang['en'][key] = en
    lang['de'][key] = de

mobs = []
for m in eggs:
    c = mob_cat(m)
    hint = None
    if m in MOB_HINTS:
        hint = 'mob.' + m
        put('completiontracker.hint.mob.' + m, *MOB_HINTS[m])
    elif c != 'boss':
        hint = 'mob_' + c
    vs = []
    for k, (en, de, h_en, h_de) in VARIANTS.get(m, []):
        put(f'completiontracker.variant.{m}.{k}', en, de)
        if h_en:
            put(f'completiontracker.variant_hint.{m}.{k}', h_en, h_de)
        vs.append(k)
    mobs.append({'id': 'minecraft:' + m, 'cat': c, 'diff': MOB_DIFF.get(m, 1), 'hint': hint, 'variants': vs})
for c, (en, de) in GENERIC_MOB.items():
    if en:
        put('completiontracker.hint.mob_' + c, en, de)
for k, (en, de) in GENERIC.items():
    put('completiontracker.hint.' + k, en, de)
for k, (en, de) in ITEM_HINTS.items():
    put('completiontracker.hint.item.' + k, en, de)

# ---------------------------------------------------------------- advancements
ADV_DIFF = {'nether/all_effects': 4, 'nether/all_potions': 4, 'adventure/adventuring_time': 3, 'husbandry/balanced_diet': 3,
            'adventure/kill_all_mobs': 4, 'husbandry/bred_all_animals': 3, 'husbandry/complete_catalogue': 3, 'husbandry/whole_pack': 3,
            'adventure/trim_with_all_exclusive_armor_patterns': 4, 'nether/explore_nether': 3, 'nether/create_full_beacon': 3,
            'adventure/two_birds_one_arrow': 3, 'adventure/arbalistic': 3, 'nether/uneasy_alliance': 3, 'adventure/hero_of_the_village': 3,
            'end/levitate': 3}
FRAME = {'task': 1, 'goal': 2, 'challenge': 3}
advs = {}
for key, d in adv_def.items():
    if key.startswith('recipes/') or 'display' not in d:
        continue
    advs['minecraft:' + key] = ADV_DIFF.get(key, FRAME.get(d['display'].get('frame', 'task'), 1))

catalog = {'version': '26.3', 'items': items, 'mobs': mobs, 'advancementDifficulty': advs}
with open(os.path.join(ASSETS, 'catalog.json'), 'w', encoding='utf-8') as f:
    json.dump(catalog, f, ensure_ascii=False, separators=(',', ':'))

base = {code: json.load(open(os.path.join(ROOT, f'tools/lang_base_{code}.json'), encoding='utf-8')) for code in ('en', 'de')}
for code, file in (('en', 'en_us'), ('de', 'de_de')):
    out = dict(base[code])
    out.update(lang[code])
    with open(os.path.join(ASSETS, f'lang/{file}.json'), 'w', encoding='utf-8') as f:
        json.dump(out, f, ensure_ascii=False, indent='\t', sort_keys=True)
print(f'{len(items)} items, {len(mobs)} mobs, {len(advs)} advancements, {len(lang["en"])} generated strings')
