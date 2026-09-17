"""PyInstaller entry point for the PigeonHub CLI."""
import sys

from pigeonhub.cli import main

if __name__ == "__main__":
    sys.exit(main())
