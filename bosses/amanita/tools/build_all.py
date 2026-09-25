"""Rebuild every Amanita model through the Blockbench MCP (Blockbench open, MCP plugin on :3000), then posed renders.
    python build_all.py"""
import os, subprocess, sys
os.chdir(os.path.dirname(os.path.abspath(__file__)))
subprocess.check_call([sys.executable, "build_one.py", "hollow_bolt", "hollow_spawn", "lamp_eater", "amanita"])
subprocess.check_call([sys.executable, "poses.py"])
