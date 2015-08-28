package Util;

import static org.junit.Assert.*;

import java.util.TreeSet;
import java.util.UUID;

import org.junit.Test;

import Entity.Loadprofile;
import Entity.Offer;

public class BATest {

	@Test
	public void testScoreCalculation() {
		Loadprofile lA = new Loadprofile(new double[] { 0.0, 0.0, 0.0, 0.0 }, DateTime.currentTimeSlot(),
				Loadprofile.Type.DELTA);
		Offer oA = new Offer(UUID.randomUUID(), lA);

		Loadprofile lB = new Loadprofile(new double[] { 0.0, 0.0, 0.0, 0.0 }, DateTime.currentTimeSlot(),
				Loadprofile.Type.DELTA);
		Offer oB = new Offer(UUID.randomUUID(), lB);

		Scorecard scorecard = new Scorecard();
		assertEquals("comparision shall be null", null, scorecard.first());

	}
}
