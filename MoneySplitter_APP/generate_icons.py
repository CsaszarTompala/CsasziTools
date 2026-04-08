from PIL import Image
import os

BASE = r'C:\Private\CsasziTools\MoneySplitter2\app\src\main\res'

icon = Image.open(r'C:\Private\CsasziTools\MoneySplitter2\pics\icon.png').convert('RGBA')

# Android adaptive icon sizes: the full icon is 108dp, safe zone is 72dp
# We need to generate mipmap-* webp files at various densities
# mdpi=48, hdpi=72, xhdpi=96, xxhdpi=144, xxxhdpi=192
mipmap_sizes = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192,
}

for folder, size in mipmap_sizes.items():
    out_dir = os.path.join(BASE, folder)
    os.makedirs(out_dir, exist_ok=True)

    resized = icon.resize((size, size), Image.LANCZOS)
    # Save as webp (matching existing format)
    resized.save(os.path.join(out_dir, 'ic_launcher.webp'), 'WEBP', quality=90)
    resized.save(os.path.join(out_dir, 'ic_launcher_round.webp'), 'WEBP', quality=90)
    print(f'Generated {folder}: {size}x{size}')

# Also generate adaptive icon foreground (108dp * density)
# mdpi=108, hdpi=162, xhdpi=216, xxhdpi=324, xxxhdpi=432
adaptive_sizes = {
    'mipmap-mdpi': 108,
    'mipmap-hdpi': 162,
    'mipmap-xhdpi': 216,
    'mipmap-xxhdpi': 324,
    'mipmap-xxxhdpi': 432,
}

for folder, size in adaptive_sizes.items():
    out_dir = os.path.join(BASE, folder)
    # Create foreground with padding for adaptive icon (icon in center 66% of canvas)
    canvas = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    icon_size = int(size * 0.66)
    offset = (size - icon_size) // 2
    resized = icon.resize((icon_size, icon_size), Image.LANCZOS)
    canvas.paste(resized, (offset, offset), resized)
    canvas.save(os.path.join(out_dir, 'ic_launcher_foreground.webp'), 'WEBP', quality=90)
    print(f'Generated adaptive foreground {folder}: {size}x{size}')

# Copy main_page.png to drawable as the background/splash image
# Resize to a reasonable resolution for drawable (max 1024 wide)
main = Image.open(r'C:\Private\CsasziTools\MoneySplitter2\pics\main_page.png')
drawable_dir = os.path.join(BASE, 'drawable-nodpi')
os.makedirs(drawable_dir, exist_ok=True)
main.save(os.path.join(drawable_dir, 'main_page.webp'), 'WEBP', quality=85)
print(f'Generated drawable-nodpi/main_page.webp')

print('Done!')

