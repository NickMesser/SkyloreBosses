"""Rebuild every Matris Calyx model through the Blockbench MCP (Blockbench open, MCP plugin on :3000).
    python build_all.py            # all models + renders
    python build_one.py slam        # one model (names: grasping slam charging mouth spitting nerve bloom spore_vent bile_glob)
    python poses.py                 # posed renders"""
import os, subprocess, sys
os.chdir(os.path.dirname(os.path.abspath(__file__)))
names = "grasping slam charging mouth spitting nerve bloom spore_vent bile_glob".split()
subprocess.check_call([sys.executable, "build_one.py", *names])
subprocess.check_call([sys.executable, "poses.py"])
