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
		Loadprofile lA = new Loadprofile(new double[] { 0.0, 0.0, 0.0, 0.0 }, DateTime.currentTimeSlot(), 0.0);
		Offer oA = new Offer(UUID.randomUUID(), lA);

		Loadprofile lB = new Loadprofile(new double[] { 0.0, 0.0, 0.0, 0.0 }, DateTime.currentTimeSlot(), 0.0);
		Offer oB = new Offer(UUID.randomUUID(), lB);

		Score scoreOne = new Score(oA, oB, null, null);
		assertEquals("score shall be 0", 0.0, scoreOne.getScore(), 0.0);

		Loadprofile lC = new Loadprofile(new double[] { 1.0, 0.0, 0.0, 0.0 }, DateTime.currentTimeSlot(), 0.0);
		Offer oC = new Offer(UUID.randomUUID(), lC);

		Score scoreTwo = new Score(oA, oC, null, null);
		assertEquals("score shall be 1", 1.0, scoreTwo.getScore(), 0.0);

		Loadprofile lD = new Loadprofile(new double[] { 1.0, -1.0, 5.0, 0.0 }, DateTime.currentTimeSlot(), 0.0);
		Offer oD = new Offer(UUID.randomUUID(), lD);

		Score scoreThree = new Score(oA, oD, null, null);
		assertEquals("score shall be 7", 7.0, scoreThree.getScore(), 0.0);

		TreeSet<Score> scorecard = new TreeSet<Score>();
		scorecard.add(scoreOne);
		scorecard.add(scoreTwo);
		scorecard.add(scoreThree);

		assertEquals("comparision shall be 0", true, scoreOne.equals(scorecard.first()));
		assertEquals("comparision shall be -1", -1, scoreOne.compareTo(scoreTwo));
		assertEquals("comparision shall be -1", -1, scoreTwo.compareTo(scoreThree));
		assertEquals("comparision shall be 1", 1, scoreTwo.compareTo(scoreOne));
	}
}
