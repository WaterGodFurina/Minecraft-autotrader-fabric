import json, re, glob

ok = True
for p in glob.glob('src/main/resources/assets/autotrader/lang/*.json'):
    try:
        d = json.load(open(p, encoding='utf-8'))
    except Exception as e:
        ok = False
        print('BAD JSON', p, e)
        continue
    print(p, 'keys=', len(d))

keys = set(json.load(open('src/main/resources/assets/autotrader/lang/en_us.json', encoding='utf-8')))
used = set()
for p in glob.glob('src/main/java/**/*.java', recursive=True):
    s = open(p, encoding='utf-8').read()
    used |= set(re.findall(r'"(autotrader\.[A-Za-z0-9_.]+)"', s))
    used |= set(re.findall(r'"(key\.autotrader\.[A-Za-z0-9_.]+)"', s))
    used |= set(re.findall(r'"(autotrader\.hotkey\.[A-Za-z0-9_.]+)"', s))

missing = sorted(k for k in used if k not in keys)
print('referenced keys missing from en_us:', missing if missing else 'none')
print('RESULT', 'OK' if ok and not missing else 'CHECK')
