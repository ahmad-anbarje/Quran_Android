# Builds the English meaning of every word, shown under its transliteration, from the website's data/meaning/{surah}.json.
# Lined up with the app's own word count: a printed slot the recitation counts as two words carries its meaning on both.
# Usage: python tools/word_meaning.py <website public/data/meaning folder>
import json, os, sys

SRC = sys.argv[1]
OUT = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets', 'data', 'word-meaning.txt')

# As tools/tajweed_build.py: the word that is two, by ayah
PAIRS = {(2, 181): 2, (8, 6): 3, (13, 37): 7}

rows = []
for s in range(1, 115):
    for a, words in enumerate(json.load(open(os.path.join(SRC, f'{s}.json'), encoding='utf-8')), 1):
        if (s, a) in PAIRS:
            w = PAIRS[(s, a)]
            words = words[:w + 1] + words[w:]
        rows.append(f'{s}:{a}\t' + '|'.join(x.replace('|', '/').strip() for x in words))
open(OUT, 'w', encoding='utf-8', newline='\n').write('\n'.join(rows) + '\n')
print(len(rows), 'ayahs')
