package Util;

import java.util.UUID;

import Entity.Identifiable;

public class Identity {
	private UUID uuid = null;
	private String name = null;
	private UUID authKey = null;

	public Identity() {

	}

	/**
	 * 
	 * @param caller
	 */
	public Identity(Identifiable caller) {
		this.uuid = caller.getUUID();
		this.name = caller.getClass().getName();
	}

	/**
	 * Konsturktor für werweiterte Authentifizierung
	 * 
	 * @param caller
	 *            Wer
	 * @param authKey
	 *            Authorisierungsschlüssel
	 */
	public Identity(Identifiable caller, UUID authKey) {
		this(caller);
		this.authKey = authKey;
	}

	public UUID getUUID() {
		return uuid;
	}

	public String getName() {
		return name;
	}

	public UUID getAuthKey() {
		return authKey;
	}

}
