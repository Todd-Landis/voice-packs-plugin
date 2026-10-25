package com.voicepacks;

import java.util.Set;

/**
 * Boss rooms and raids where lines are held until the player leaves, so a line never talks over a fight (the "Hold
 * lines during boss fights" setting, on by default). The triggers are fixed achievement events, not mechanic cues,
 * so the hub reviewer (2026-10-25) said no restriction is required; this is a courtesy. Copied from the Watchdog
 * plugin's Region.java as of its hub commit 3360e0a (2026-10-21; plugin-hub plugins/watchdog); keep it in sync with
 * that list. Hydra and Kalphite Queen count everywhere here, though Watchdog limits them to the instance and plane 0.
 */
final class RestrictedRegions
{
	private static final Set<Integer> REGIONS = Set.of(
		5536,                                      // Alchemical Hydra (instance)
		4405, 8291, 10595, 12132,                  // Vardorvis, Leviathan, Whisperer, Sucellus
		9023, 9043, 9551, 7216,                    // Vorkath, Inferno, Fight Caves, Colosseum
		13972,                                     // Kalphite Queen
		13136, 13137, 13393, 13138, 13394, 13139, 13395, 13140, 13396, 13141, 13397, 13145, 13401, 12889, // CoX
		12613, 13125, 13122, 13123, 13379, 12612, 12611, // ToB
		14160, 15698, 15700, 14162, 14164, 15186, 15188, 14674, 14676, 15184, 15696, // ToA
		6045, 5269, 13668, 14180, 15515,           // Yama, Doom of Mokhaiotl, Nightmare
		11330, 11645, 11901,                       // Phantom Muspah, Maggot King
		16214, 16726, 16215, 16727, 16216, 16728, 16472, 16471 // TFA
	);

	private RestrictedRegions()
	{
	}

	static boolean contains(int regionId)
	{
		return REGIONS.contains(regionId);
	}
}
