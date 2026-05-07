<img style="display: block; margin-left: auto; margin-right: auto;" src="https://media.forgecdn.net/attachments/1115/225/everfurnace_title.png" alt="EverFurnace" width="912" height="125" />

## How it works
Vanilla furnaces only cook when their chunk is loaded &mdash; step too far away and everything freezes until you return. EverFurnace fixes that.

When you come back and the furnace chunk loads, EverFurnace calculates exactly how much time has passed and instantly applies it &mdash; consuming fuel, processing inputs, and filling the output slot as if the furnace had been running the whole time. No waiting. No lost progress.

## Features

* ✅ Works with all three vanilla furnace types &mdash; Furnace, Blast Furnace, and Smoker
* ✅ Handles full stacks &mdash; multiple items and fuel are consumed correctly in one pass</span></li>
* ✅ Chat notification when you open a furnace that cooked while you were away</span></li>
* ✅ Flame particle burst at the furnace when catch-up completes</span></li>
* ✅ Fully configurable &mdash; catch-up cap, delta threshold, notifications, and particles can all be tuned or disabled via config</span></li>

## Compatibility
EverFurnace targets <code>AbstractFurnaceBlockEntity</code> via Mixin, so it automatically covers any block that extends it. It is compatible with other mixin-based furnace mods (such as FastFurnace) as long as they do not replace or redirect the tick method entirely.

## Notes

* Catch-up is capped at one in-game day per load event by default (configurable). A furnace offline longer than the cap will not fully simulate all elapsed time &mdash; by design, to prevent lag spikes on chunk load.</span></li>
* If the furnace runs out of fuel or input items partway through a catch-up window, processing stops at that point. This is correct behaviour.</span></li>

## Discord
>[https://discord.gg/MskxMMRD](https://discord.gg/CpWXamx)