package start;

import java.util.Map;
import java.util.UUID;

import Packet.ChangeRequest;
import Util.DeviceStatus;

public interface Device {
	int numSlots = 4;

	public double[] createValuesLoadprofile();

	public void sendLoadprofile();

	public UUID getUUID();

	public double[] chargeValuesLoadprofile(double[] toBeReduced);

	public void initialize(Map<String, Object> init);

	public DeviceStatus getStatus();

	public void ping();

	public void changeLoadprofile(ChangeRequest cr);
}
