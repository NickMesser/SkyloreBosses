TESTS = {
    # name: (player pos, look-at, extra setup commands, ticks)
    "slam": ((0, 77, 110), (0, 90, 120), [], 160),
    "charging": ((-94, 101, 60), (-104, 104, 60), [], 160),
    "spitting": ((90, 91, 60), (104, 105, 60), [], 160),
    "grasping": ((98, 140, -54), (104, 150, -60), ["effect give @s minecraft:levitation 8 0 true"], 160),
    "mouth": ((-98, 128, -56), (-104, 128, -60), ["effect give @s minecraft:levitation 6 0 true"], 140),
}

