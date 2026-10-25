package com.voicepacks;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RestrictedRegionsTest
{
	/** Every region in Watchdog's Region.java at its plugin-hub commit 3360e0a (2026-10-21). */
	private static final int[] WATCHDOG = {
		5536, 4405, 8291, 10595, 12132, 9023, 9043, 9551, 7216, 13972,
		13136, 13137, 13393, 13138, 13394, 13139, 13395, 13140, 13396, 13141, 13397, 13145, 13401, 12889,
		12613, 13125, 13122, 13123, 13379, 12612, 12611,
		14160, 15698, 15700, 14162, 14164, 15186, 15188, 14674, 14676, 15184, 15696,
		6045, 5269, 13668, 14180, 15515, 11330, 11645, 11901,
		16214, 16726, 16215, 16727, 16216, 16728, 16472, 16471,
	};

	@Test
	public void coversEveryWatchdogRegion()
	{
		for (int region : WATCHDOG)
		{
			assertTrue("region " + region + " should be restricted", RestrictedRegions.contains(region));
		}
	}

	@Test
	public void leavesOrdinaryAreasAlone()
	{
		assertFalse(RestrictedRegions.contains(12850));   // Lumbridge
		assertFalse(RestrictedRegions.contains(12342));   // Edgeville
	}
}
