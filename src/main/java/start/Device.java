package start;

import java.util.UUID;

public class Device {
	protected int numSlots = 4;

	private final UUID uuid;

	protected Device() {
		uuid = UUID.randomUUID();
	}

	public UUID getUUID() {
		return uuid;
	}

	public double[] chargeValuesLoadprofile(double[] toBeReduced) {
		// TODO
		return new double[4];
	}
}
