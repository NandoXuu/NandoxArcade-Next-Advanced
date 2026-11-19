import os
import requests
from io import BytesIO
from PIL import Image

DESTINO = "app/src/main/assets/platforms"
os.makedirs(DESTINO, exist_ok=True)

API = "https://commons.wikimedia.org/w/api.php"

CONSOLES = {
    "ps1": "Sony PlayStation original console",
    "ps2": "Sony PlayStation 2 console",
    "psp": "Sony PlayStation Portable PSP",
    "nds": "Nintendo DS handheld console",
    "3ds": "Nintendo 3DS handheld console",
    "n64": "Nintendo 64 console",
    "dreamcast": "Sega Dreamcast console",
    "gba": "Nintendo Game Boy Advance",
    "gamecube": "Nintendo GameCube console",
    "wii": "Nintendo Wii console",
}

session = requests.Session()
session.headers.update({
    "User-Agent": "NandoxArcade/1.0 (personal game launcher)"
})

def buscar_imagem(termo):
    params = {
        "action": "query",
        "format": "json",
        "generator": "search",
        "gsrsearch": "filetype:bitmap " + termo,
        "gsrnamespace": 6,
        "gsrlimit": 15,
        "prop": "imageinfo",
        "iiprop": "url|mime|extmetadata",
        "iiurlwidth": 1400,
    }

    response = session.get(API, params=params, timeout=30)
    response.raise_for_status()

    pages = response.json().get("query", {}).get("pages", {})
    candidatos = []

    for page in pages.values():
        info = page.get("imageinfo", [{}])[0]
        mime = info.get("mime", "")

        if not mime.startswith("image/"):
            continue

        url = info.get("thumburl") or info.get("url")
        if not url:
            continue

        metadata = info.get("extmetadata", {})
        candidatos.append({
            "title": page.get("title", ""),
            "url": url,
            "mime": mime,
            "license": metadata.get("LicenseShortName", {}).get("value", "Não informada"),
            "artist": metadata.get("Artist", {}).get("value", "Não informado"),
            "page": info.get("descriptionurl", ""),
        })

    candidatos.sort(key=lambda x: (
        0 if x["mime"] == "image/png" else 1
    ))

    return candidatos


def baixar_console(codigo, termo):
    destino = os.path.join(DESTINO, codigo + ".png")

    print(f"\n[+] Pesquisando: {codigo}")

    try:
        candidatos = buscar_imagem(termo)

        for item in candidatos:
            try:
                response = session.get(item["url"], timeout=45)
                response.raise_for_status()

                imagem = Image.open(BytesIO(response.content))
                imagem.load()
                imagem = imagem.convert("RGBA")

                imagem.thumbnail((1400, 1400), Image.Resampling.LANCZOS)
                imagem.save(destino, "PNG", optimize=True)

                with open("fontes_imagens.txt", "a", encoding="utf-8") as f:
                    f.write(
                        f"{codigo}: {item['title']}\n"
                        f"Origem: {item['page']}\n"
                        f"Licença: {item['license']}\n"
                        f"Autor: {item['artist']}\n\n"
                    )

                print(f"[OK] {codigo}.png ({imagem.width}x{imagem.height})")
                return True

            except Exception as erro:
                print(f"[-] Imagem descartada: {erro}")

        print(f"[ERRO] Nenhuma imagem utilizável para {codigo}")
        return False

    except Exception as erro:
        print(f"[ERRO] Pesquisa de {codigo}: {erro}")
        return False


sucesso = True

for codigo, termo in CONSOLES.items():
    if not baixar_console(codigo, termo):
        sucesso = False

print("\n================================")
print("DOWNLOAD DAS ARTES CONCLUÍDO")
print("================================")
print("Diretório:", DESTINO)

if not sucesso:
    print("ATENÇÃO: Algumas imagens não foram baixadas.")
    raise SystemExit(1)
