"""Rebuild every Null Router model through the Blockbench MCP (Blockbench open, MCP plugin on :3000), then posed renders.
    python build_all.py"""
import os, subprocess, sys
os.chdir(os.path.dirname(os.path.abspath(__file__)))
subprocess.check_call([sys.executable, "build_one.py", "retry_packet", "null_router"])
subprocess.check_call([sys.executable, "poses.py"])
