"""Rebuild original pixel-art assets and synthesized sounds (Pillow + ffmpeg).

Generated assets are committed; players and Java builds need neither dependency.
"""
from pathlib import Path
import json
import math
import random
import struct
import subprocess
import tempfile
import wave
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'
ASSETS = ROOT / 'assets/accesscontrol'
DATA = ROOT / 'data/accesscontrol'
COLORS = ['#46d88b', '#4eb9ff', '#ddabff', '#ffc958']


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def texture(path, image):
    target = ASSETS / 'textures' / (path + '.png')
    target.parent.mkdir(parents=True, exist_ok=True)
    image.save(target)


def model(name, value):
    write_json(ASSETS / 'models' / (name + '.json'), value)


def item(name, block=False):
    write_json(ASSETS / 'items' / (name + '.json'), {
        'model': {'type': 'minecraft:model', 'model': f'accesscontrol:{"block" if block else "item"}/{name}'}})


def box(start, end, tex, front=None):
    return {'from': start, 'to': end, 'faces': {
        side: {'texture': front if front and side == 'north' else tex}
        for side in ['north', 'south', 'east', 'west', 'up', 'down']}}


def make_cards():
    # Different color, printed number, stripe count, and circuit layout per clearance.
    for level, color in enumerate(COLORS, 1):
        name = f'access_card_{level}'
        im = Image.new('RGBA', (32, 32))
        d = ImageDraw.Draw(im)
        d.rounded_rectangle((2, 5, 29, 26), radius=2, fill='#142634', outline='#c8dce4', width=1)
        d.rectangle((3, 6, 28, 10), fill=color)
        d.rectangle((5, 13, 11, 19), fill='#d8b96c', outline='#8c733d')
        d.line((8, 13, 8, 19), fill='#806637')
        d.line((5, 16, 11, 16), fill='#806637')
        d.text((18, 10), str(level), fill=color, stroke_width=0)
        for i in range(level):
            d.rectangle((5 + i * 5, 22, 7 + i * 5, 23), fill=color)
        for i in range(level + 1):
            d.line((13, 13 + i * 2, 15 + i, 13 + i * 2), fill='#526d7a')
        texture('item/' + name, im)
        model('item/' + name, {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'accesscontrol:item/{name}'}})
        item(name)


def make_readers():
    body = Image.new('RGBA', (32, 32), '#283746')
    d = ImageDraw.Draw(body)
    for x in range(0, 32, 8):
        d.line((x, 0, x, 31), fill='#314452')
    texture('block/reader_body', body)
    variants = {}
    for level in range(1, 5):
        for status, color in enumerate(['#50bad2', '#45f294', '#ff555e']):
            name = f'reader_l{level}_s{status}'
            im = Image.new('RGBA', (32, 32), '#17222f')
            d = ImageDraw.Draw(im)
            d.rectangle((0, 0, 31, 31), outline='#70899c', width=2)
            d.rectangle((4, 4, 27, 17), fill='#0c141c', outline='#3a5567')
            d.text((8, 4), 'L' + str(level), fill=COLORS[level - 1])
            d.rectangle((5, 22, 26, 24), fill='#080e16')
            d.line((6, 25, 25, 25), fill='#597082')
            d.rectangle((4, 28, 27, 29), fill=color)
            for x, y in [(2, 2), (29, 2), (2, 29), (29, 29)]:
                d.point((x, y), fill='#b0c5cf')
            texture('block/' + name, im)
            elem = box([4, 3, 13], [12, 13, 16], '#body', '#front')
            # Full texture on the front despite the narrower wall-mounted geometry.
            elem['faces']['north']['uv'] = [0, 0, 16, 16]
            elem['faces']['north']['light_emission'] = 8 if status else 0
            model('block/' + name, {'parent': 'minecraft:block/block', 'ambientocclusion': False,
                'textures': {'body': 'accesscontrol:block/reader_body', 'front': f'accesscontrol:block/{name}',
                             'particle': 'accesscontrol:block/reader_body'}, 'elements': [elem]})
            for facing, rotation in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]:
                variants[f'facing={facing},required_level={level},status={status}'] = {
                    'model': 'accesscontrol:block/' + name, 'y': rotation}
    write_json(ASSETS / 'blockstates/card_reader.json', {'variants': variants})
    model('block/card_reader', {'parent': 'accesscontrol:block/reader_l1_s0',
        'display': {'gui': {'rotation': [25, 225, 0], 'translation': [0, 0, 0], 'scale': [1.4, 1.4, 1.4]}}})
    item('card_reader', True)


def door_texture(name, tech, window):
    rng = random.Random(42)
    im = Image.new('RGBA', (32, 64), '#c0c7c9' if not tech else '#172632')
    d = ImageDraw.Draw(im)
    for y in range(64):
        for x in range(32):
            base = (184, 194, 198) if not tech else (27, 43, 56)
            n = rng.randrange(-5, 6)
            d.point((x, y), fill=tuple(v + n for v in base) + (255,))
    d.rectangle((0, 0, 31, 63), outline='#354653', width=2)
    d.rectangle((3, 3, 28, 60), outline='#627783' if not tech else '#3e657c')
    d.line((4, 32, 27, 32), fill='#627783' if not tech else '#0b151e', width=2)
    for x in (4, 27):
        for y in (4, 29, 35, 59):
            d.rectangle((x, y, x + 1, y + 1), fill='#e2edf0')
    if tech:
        d.rectangle((5, 7, 6, 56), fill='#47e1e8')
        d.rectangle((25, 7, 26, 56), fill='#47e1e8')
        d.line((10, 5, 21, 5), fill='#67edf0')
        d.rectangle((12, 41, 20, 52), fill='#101b24', outline='#54768b')
        for y in (44, 47, 50):
            d.line((14, y, 18, y), fill='#608c9c')
    else:
        for y in (40, 44, 48, 52):
            d.line((9, y, 22, y), fill='#5c6f7a')
        d.rectangle((23, 30, 25, 36), fill='#425867')
    if window:
        d.rectangle((8, 8, 23, 23), fill='#101e28', outline='#91b8c8', width=2)
        # Cutout glass: clear interior, diagonal specular pixels and a cross brace.
        d.rectangle((10, 10, 21, 21), fill=(0, 0, 0, 0))
        d.line((11, 12, 16, 10), fill='#a1d9e8')
        d.line((17, 21, 21, 19), fill='#a1d9e8')
        d.line((10, 16, 21, 16), fill='#506d7d')
    texture('block/' + name + '_top', im.crop((0, 0, 32, 32)))
    texture('block/' + name + '_bottom', im.crop((0, 32, 32, 64)))
    icon = Image.new('RGBA', (32, 32))
    icon.alpha_composite(im.resize((14, 28), Image.Resampling.NEAREST), (9, 2))
    texture('item/' + name, icon)


def make_doors():
    # Vanilla rotations, hinges and open models are mirrored here without importing assets.
    rotations = {'east': 0, 'south': 90, 'west': 180, 'north': 270}
    for name, tech, window in [('sealed_iron_door', False, False), ('windowed_iron_door', False, True),
            ('sliding_tech_door', True, False), ('windowed_sliding_tech_door', True, True)]:
        door_texture(name, tech, window)
        variants = {}
        for half, part in [('lower', 'bottom'), ('upper', 'top')]:
            for hinge in ['left', 'right']:
                for opened in [False, True]:
                    suffix = f'{part}_{hinge}' + ('_open' if opened else '')
                    model('block/' + name + '_' + suffix, {'parent': 'minecraft:block/door_' + suffix,
                        'textures': {'top': f'accesscontrol:block/{name}_top', 'bottom': f'accesscontrol:block/{name}_bottom'}})
                    for facing, rot in rotations.items():
                        y = (rot + (90 if hinge == 'left' else 270)) % 360 if opened else rot
                        variants[f'facing={facing},half={half},hinge={hinge},open={str(opened).lower()}'] = {
                            'model': f'accesscontrol:block/{name}_{suffix}', 'y': y}
        write_json(ASSETS / 'blockstates' / (name + '.json'), {'variants': variants})
        model('item/' + name, {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'accesscontrol:item/' + name}})
        item(name)
        if tech:
            panel = 'tech_window_panel' if window else 'tech_panel'
            pv = {}
            for half, part in [('lower', 'bottom'), ('upper', 'top')]:
                # Default north-facing slab matches DoorBlock's closed collision shape.
                elem = box([0, 0, 13], [16, 16, 16], '#panel')
                for side in ['north', 'south']:
                    elem['faces'][side]['uv'] = [0, 0, 16, 16]
                model(f'block/{panel}_{part}', {'parent': 'minecraft:block/block', 'ambientocclusion': False,
                    'textures': {'panel': f'accesscontrol:block/{name}_{part}', 'particle': f'accesscontrol:block/{name}_{part}'},
                    'elements': [elem]})
                for facing, rot in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]:
                    pv[f'facing={facing},half={half}'] = {'model': f'accesscontrol:block/{panel}_{part}', 'y': rot}
            write_json(ASSETS / 'blockstates' / (panel + '.json'), {'variants': pv})


def make_data():
    for name in ['card_reader', 'sealed_iron_door', 'windowed_iron_door', 'sliding_tech_door', 'windowed_sliding_tech_door']:
        condition = {'condition': 'minecraft:survives_explosion'}
        if name != 'card_reader':
            condition = {'condition': 'minecraft:block_state_property', 'block': 'accesscontrol:' + name, 'properties': {'half': 'lower'}}
        write_json(DATA / 'loot_table/blocks' / (name + '.json'), {'type': 'minecraft:block', 'pools': [
            {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'accesscontrol:' + name}],
             'conditions': [condition] + ([] if name == 'card_reader' else [{'condition': 'minecraft:survives_explosion'}])}]})
    def recipe(name, pattern, key, count=1):
        write_json(DATA / 'recipe' / (name + '.json'), {'type': 'minecraft:crafting_shaped', 'category': 'redstone',
            'pattern': pattern, 'key': {k: v for k, v in key.items()}, 'result': {'id': 'accesscontrol:' + name, 'count': count}})
    recipe('card_reader', ['IRI', 'CQC', 'IRI'], {'I': 'minecraft:iron_ingot', 'R': 'minecraft:redstone', 'C': 'minecraft:copper_ingot', 'Q': 'minecraft:quartz'})
    recipe('sealed_iron_door', ['II', 'II', 'II'], {'I': 'minecraft:iron_block'})
    recipe('windowed_iron_door', ['G', 'D'], {'G': 'minecraft:glass_pane', 'D': 'accesscontrol:sealed_iron_door'})
    recipe('sliding_tech_door', ['QCQ', 'PDP', 'QRQ'], {'Q': 'minecraft:quartz', 'C': 'minecraft:copper_ingot', 'P': 'minecraft:piston', 'D': 'accesscontrol:sealed_iron_door', 'R': 'minecraft:redstone'})
    recipe('windowed_sliding_tech_door', ['G', 'D'], {'G': 'minecraft:glass_pane', 'D': 'accesscontrol:sliding_tech_door'})
    # Cards intentionally have no survival recipes: otherwise anyone could craft clearance.
    write_json(ROOT / 'data/minecraft/tags/block/mineable/pickaxe.json', {'replace': False, 'values': [
        'accesscontrol:' + n for n in ['card_reader', 'sealed_iron_door', 'windowed_iron_door', 'sliding_tech_door', 'windowed_sliding_tech_door']]})


def make_language():
    zh = {
        'itemGroup.accesscontrol': '门禁系统',
        'block.accesscontrol.card_reader': '门禁读卡器',
        'block.accesscontrol.sealed_iron_door': '全封闭铁制安防门',
        'block.accesscontrol.windowed_iron_door': '观察窗铁制安防门',
        'block.accesscontrol.sliding_tech_door': '全封闭科技移门',
        'block.accesscontrol.windowed_sliding_tech_door': '观察窗科技移门',
        'message.accesscontrol.granted': '验证通过 · 所需权限 L%s',
        'message.accesscontrol.denied': '权限不足 · 需要 L%s 或更高级门禁卡',
        'message.accesscontrol.use_card': '请手持门禁卡刷卡；OP 潜行右键可配置',
        'message.accesscontrol.op_only': '只有 OP 可以配置门禁',
        'message.accesscontrol.config_saved': '门禁已设置为 L%s 及以上可通行',
        'message.accesscontrol.config_rejected': '配置失败：请靠近门禁并确认 OP 权限',
        'screen.accesscontrol.title': '门禁权限配置',
        'screen.accesscontrol.minimum': '选择最低通行权限',
        'screen.accesscontrol.rule': 'L%s 及更高级别的卡可以通行',
        'screen.accesscontrol.save': '保存设置',
        'subtitles.accesscontrol.reader_granted': '门禁：验证通过',
        'subtitles.accesscontrol.reader_denied': '门禁：拒绝通行',
        'subtitles.accesscontrol.pneumatic_open': '气动门开启',
        'subtitles.accesscontrol.pneumatic_close': '气动门关闭',
    }
    en = dict(zip(zh.keys(), [
        'Access Control', 'Card Reader', 'Sealed Iron Security Door', 'Windowed Iron Security Door',
        'Sealed Sliding Tech Door', 'Windowed Sliding Tech Door',
        'Access granted · Required clearance L%s', 'Access denied · Clearance L%s or higher required',
        'Use an access card; operators can sneak-use to configure', 'Only operators can configure readers',
        'Reader now requires clearance L%s or higher', 'Configuration rejected: check operator permission and distance',
        'Reader Configuration', 'Choose minimum clearance', 'L%s and higher cards are accepted', 'Save',
        'Reader grants access', 'Reader denies access', 'Pneumatic door opens', 'Pneumatic door closes']))
    for level in range(1, 5):
        zh[f'item.accesscontrol.access_card_{level}'] = f'L{level} 门禁卡'
        en[f'item.accesscontrol.access_card_{level}'] = f'Level {level} Access Card'
    write_json(ASSETS / 'lang/zh_cn.json', zh)
    write_json(ASSETS / 'lang/en_us.json', en)


def make_sounds():
    rate = 22050
    for name, duration in [('reader_granted', .24), ('reader_denied', .36), ('pneumatic_open', .52), ('pneumatic_close', .56)]:
        rng = random.Random(name)
        samples = []
        low = 0
        for i in range(int(rate * duration)):
            t = i / rate
            if name.startswith('reader'):
                granted = name.endswith('granted')
                freq = (880 if t < .11 else 1320) if granted else 185
                env = min(1, t / .012) * min(1, (duration - t) / .04)
                value = math.sin(2 * math.pi * freq * t) * .28 * env
                if not granted: value += math.sin(2 * math.pi * 233 * t) * .13 * env
            else:
                noise = rng.uniform(-1, 1)
                low = .82 * low + .18 * noise
                hiss = noise - low
                env = min(1, t / .04) * max(0, 1 - t / duration) ** .7
                value = hiss * .20 * env + low * .35 * env
                value += math.sin(2 * math.pi * (90 * t - 25 * t * t)) * .10 * env
                impact = max(0, 1 - abs(t - duration * .83) / .025)
                value += low * .6 * impact
            samples.append(struct.pack('<h', int(max(-1, min(1, value)) * 32767)))
        target = ASSETS / 'sounds' / (name + '.ogg')
        target.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory() as temp:
            wav = Path(temp) / 'source.wav'
            with wave.open(str(wav), 'wb') as f:
                f.setnchannels(1); f.setsampwidth(2); f.setframerate(rate); f.writeframes(b''.join(samples))
            subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', str(wav), '-c:a', 'libvorbis', '-q:a', '5', str(target)], check=True)
    write_json(ASSETS / 'sounds.json', {name: {'subtitle': 'subtitles.accesscontrol.' + name,
        'sounds': ['accesscontrol:' + name]} for name in ['reader_granted', 'reader_denied', 'pneumatic_open', 'pneumatic_close']})


if __name__ == '__main__':
    make_cards(); make_readers(); make_doors(); make_data(); make_language(); make_sounds()
    print('Generated textures, models, blockstates, recipes, loot, translations and sounds.')
