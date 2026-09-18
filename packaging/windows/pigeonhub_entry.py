"""PyInstaller entry point for the PigeonHub CLI."""
import sys

# qrcode imports its PIL image factory lazily inside make_image(); the static
# import here is what makes PyInstaller bundle Pillow for `pigeonhub login`.
import qrcode.image.pil  # noqa: F401

from pigeonhub.cli import main

if __name__ == "__main__":
    sys.exit(main())
