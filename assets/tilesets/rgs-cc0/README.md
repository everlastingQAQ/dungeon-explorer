# RGS_Dev CC0 terrain

`tileset-gray.png` comes from **Free CC0 Top Down Tileset Template Pixel Art**
by RGS_Dev:

https://rgsdev.itch.io/free-cc0-top-down-tileset-template-pixel-art

The source pack provides 16x16 tiles in five color variations. This project
uses the gray variation for its floors. Walls are drawn by `WallAppearance`
using the same gray palette, with continuous caps and faces for arbitrary
maze junctions. The source's room-outline slices are not a complete wall
autotile set: cell (3,1) includes a turn, and (1,2)/(1,3) contain floor fills.
The included `LICENSE.txt` records the author's CC0 license notice.

## Room scenery

Room furniture uses this pack exclusively. Source rectangles below use top-left
pixel coordinates in the unchanged gray PNG:

| Scenery | Source (x, y, width, height) | Footprint |
| --- | --- | --- |
| Abandoned box / 废弃木箱 | 112, 0, 32, 16 | Two horizontal cells, lid included |
| Pot / 陶罐 | 224, 0, 16, 16 | One cell |
| Pottery fragments / 散落陶片 | 240, 0, 16, 16 | Walkable floor detail |
| Bordered paving / 镶边石板 | 160, 0, 48, 48 | Nine-slice walkable floor patch |

Keep the original orientation, pixels and nearest-neighbor filtering. Do not
rotate the front-facing box, stretch sprites, omit its lid, or use reward icons
as noninteractive decoration. Layout variants reuse these same source images.
Existing procedural walls/floor motifs and DungeonTileset II gameplay sprites
are outside the room-furniture replacement scope.

The bordered paving uses columns 10–12 and rows 0–2 only, never the raised-face
tiles in row 3. Repeat edge/center cells without scaling or rotating them; saved
decoration parts 0–8 run left-to-right, bottom-to-top. Complete patches count
toward the 25% floor-decoration budget and never change collision. Mixed storage
reuses the same boxes and pots; shattered-pottery layouts reuse the same fragments.
