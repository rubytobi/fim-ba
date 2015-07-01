package start;

import java.util.Map;
import java.util.UUID;

public interface Device {
	int numSlots = 4;

	public double[] createValuesLoadprofile();

	public void sendLoadprofile();

	public UUID getUUID();

	public String getName();

	public double[] chargeValuesLoadprofile(double[] toBeReduced);

	public void initialize(Map<String, Object> init);

	public DeviceStatus getStatus();

	public void ping();
}
