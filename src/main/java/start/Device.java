package start;

import java.util.Map;
import java.util.UUID;
import java.util.GregorianCalendar;

import Packet.ChangeRequest;
import Util.DeviceStatus;

public interface Device {
	int numSlots = 4;	

	public double[] createValuesLoadprofile(double[] values);

	public void sendNewLoadprofile();

	public void sendDeltaLoadprofile(GregorianCalendar timeChanged, double valueChanged);

	public UUID getUUID();

	public void initialize(Map<String, Object> init);

	public DeviceStatus getStatus();

	public void ping();

	public void setConsumer(UUID consumerUUID);

	public void changeLoadprofile(ChangeRequest cr);
	
	public void confirmLoadprofile (GregorianCalendar time);
}
