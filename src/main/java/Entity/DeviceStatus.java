package Entity;

public enum DeviceStatus {
	// Konstruktor ohne konkrete Werte aufgerufen
	CREATED,

	// Konsturktor mit Werten wie für maxCooling, minTemp1, ... aufgerufen
	INITIALIZED,

	// Consumer verknüpft, Kommunikation ist nun möglich
	READY;
}
