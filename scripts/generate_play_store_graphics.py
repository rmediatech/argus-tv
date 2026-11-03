#!/usr/bin/env python3
"""
Argus Sovereign Remote — Google Play Store Graphic Asset Generator
Generates:
1. 512x512 Master App Icon (icon_512x512.png)
2. 1024x500 Feature Graphic Banner (feature_graphic_1024x500.png)
Complies strictly with Google Play Console 2026 Asset Specifications.
"""

import os
from PIL import Image, ImageDraw, ImageFont

def generate_graphics():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_dir = os.path.abspath(os.path.join(script_dir, ".."))
    assets_dir = os.path.join(project_dir, "assets")
    out_dir = os.path.join(assets_dir, "play_store")
    os.makedirs(out_dir, exist_ok=True)

    emblem_path = os.path.join(assets_dir, "argus_emblem.png")
    if not os.path.exists(emblem_path):
        raise FileNotFoundError(f"Missing master emblem: {emblem_path}")
    emblem = Image.open(emblem_path).convert("RGBA")

    # 1. 512x512 Master App Icon
    icon_canvas = Image.new("RGBA", (512, 512), (11, 15, 25, 255))
    draw_icon = ImageDraw.Draw(icon_canvas)
    draw_icon.rounded_rectangle([4, 4, 507, 507], radius=110, outline=(0, 229, 255, 120), width=3)
    emblem_resized = emblem.resize((420, 420), Image.Resampling.LANCZOS)
    icon_canvas.paste(emblem_resized, (46, 46), emblem_resized)
    icon_path = os.path.join(out_dir, "icon_512x512.png")
    icon_canvas.save(icon_path, "PNG", optimize=True)
    print(f"✅ Generated Master Icon: {icon_path} ({os.path.getsize(icon_path)} bytes)")

    # 2. 1024x500 Feature Graphic Banner
    fg = Image.new("RGB", (1024, 500), (10, 14, 23))
    draw_fg = ImageDraw.Draw(fg)

    # Ambient radar concentric rings
    for r, alpha in [(120, 30), (220, 25), (320, 20), (420, 15), (520, 10)]:
        draw_fg.arc([750 - r, 250 - r, 750 + r, 250 + r], start=0, end=360, fill=(0, 229, 255, alpha), width=1)
    draw_fg.line([750, 250, 750 + 380, 250 - 150], fill=(0, 229, 255, 45), width=2)

    font_title = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 54)
    font_sub = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 20)
    font_body = ImageFont.truetype("/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf", 17)
    font_badge = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 13)

    draw_fg.text((60, 75), "ARGUS", font=font_title, fill=(0, 229, 255))
    draw_fg.text((60, 145), "ZERO-CLOUD SMART TV & MEDIA CONTROLLER", font=font_sub, fill=(240, 245, 255))

    bullets = [
        "• 100% Local LAN Control — Zero Cloud, Zero Ads, Zero Telemetry",
        "• Multi-Room Watch Party Lockstep Synchronization (<50ms)",
        "• Cinema Beaming for YouTube, Prime Video, Pluto TV & Direct URLs",
        "• Tactical Microsecond Volume & Transport Fanout"
    ]
    y_offset = 195
    for bullet in bullets:
        draw_fg.text((60, y_offset), bullet, font=font_body, fill=(180, 195, 215))
        y_offset += 32

    badges = [
        ("GOOGLE TV", (0, 229, 255)),
        ("FIRE TV", (255, 153, 0)),
        ("ROKU", (168, 85, 247)),
        ("SAMSUNG", (59, 130, 246)),
        ("LG WEBOS", (236, 72, 153)),
        ("SONY", (34, 197, 94))
    ]
    bx = 60
    by = 385
    for label, col in badges:
        bbox = font_badge.getbbox(label)
        bw = (bbox[2] - bbox[0]) + 18
        bh = 28
        draw_fg.rounded_rectangle([bx, by, bx + bw, by + bh], radius=6, outline=col, width=1)
        draw_fg.text((bx + 9, by + 6), label, font=font_badge, fill=col)
        bx += bw + 12

    emblem_fg = emblem.resize((360, 360), Image.Resampling.LANCZOS)
    fg.paste(emblem_fg, (620, 70), emblem_fg)

    fg_path = os.path.join(out_dir, "feature_graphic_1024x500.png")
    fg.save(fg_path, "PNG", optimize=True)
    print(f"✅ Generated Feature Graphic: {fg_path} ({os.path.getsize(fg_path)} bytes)")

if __name__ == "__main__":
    generate_graphics()
