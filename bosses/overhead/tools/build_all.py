"""Rebuild every Overhead model through the Blockbench MCP (Blockbench open, MCP plugin on :3000), then posed renders.
    python build_all.py"""
import os, subprocess, sys
os.chdir(os.path.dirname(os.path.abspath(__file__)))
subprocess.check_call([sys.executable, "build_one.py", "overhead", "generator_pylon", "howitzer_shell", "seeker_missile"])
subprocess.check_call([sys.executable, "poses.py"])
