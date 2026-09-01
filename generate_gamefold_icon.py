from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont


OUT_DIR = Path(__file__).resolve().parent
SIZE = 1620
SCALE = 3
W = H = SIZE * SCALE


def s(value):
    return int(round(value * SCALE))


def rounded_rect_mask(size, radius):
    mask = Image.new("L", size, 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius=radius, fill=255)
    return mask


def fit_font(text, max_width, start_size):
    font_path = "/System/Library/Fonts/Supplemental/Impact.ttf"
    size = start_size
    while size > 20:
        font = ImageFont.truetype(font_path, size)
        box = ImageDraw.Draw(Image.new("L", (10, 10))).textbbox((0, 0), text, font=font)
        if box[2] - box[0] <= max_width:
            return font, box
        size -= 2
    return ImageFont.truetype(font_path, size), box


def paste_color_with_mask(base, mask, color, offset=(0, 0), opacity=255):
    layer = Image.new("RGBA", base.size, color)
    if opacity != 255:
        mask = mask.point(lambda p: int(p * opacity / 255))
    shifted = Image.new("L", base.size, 0)
    shifted.paste(mask, offset)
    base.alpha_composite(Image.composite(layer, Image.new("RGBA", base.size, (0, 0, 0, 0)), shifted))


def main():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))

    outer_mask = rounded_rect_mask((W, H), s(335))
    outer = Image.new("RGBA", (W, H), (134, 142, 124, 255))
    img.alpha_composite(Image.composite(outer, Image.new("RGBA", (W, H), (0, 0, 0, 0)), outer_mask))

    inner_margin = s(67)
    inner_box = (inner_margin, inner_margin, W - inner_margin, H - inner_margin)
    inner_mask = Image.new("L", (W, H), 0)
    dmask = ImageDraw.Draw(inner_mask)
    dmask.rounded_rectangle(inner_box, radius=s(265), fill=255)

    bg = Image.new("RGBA", (W, H), (10, 18, 21, 255))
    img.alpha_composite(Image.composite(bg, Image.new("RGBA", (W, H), (0, 0, 0, 0)), inner_mask))

    draw = ImageDraw.Draw(img)

    # Subtle inner screen depth.
    for i, alpha in enumerate((18, 12)):
        inset = inner_margin + s(26 + i * 18)
        draw.rounded_rectangle(
            (inset, inset, W - inset, H - inset),
            radius=s(240 - i * 12),
            outline=(255, 252, 230, alpha),
            width=s(2),
        )

    text = "GAMEFOLD"
    font, bbox = fit_font(text, s(1195), s(247))
    text_w = bbox[2] - bbox[0]
    text_h = bbox[3] - bbox[1]
    x = (W - text_w) // 2 - bbox[0]
    y = s(700) - text_h // 2 - bbox[1]

    # Shared wordmark plate: a dark object behind the type, kept optically flat.
    plate = Image.new("L", (W, H), 0)
    pdraw = ImageDraw.Draw(plate)
    plate_box = (x - s(38), y + bbox[1] - s(68), x + text_w + s(58), y + bbox[3] + s(92))
    pdraw.rounded_rectangle(plate_box, radius=s(94), fill=235)
    plate = plate.filter(ImageFilter.GaussianBlur(s(1.4)))
    paste_color_with_mask(img, plate, (4, 10, 13, 232))

    text_mask = Image.new("L", (W, H), 0)
    tdraw = ImageDraw.Draw(text_mask)
    tdraw.text((x, y), text, font=font, fill=255)

    # Corrected perspective: all depth layers move along one consistent vector.
    depth_steps = 11
    for step in range(depth_steps, 0, -1):
        t = step / depth_steps
        ox = s(14 + 8 * step)
        oy = s(11 + 7 * step)
        green = (
            int(0 + 4 * (1 - t)),
            int(118 + 95 * (1 - t)),
            int(77 + 48 * (1 - t)),
            int(72 + 172 * t),
        )
        paste_color_with_mask(img, text_mask, green, offset=(ox, oy), opacity=int(130 + 90 * t))

    # A single dark contact shadow makes the word sit on the plate.
    shadow = text_mask.filter(ImageFilter.GaussianBlur(s(11)))
    paste_color_with_mask(img, shadow, (0, 0, 0, 170), offset=(s(22), s(27)), opacity=155)

    # Fold facets are parallel and clipped to the word shape, avoiding the old mismatched perspective.
    facets = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    fdraw = ImageDraw.Draw(facets)
    facet_color = (0, 198, 105, 215)
    facet_dark = (0, 112, 78, 190)
    base_y = y + bbox[1]
    for px, py, fw, fh in [
        (x + s(40), base_y + s(88), s(165), s(64)),
        (x + s(438), base_y + s(86), s(185), s(70)),
        (x + s(687), base_y + s(80), s(175), s(64)),
        (x + s(1015), base_y + s(86), s(188), s(70)),
        (x + s(154), base_y + s(312), s(260), s(62)),
        (x + s(615), base_y + s(310), s(250), s(62)),
        (x + s(974), base_y + s(314), s(235), s(62)),
    ]:
        fdraw.polygon(
            [(px, py), (px + fw, py), (px + fw - s(44), py + fh), (px - s(44), py + fh)],
            fill=facet_color,
        )
        fdraw.polygon(
            [(px + fw - s(44), py + fh), (px + fw, py), (px + fw + s(20), py + s(18)), (px + fw - s(24), py + fh + s(18))],
            fill=facet_dark,
        )
    facets_clipped = Image.composite(facets, Image.new("RGBA", (W, H), (0, 0, 0, 0)), text_mask)
    img.alpha_composite(facets_clipped)

    # Cream face on top, with a soft bevel highlight instead of noisy texture.
    face = Image.new("RGBA", (W, H), (255, 244, 217, 255))
    img.alpha_composite(Image.composite(face, Image.new("RGBA", (W, H), (0, 0, 0, 0)), text_mask))

    highlight = Image.new("L", (W, H), 0)
    hdraw = ImageDraw.Draw(highlight)
    hdraw.rectangle((0, 0, W, y + text_h // 2), fill=84)
    highlight = Image.composite(highlight, Image.new("L", (W, H), 0), text_mask)
    paste_color_with_mask(img, highlight, (255, 251, 235, 66))

    # Re-apply only the actual facet polygons over the face so the fold motif remains visible.
    facets_alpha = Image.composite(facets.getchannel("A"), Image.new("L", (W, H), 0), text_mask)
    facets_alpha = facets_alpha.point(lambda p: int(p * 0.58))
    facets_over = facets.copy()
    facets_over.putalpha(facets_alpha)
    img.alpha_composite(facets_over)

    # Tiny top-right control is aligned with the plate perspective and kept away from the logo edge.
    draw.rounded_rectangle(
        (x + text_w - s(112), y + bbox[1] - s(70), x + text_w - s(12), y + bbox[1] - s(28)),
        radius=s(21),
        fill=(255, 178, 36, 255),
    )
    draw.rounded_rectangle(
        (x + text_w + s(15), y + bbox[1] - s(40), x + text_w + s(58), y + bbox[1] + s(3)),
        radius=s(22),
        outline=(237, 241, 232, 228),
        width=s(6),
    )
    draw.ellipse(
        (x + text_w + s(28), y + bbox[1] - s(27), x + text_w + s(45), y + bbox[1] - s(10)),
        outline=(237, 241, 232, 228),
        width=s(4),
    )

    # Clip everything to the outer app icon silhouette.
    clipped = Image.composite(img, Image.new("RGBA", (W, H), (0, 0, 0, 0)), outer_mask)
    clipped = clipped.resize((SIZE, SIZE), Image.Resampling.LANCZOS)
    clipped.save(OUT_DIR / "gamefold-icon-fixed.png")


if __name__ == "__main__":
    main()
